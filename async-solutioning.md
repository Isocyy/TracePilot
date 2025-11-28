# Async Debugging Solution - TracePilot v1

## Problem Statement

### The Core Issue
MCP (Model Context Protocol) is request/response based, while JDI (Java Debug Interface) is event-driven.
When a breakpoint hits or a step completes, there's no native way to "push" this information to the LLM client.

### Current Broken Behavior
In `DebugSession.java` (lines 264-287), the event processing thread auto-resumes after EVERY event:
```java
eventThread = new Thread(() -> {
    EventQueue queue = vm.eventQueue();
    while (eventThreadRunning && vm != null) {
        EventSet eventSet = queue.remove(100);
        if (eventSet != null) {
            for (Event event : eventSet) {
                processEvent(event);
            }
            eventSet.resume();  // ← PROBLEM: Auto-resumes on breakpoint hits!
        }
    }
}, "TracePilot-EventProcessor");
```

**Result**: Breakpoints never actually "hold" the VM. The LLM sets a breakpoint, calls resume, and the VM immediately continues past the breakpoint.

### MCP Notification Limitations
Per MCP spec (2025-06-18), notifications are designed for **metadata changes** only:
- `notifications/tools/list_changed`
- `notifications/resources/list_changed`

They are NOT designed for arbitrary custom events like "breakpoint hit". Using custom notifications would violate the spec and may not work with MCP clients like Claude.

---

## Solution: Enhanced Polling Model (Option 3)

### Design Principles
1. **Works within MCP spec** - No custom notifications
2. **Simple for LLMs** - Clear state machine
3. **Efficient** - Optional blocking wait
4. **Informative** - Rich stop context

### Components

#### 1. StopReason Tracking
Track WHY the VM stopped with detailed context.

```java
public class StopReason {
    public enum Type {
        NONE,              // VM is running or not connected
        BREAKPOINT_HIT,    // Hit a line breakpoint
        STEP_COMPLETE,     // Step into/over/out completed
        EXCEPTION,         // Uncaught/caught exception
        WATCHPOINT_ACCESS, // Field read
        WATCHPOINT_MODIFY, // Field write
        METHOD_ENTRY,      // Entered a method
        METHOD_EXIT,       // Exited a method
        VM_START,          // VM just started (suspended)
        VM_DEATH,          // VM terminated
        USER_SUSPEND       // Manual suspend() call
    }

    private Type type;
    private ThreadReference thread;
    private Location location;
    private Map<String, Object> details; // breakpointId, exceptionType, fieldName, etc.
}
```

#### 2. Smart Event Resume Logic
Only auto-resume for **monitoring events**, NOT for **stop events**.

| Event Type | Action | Rationale |
|------------|--------|-----------|
| BreakpointEvent | **DO NOT resume** | User wants to inspect |
| StepEvent | **DO NOT resume** | Step completed, inspect |
| ExceptionEvent | **DO NOT resume** | Exception to analyze |
| WatchpointEvent | **DO NOT resume** | Field access to inspect |
| MethodEntryEvent | **DO NOT resume** | Method entry to inspect |
| MethodExitEvent | **DO NOT resume** | Method exit to inspect |
| ClassPrepareEvent | Auto-resume | Monitoring only |
| ClassUnloadEvent | Auto-resume | Monitoring only |
| ThreadStartEvent | Auto-resume | Monitoring only |
| ThreadDeathEvent | Auto-resume | Monitoring only |
| MonitorContendedEnterEvent | Auto-resume | Monitoring only |
| VMStartEvent | Depends | Usually keep suspended |
| VMDeathEvent | N/A | VM is dead |

#### 3. Enhanced `debug_status` Tool
Return comprehensive state information.

**Current Response**:
```json
{
  "connected": true,
  "vmName": "Java HotSpot(TM) 64-Bit Server VM"
}
```

