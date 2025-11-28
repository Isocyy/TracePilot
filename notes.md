# TracePilot Development Notes

## Known Behaviors & Limitations

### 1. Asynchronous Debugging Model (SOLVED ✅)

TracePilot now has a robust async debugging model that works well with MCP's request/response nature.

**Key Features:**
- **Stop Reason Tracking**: VM tracks WHY it stopped (breakpoint_hit, step_complete, exception, watchpoint, etc.)
- **Smart Event Handling**: Stop events (breakpoint, step, exception) keep VM suspended; monitor events (class load, thread start) auto-resume
- **Blocking Wait**: Use `wait_for_stop` tool to block until the VM stops or timeout

**Recommended Usage Pattern:**
```
1. Set a breakpoint: breakpoint_set(className, lineNumber)
2. Resume execution: resume()
3. Wait for stop: wait_for_stop(timeout=30)
   → Returns STOPPED with type=BREAKPOINT_HIT, location, thread info
4. Inspect: variables_local(), stack_frames()
5. Continue: step_over(), step_into(), or resume()
```

**Alternative (Polling):**
```
1. resume()
2. debug_status() → state: RUNNING or STOPPED + stop reason details
```

**Stop Reason Types:**
- `BREAKPOINT_HIT` - Hit a line breakpoint
- `STEP_COMPLETE` - Step operation finished
- `EXCEPTION_THROWN` - Exception occurred
- `WATCHPOINT_ACCESS` - Field was read
- `WATCHPOINT_MODIFY` - Field was modified
- `METHOD_ENTRY` - Entered a watched method
- `METHOD_EXIT` - Exited a watched method
- `VM_START` - VM just started (suspended)
- `VM_DISCONNECT` - VM disconnected

---

### 2. Deferred Breakpoints

When setting a breakpoint on a class that hasn't been loaded yet, TracePilot creates a **pending/deferred breakpoint**.

**Behavior:**
- Breakpoint is marked as `[pending]` in `breakpoint_list`
- The breakpoint will be activated when the class is loaded
- Currently requires a ClassPrepareRequest event handler (partially implemented)

**Example:**
```
# When DebugPlayground class is not yet loaded:
breakpoint_set("com.nanik.debuggee.DebugPlayground", 72)
→ "Breakpoint set (PENDING - class not yet loaded)"

# After resume and class loads, breakpoint becomes active
```

**Current Limitation:** The ClassPrepareEvent handler is set up but not actively processed in an event loop. Full deferred breakpoint resolution requires implementing an event processing thread.

---

### 3. Native Code Stepping Limitation

JDI cannot step through native methods (JNI code, native system calls).

**Symptoms:**
- `execution_location` shows `Line: -1` 
- Step operations fail with: "Thread is in native code (line -1)"

**Common Native Code Locations:**
- `java.lang.ref.Reference.waitForReferencePendingList()` - Reference Handler thread
- `java.lang.Object.wait()` - Thread waiting
- `java.net.SocketInputStream.read()` - Network I/O

**Solution:**
Set a breakpoint in Java code and resume. The VM will stop at the breakpoint where stepping is possible.

---

### 4. VM Startup Suspension

When a VM starts with `suspend=y`, all threads are suspended before any user code runs.

**Observed Behavior:**
- Main thread hasn't started executing yet
- Only system threads exist (Reference Handler, Finalizer, Signal Dispatcher)
- All threads are typically in native code at this point

**Implication:**
You cannot step immediately after attach. You must:
1. Set a breakpoint in the target class
2. Resume to let the class load and breakpoint hit
3. Then step through Java code

---

## Debugging Session Notes (Phase 3 Development)

### Issue: JDWP Error 13 (THREAD_NOT_SUSPENDED)

**Symptom:** `step_over` failed with "Unexpected JDWP Error: 13"

**Root Cause:** 
- JDI's `createStepRequest()` requires the thread to be suspended AND in Java code
- When suspended in a native method, step request creation fails

**Fix Applied:**
Added pre-check in all step tools to verify:
1. Thread is suspended
2. Thread has stack frames
3. Top frame is in Java code (line number > 0)

If in native code, returns helpful error message instead of cryptic JDWP error.

---

### Issue: Breakpoints Not Hitting

**Symptom:** Set breakpoint, resume, suspend immediately → still in native code

**Root Cause:**
- The time between resume and suspend is too short
- VM doesn't have time to reach the breakpoint
- Immediate suspend catches threads in unpredictable locations

**Not a Bug:** This is expected async behavior. The LLM client should:
1. Resume
2. Wait or poll
3. Check execution_location to see if breakpoint was hit

---

### Issue: Class Not Found for Breakpoints

**Symptom:** `breakpoint_set` returns "Class not found: com.nanik.debuggee.DebugPlayground"

**Root Cause:**
- JDI only knows about classes that have been loaded by the JVM
- At VM startup with `suspend=y`, only bootstrap classes are loaded

**Fix Applied:**
Implemented deferred breakpoint system:
- Detects unloaded classes
- Creates pending breakpoint
- Sets up ClassPrepareRequest to activate when class loads