**Enhanced Response**:
```json
{
  "connected": true,
  "vmName": "Java HotSpot(TM) 64-Bit Server VM",
  "state": "STOPPED",
  "stopReason": {
    "type": "BREAKPOINT_HIT",
    "threadId": 1,
    "threadName": "main",
    "location": {
      "className": "com.example.OrderService",
      "methodName": "processOrder",
      "lineNumber": 42,
      "sourcePath": "OrderService.java"
    },
    "details": {
      "breakpointId": "bp-1",
      "hitCount": 3
    }
  },
  "suspendedThreads": ["main", "worker-1"],
  "runningThreads": ["GC-thread", "Finalizer"]
}
```

#### 4. New `wait_for_stop` Tool
Blocking wait with timeout for LLM convenience.

**Input**:
```json
{
  "timeout": 30
}
```

**Output (when stopped)**:
```json
{
  "stopped": true,
  "stopReason": { ... same as debug_status ... },
  "waitedMs": 1523
}
```

**Output (timeout)**:
```json
{
  "stopped": false,
  "state": "RUNNING",
  "waitedMs": 30000,
  "message": "Timeout waiting for VM to stop"
}
```

---

## Implementation Plan

### Step 1: Create StopReason Class
- File: `src/main/java/com/nanik/tracepilot/debug/StopReason.java`
- Immutable class with factory methods for each event type
- Thread-safe (will be accessed from event thread and tool threads)

### Step 2: Modify DebugSession Event Handling
- File: `src/main/java/com/nanik/tracepilot/debug/DebugSession.java`
- Add `private volatile StopReason lastStopReason`
- Add `private final Object stopLock = new Object()` for wait/notify
- Modify `processEvent()` to:
  1. Determine if event is a "stop event" or "monitor event"
  2. For stop events: Set `lastStopReason`, notify waiters, DO NOT resume
  3. For monitor events: Log to EventMonitorManager, resume EventSet
- Add methods:
  - `public StopReason getLastStopReason()`
  - `public boolean isSuspended()`
  - `public StopReason waitForStop(long timeoutMs)`

### Step 3: Enhance DebugStatusTool
- File: `src/main/java/com/nanik/tracepilot/tools/impl/DebugStatusTool.java`
- Include stop reason in response
- Include thread states
- Include current location if stopped

### Step 4: Create WaitForStopTool
- File: `src/main/java/com/nanik/tracepilot/tools/impl/WaitForStopTool.java`
- Input: timeout (seconds, default 30)
- Calls `debugSession.waitForStop(timeoutMs)`
- Returns stop reason or timeout message

### Step 5: Update resume/step Tools
- Modify `ResumeTool`, `StepIntoTool`, `StepOverTool`, `StepOutTool`
- Clear `lastStopReason` when resuming/stepping
- Return clear message: "VM resumed, use debug_status or wait_for_stop to check state"

---

## Testing Scenarios

### Scenario 1: Basic Breakpoint Hit
**Setup**: DebugPlayground with loop, breakpoint on line inside loop

**Test Steps**:
```
1. debug_attach_socket(port=5005)
2. breakpoint_set(className="com.nanik.debuggee.DebugPlayground", lineNumber=15)
3. resume()
4. debug_status()  // Should show STOPPED, BREAKPOINT_HIT
5. variables_local()  // Should work
6. resume()
7. debug_status()  // Should show STOPPED again (loop iteration)
```

**Expected**:
- Step 4: `state=STOPPED`, `stopReason.type=BREAKPOINT_HIT`, `location.lineNumber=15`
- Step 5: Variables visible
- Step 7: Same as step 4, hitCount incremented

### Scenario 2: Step Operations
**Setup**: Stopped at breakpoint

**Test Steps**:
```
1. (Already stopped at breakpoint from Scenario 1)
2. step_over()
3. debug_status()  // Should show STOPPED, STEP_COMPLETE
4. execution_location()  // Should be next line
5. step_into()
6. debug_status()  // Should show STOPPED, STEP_COMPLETE
7. execution_location()  // Should be inside called method
```

**Expected**:
- Step 3: `state=STOPPED`, `stopReason.type=STEP_COMPLETE`
- Step 4: Line number advanced by 1
- Step 6: `state=STOPPED`, `stopReason.type=STEP_COMPLETE`
- Step 7: Different method name

### Scenario 3: Wait For Stop
**Setup**: DebugPlayground with sleep, breakpoint after sleep

**Test Steps**:
```
1. debug_attach_socket(port=5005)
2. breakpoint_set(className="com.nanik.debuggee.DebugPlayground", lineNumber=25)
3. resume()
4. wait_for_stop(timeout=10)  // Should return when breakpoint hits
```

**Expected**:
- Step 4: Returns with `stopped=true`, `stopReason.type=BREAKPOINT_HIT`
- `waitedMs` should be less than 10000

### Scenario 4: Wait For Stop Timeout
**Setup**: No breakpoints, infinite loop

**Test Steps**:
```
1. debug_attach_socket(port=5005)
2. resume()
3. wait_for_stop(timeout=2)  // Should timeout
```

**Expected**:
- Step 3: Returns with `stopped=false`, `waitedMs=2000`, message about timeout

### Scenario 5: Exception Breakpoint
**Setup**: Code that throws exception

**Test Steps**:
```
1. debug_attach_socket(port=5005)
2. exception_break_on(exceptionClass="java.lang.NullPointerException", caught=true, uncaught=true)
3. resume()
4. debug_status()  // Should show STOPPED, EXCEPTION
5. exception_info()  // Should show NPE details
```

**Expected**:
- Step 4: `state=STOPPED`, `stopReason.type=EXCEPTION`
- Step 5: Exception class, message, stack trace

### Scenario 6: Watchpoint Hit
**Setup**: Class with field access

**Test Steps**:
```
1. debug_attach_socket(port=5005)
2. watchpoint_access(className="com.nanik.debuggee.DebugPlayground", fieldName="counter")
3. resume()
4. debug_status()  // Should show STOPPED, WATCHPOINT_ACCESS
```

**Expected**:
- Step 4: `state=STOPPED`, `stopReason.type=WATCHPOINT_ACCESS`, details include fieldName

### Scenario 7: Monitor Events Don't Stop
**Setup**: Code that creates new threads

**Test Steps**:
```
1. debug_attach_socket(port=5005)
2. thread_start_watch()
3. resume()
4. debug_status()  // Should show RUNNING (not stopped)
5. events_pending()  // Should show thread start events
```