---

## Future Improvements (v1.x)

1. ~~**Event Processing Thread**: Implement a background thread that processes JDI events and updates internal state (breakpoint hits, step completions, etc.)~~ ✅ Implemented - Smart event handling with StopReason tracking

2. ~~**Blocking Step Operations**: Add optional `waitForStep=true` parameter that blocks until step completes~~ ✅ Implemented - Use `wait_for_stop` after step operations

3. ~~**Breakpoint Hit Notification**: Track when breakpoints are hit and report hit counts~~ ✅ Implemented - StopReason includes breakpoint ID and location

4. **Better Thread Selection**: For step operations, prefer the "main" thread or the thread that hit a breakpoint

5. ~~**Watchpoints**: Add field access/modification watchpoints~~ ✅ Implemented in Phase 5

---

## v2 Features (Advanced Phase 7)

The following advanced features require careful implementation due to VM capability requirements and complexity. Deferred to v2:

### 1. Hot Code Replace (`hot_swap`)
**Purpose**: Replace a class's bytecode at runtime without restarting the VM.

**JDI Components**:
- `VirtualMachine.redefineClasses(Map<ReferenceType, byte[]>)`
- Capability check: `vm.canRedefineClasses()`

**Limitations**:
- Cannot add/remove methods or fields (schema changes)
- Only method body changes are supported
- Some VMs don't support this at all

**Implementation Notes**:
- Takes class name and path to new .class file
- Reads bytecode, validates, and calls redefineClasses()
- Must handle `UnsupportedOperationException`, `ClassFormatError`

---

### 2. Force Early Return (`force_return`)
**Purpose**: Force a method to return immediately with a specified value.

**JDI Components**:
- `ThreadReference.forceEarlyReturn(Value)`
- Capability check: `vm.canForceEarlyReturn()`

**Use Cases**:
- Skip problematic code paths
- Test error handling by forcing return values
- Break out of infinite loops

**Implementation Notes**:
- Thread must be suspended at a location that's not native
- Return value must match method's return type

---

### 3. Pop Stack Frames (`pop_frames`)
**Purpose**: Pop frames from the call stack, effectively "rewinding" execution.

**JDI Components**:
- `ThreadReference.popFrames(StackFrame)`
- Capability check: `vm.canPopFrames()`

**Use Cases**:
- Re-execute a method after modifying variables
- Undo execution steps
- "Go back in time" during debugging

**Implementation Notes**:
- All frames above the specified frame are popped
- Thread must be suspended
- Cannot pop native frames

---

### 4. Create New Instance (`new_instance`)
**Purpose**: Create a new instance of a class by invoking its constructor.

**JDI Components**:
- `ClassType.newInstance(ThreadReference, Method, List<Value>, int)`

**Use Cases**:
- Create test objects on-the-fly
- Inject mock objects into running code

**Implementation Notes**:
- Find constructor by signature
- Parse arguments similar to `invoke_method`
- Returns ObjectReference of new instance

---

### 5. Get All Instances (`instances_of`)
**Purpose**: Find all instances of a class currently in the heap.

**JDI Components**:
- `ReferenceType.instances(long maxInstances)`
- Capability check: `vm.canGetInstanceInfo()`

**Use Cases**:
- Memory leak investigation
- Find all objects of a type for inspection
- Verify singleton patterns

**Implementation Notes**:
- Can be slow for classes with many instances
- Use maxInstances to limit results

---

### 6. Get Referring Objects (`referring_objects`)
**Purpose**: Find all objects that hold references to a given object.

**JDI Components**:
- `ObjectReference.referringObjects(long maxReferrers)`
- Capability check: `vm.canGetInstanceInfo()`

**Use Cases**:
- Memory leak debugging (who's holding this object?)
- Understand object graphs
- Find GC roots

**Implementation Notes**:
- Can be very slow
- Returns ObjectReferences that point to the target

---

### 7. Breakpoint by File Path (`breakpoint_set_by_file`)
**Purpose**: Set breakpoint using source file path instead of class name.

**Why Useful**:
- LLMs naturally work with file paths when reading code
- Avoids manual package → class name derivation

**Implementation**:
- Parse Java file for `package` declaration
- Extract class name from filename
- Call existing breakpoint logic

---

### 8. Conditional Breakpoints (`breakpoint_conditional`)
**Purpose**: Only stop at breakpoint if a condition evaluates to true.

**JDI Components**:
- `BreakpointRequest.addCountFilter(int)`
- Custom condition evaluation using expression evaluator

**Implementation Notes**:
- Simple approach: hit count filter
- Complex approach: evaluate expression on each hit, resume if false
- Requires event loop to process and filter hits

---

### 9. Class/Method Bytecode Viewer
**Purpose**: View the bytecode of a loaded class or method.

**JDI Components**:
- `Method.bytecodes()` - returns byte array of method bytecode
- `ReferenceType.constantPool()` - constant pool access

**Use Cases**:
- Debug class loading issues
- Verify compiled code
- Advanced debugging scenarios