**Expected**:
- Step 4: `state=RUNNING` (thread start doesn't suspend)
- Step 5: Events captured for later retrieval

### Scenario 8: Already Stopped Behavior
**Setup**: VM already stopped at breakpoint

**Test Steps**:
```
1. (Already stopped at breakpoint)
2. wait_for_stop(timeout=5)  // Should return immediately
```

**Expected**:
- Step 2: Returns immediately with `stopped=true`, `waitedMs` near 0


---

## Edge Cases & Error Handling

### Edge Case 1: VM Disconnects While Waiting
- `wait_for_stop()` should detect VM disconnect
- Return: `{"stopped": false, "error": "VM disconnected", "state": "DISCONNECTED"}`

### Edge Case 2: Multiple Threads Hit Breakpoints
- First breakpoint hit sets `stopReason`
- Other threads may also hit breakpoints before LLM inspects
- Solution: Track per-thread stop reasons, return the "first" one
- `debug_status` should list all suspended threads

### Edge Case 3: Rapid Resume/Stop
- LLM calls `resume()`, then immediately `wait_for_stop()`
- Must ensure event thread has time to process events
- Use proper synchronization with wait/notify

### Edge Case 4: Step Into Native Code
- JDI can't step through native methods
- `step_into()` may complete immediately if native
- `stopReason.type = STEP_COMPLETE` still applies
- Location may be unexpected (after native call)

### Edge Case 5: VM Start State
- When launching with `suspend=y`, VM starts suspended
- Initial state: `STOPPED`, `stopReason.type = VM_START`
- LLM should call `resume()` to start execution

---

## LLM Workflow Examples

### Example 1: Debug a NullPointerException
```
LLM: I'll help debug the NPE. Let me connect and set up exception handling.

1. debug_attach_socket(port=5005)
   → Connected to Java HotSpot VM

2. exception_break_on(exceptionClass="java.lang.NullPointerException", caught=false, uncaught=true)
   → Exception breakpoint set

3. resume()
   → VM resumed

4. wait_for_stop(timeout=60)
   → STOPPED: EXCEPTION at OrderService:42

5. stack_frames()
   → [OrderService.processOrder:42, Main.main:15]

6. variables_local()
   → order=null, customerId=123

LLM: Found it! The 'order' variable is null at line 42. Let me check where it comes from...

7. step_out()
   → Step requested

8. debug_status()
   → STOPPED: STEP_COMPLETE at Main:15
```

### Example 2: Trace Through a Method
```
LLM: Let me set a breakpoint and trace through calculateTotal().

1. breakpoint_set(className="OrderService", lineNumber=50)
   → Breakpoint set (bp-1)

2. resume()
   → VM resumed

3. wait_for_stop(timeout=30)
   → STOPPED: BREAKPOINT_HIT at OrderService:50

4. step_into()
5. debug_status()
   → STOPPED: STEP_COMPLETE at PriceCalculator:10

6. variables_local()
   → items=[...], discount=0.1

7. step_over()
8. debug_status()
   → STOPPED: STEP_COMPLETE at PriceCalculator:11

9. variables_local()
   → items=[...], discount=0.1, subtotal=150.0
```

### Example 3: Efficient Polling Pattern
```
# If LLM doesn't want to block with wait_for_stop

1. resume()
2. debug_status() → RUNNING
3. debug_status() → RUNNING
4. debug_status() → STOPPED: BREAKPOINT_HIT

# This works but is inefficient. Prefer wait_for_stop().
```

---

## Files to Modify/Create

| File | Action | Description |
|------|--------|-------------|
| `StopReason.java` | CREATE | Stop reason tracking class |
| `DebugSession.java` | MODIFY | Event handling, wait logic |
| `DebugStatusTool.java` | MODIFY | Enhanced status response |
| `WaitForStopTool.java` | CREATE | New blocking wait tool |
| `ResumeTool.java` | MODIFY | Clear stop reason |
| `StepIntoTool.java` | MODIFY | Clear stop reason |
| `StepOverTool.java` | MODIFY | Clear stop reason |
| `StepOutTool.java` | MODIFY | Clear stop reason |
| `McpServer.java` | MODIFY | Register WaitForStopTool |

---

## Success Criteria

1. ✅ Breakpoints actually suspend the VM (don't auto-resume)
2. ✅ `debug_status` shows clear RUNNING/STOPPED state
3. ✅ `debug_status` shows WHY we stopped (breakpoint, step, exception, etc.)
4. ✅ `debug_status` shows WHERE we stopped (class, method, line)
5. ✅ `wait_for_stop` blocks until VM stops or timeout
6. ✅ Step operations work correctly (step, then inspect)
7. ✅ Monitor events (class load, thread start) still auto-resume
8. ✅ Multiple breakpoint hits can be handled
9. ✅ Exception breakpoints show exception details
10. ✅ Watchpoints show field access details

---

## Version 2 Considerations (Future)

If MCP adds support for custom server-initiated notifications in a future spec version:

1. **Add notification capability during init**:
   ```json
   "capabilities": {
     "notifications": {
       "debug": ["stopped", "resumed", "terminated"]
     }
   }
   ```

2. **Send notifications on debug events**:
   ```json
   {
     "jsonrpc": "2.0",
     "method": "notifications/debug/stopped",
     "params": {
       "reason": "breakpoint_hit",
       "location": {...}
     }
   }
   ```

3. **Keep polling as fallback** for clients that don't support notifications

Until then, the enhanced polling model in v1 provides a solid, spec-compliant solution.

