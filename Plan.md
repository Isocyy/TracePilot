# TracePilot: JDI-MCP Implementation Plan

## Overview

TracePilot is a Model Context Protocol (MCP) server that exposes the full capabilities of Java Debug Interface (JDI) to LLM clients like Claude. This enables AI-assisted debugging of Java applications.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        LLM (Claude)                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ MCP Protocol (JSON-RPC 2.0)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    TracePilot MCP Server                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │   Stdio     │  │  JSON-RPC   │  │     Tool Registry       │  │
│  │  Transport  │◄─┤   Handler   │◄─┤  (40+ debugging tools)  │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
│                                              │                   │
│  ┌───────────────────────────────────────────┴───────────────┐  │
│  │                    Debug Session Manager                   │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐  │  │
│  │  │  Breakpoint  │  │   Variable   │  │     Event       │  │  │
│  │  │   Manager    │  │  Inspector   │  │     Handler     │  │  │
│  │  └──────────────┘  └──────────────┘  └─────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                              │                                   │
│                              │ JDI API                           │
│                              ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    VirtualMachine                          │  │
│  │  (com.sun.jdi.VirtualMachine)                              │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ JDWP (Java Debug Wire Protocol)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Target JVM                                  │
│                   (Java Application)                             │
└─────────────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
com.nanik.tracepilot
├── mcp/
│   ├── McpServer.java           # Main entry point
│   ├── JsonRpcHandler.java      # JSON-RPC 2.0 protocol
│   ├── StdioTransport.java      # stdin/stdout communication
│   └── ToolRegistry.java        # Tool registration & dispatch
├── protocol/
│   ├── McpMessage.java          # Base message types
│   ├── McpRequest.java          # Request structure
│   ├── McpResponse.java         # Response structure
│   └── McpError.java            # Error codes
├── debug/
│   ├── DebugSession.java        # JDI session wrapper
│   ├── BreakpointManager.java   # Breakpoint management
│   ├── VariableInspector.java   # Variable inspection
│   └── EventHandler.java        # JDI event processing
└── tools/
    ├── ToolHandler.java         # Tool interface
    ├── SessionTools.java        # launch, attach, disconnect
    ├── BreakpointTools.java     # breakpoint operations
    ├── ExecutionTools.java      # step, resume, suspend
    ├── InspectionTools.java     # variables, stack, threads
    ├── WatchpointTools.java     # field watchpoints
    ├── ExceptionTools.java      # exception breakpoints
    ├── AdvancedTools.java       # eval, hot swap, etc.
    └── MonitoringTools.java     # class/thread events
```

---

## Implementation Phases

### Phase 1: Foundation (MCP Protocol Infrastructure)
**Goal**: Establish the core MCP server infrastructure
**Effort**: 2-3 days

| Component | Description |
|-----------|-------------|
| JSON-RPC 2.0 Handler | Parse/serialize JSON-RPC messages |
| Stdio Transport | Read from stdin, write to stdout |
| MCP Lifecycle | Handle `initialize`, `initialized`, `shutdown` |
| Tool Registry | Register and dispatch tool calls |
| Error Handling | Protocol errors (-32600 to -32603) |

**Tools**: `tools/list`, `ping`

---

### Phase 2: Connection Management
**Goal**: Launch or attach to target JVMs for debugging
**Effort**: 2-3 days

| JDI Component | Purpose |
|---------------|---------|
| `Bootstrap.virtualMachineManager()` | Get VM manager |
| `LaunchingConnector` | Launch new JVM with debug |
| `AttachingConnector` | Attach to running JVM |
| `VirtualMachine` | Main debug session handle |

**Tools**:
- `debug_launch` - Launch JVM and attach debugger
- `debug_attach_socket` - Attach via socket (host:port)
- `debug_attach_pid` - Attach by process ID
- `debug_disconnect` - Disconnect from VM
- `debug_status` - Get session status
- `vm_info` - Get VM capabilities

---

### Phase 3: Basic Debugging (Breakpoints & Stepping)
**Goal**: Core debugging operations
**Effort**: 3-4 days

| JDI Component | Purpose |
|---------------|---------|
| `EventRequestManager` | Create breakpoint/step requests |
| `BreakpointRequest` | Line breakpoint |
| `StepRequest` | Step into/over/out |
| `Location` | Code location (class + line) |

**Tools**:
- `breakpoint_set` - Set line breakpoint
- `breakpoint_remove` - Remove breakpoint
- `breakpoint_list` - List all breakpoints
- `breakpoint_enable` / `breakpoint_disable`
- `step_into` / `step_over` / `step_out`
- `resume` / `suspend`
- `execution_location` - Current location

---

### Phase 4: Inspection (Variables, Stack, Threads)
**Goal**: Inspect program state
**Effort**: 3-4 days

| JDI Component | Purpose |
|---------------|---------|
| `ThreadReference` | Thread information |
| `StackFrame` | Call stack frame |
| `LocalVariable` | Local variable metadata |
| `Value` hierarchy | Variable values |
| `ObjectReference` | Object instance |

**Tools**:
- `threads_list` - List all threads
- `thread_select` / `thread_suspend` / `thread_resume`
- `stack_frames` - Get stack trace
- `frame_select` - Select frame
- `variables_local` - Local variables
- `variables_arguments` - Method arguments
- `variable_inspect` - Deep inspect
- `object_fields` - Object fields
- `array_elements` - Array values
- `this_object` - Get 'this' reference

---

### Phase 5: Advanced Breakpoints (Watchpoints, Conditions)
**Goal**: Conditional breakpoints and field watchpoints
**Effort**: 2-3 days

| JDI Component | Purpose |
|---------------|---------|
| `AccessWatchpointRequest` | Break on field read |
| `ModificationWatchpointRequest` | Break on field write |
| `MethodEntryRequest` | Break on method entry |
| `MethodExitRequest` | Break on method exit |
| `EventRequest.addCountFilter()` | Hit count filter |

**VM Capability Checks**: `canWatchFieldAccess()`, `canWatchFieldModification()`

**Tools**:
- `breakpoint_conditional` - Conditional breakpoint
- `breakpoint_hitcount` - Set hit count
- `watchpoint_access` - Field read watchpoint
- `watchpoint_modification` - Field write watchpoint
- `watchpoint_remove` - Remove watchpoint
- `method_entry_break` / `method_exit_break`
- `breakpoint_thread_filter` / `breakpoint_instance_filter`

---

### Phase 6: Exception Handling
**Goal**: Break on exceptions
**Effort**: 1-2 days

| JDI Component | Purpose |
|---------------|---------|
| `ExceptionRequest` | Exception breakpoint |
| `ExceptionEvent` | Exception notification |
| `ExceptionEvent.exception()` | The exception object |
| `ExceptionEvent.catchLocation()` | Catch location |

**Tools**:
- `exception_break_all` - Break on all exceptions
- `exception_break_type` - Break on specific type
- `exception_remove` - Remove exception breakpoint
- `exception_list` - List exception breakpoints
- `exception_info` - Current exception details

---

### Phase 7: Advanced Features
**Goal**: Expression evaluation, hot code replace, advanced control
**Effort**: 4-5 days

| JDI Component | Purpose |
|---------------|---------|
| `ObjectReference.invokeMethod()` | Invoke method |
| `ClassType.invokeMethod()` | Invoke static method |
| `ClassType.newInstance()` | Create instance |
| `VirtualMachine.redefineClasses()` | Hot code replacement |
| `ThreadReference.forceEarlyReturn()` | Force return |
| `ThreadReference.popFrames()` | Pop frames |
| `ReferenceType.instances()` | Get all instances |

**VM Capability Checks**: `canRedefineClasses()`, `canPopFrames()`, `canForceEarlyReturn()`

**Tools**:
- `evaluate_expression` - Evaluate in context
- `invoke_method` - Invoke on object
- `invoke_static` - Invoke static method
- `new_instance` - Create instance
- `hot_swap` - Replace class
- `force_return` - Force method return
- `pop_frames` - Pop stack frames
- `instances_of` - Get class instances
- `referring_objects` - Get referrers
- `set_variable` - Modify variable

---

### Phase 8: Monitoring & Events
**Goal**: Class loading, thread lifecycle, monitor events
**Effort**: 2-3 days

| JDI Component | Purpose |
|---------------|---------|
| `ClassPrepareRequest` | Class load notification |
| `ClassUnloadRequest` | Class unload notification |
| `ThreadStartRequest` | Thread start notification |
| `ThreadDeathRequest` | Thread death notification |
| `MonitorContendedEnterRequest` | Monitor contention |
| `MonitorWaitRequest` | Object.wait() called |

**VM Capability Check**: `canRequestMonitorEvents()`

**Tools**:
- `class_prepare_watch` / `class_unload_watch`
- `thread_start_watch` / `thread_death_watch`
- `monitor_contention_watch` / `monitor_wait_watch`
- `events_pending` - Get pending events
- `event_subscribe` / `event_unsubscribe`

---

## Implementation Priority & Effort Summary

| Phase | Priority | Effort | Dependencies |
|-------|----------|--------|--------------|
| Phase 1: Foundation | Critical | 2-3 days | None |
| Phase 2: Connection | Critical | 2-3 days | Phase 1 |
| Phase 3: Basic Debug | Critical | 3-4 days | Phase 2 |
| Phase 4: Inspection | High | 3-4 days | Phase 3 |
| Phase 5: Adv. Breakpoints | Medium | 2-3 days | Phase 4 |
| Phase 6: Exceptions | Medium | 1-2 days | Phase 3 |
| Phase 7: Advanced | Low | 4-5 days | Phase 4 |
| Phase 8: Monitoring | Low | 2-3 days | Phase 3 |

**Total Estimated Effort**: 20-27 days

---

## Dependencies

```groovy
dependencies {
    // JSON processing
    implementation 'com.google.code.gson:gson:2.10.1'

    // JDI is included in JDK - no external dependency needed
    // Located in: $JAVA_HOME/lib (module jdk.jdi)
}
```

---

## Tool Summary (40+ tools)

| Category | Tools |
|----------|-------|
| Session (6) | `debug_launch`, `debug_attach_socket`, `debug_attach_pid`, `debug_disconnect`, `debug_status`, `vm_info` |
| Breakpoints (10) | `breakpoint_set`, `breakpoint_remove`, `breakpoint_list`, `breakpoint_enable`, `breakpoint_disable`, `breakpoint_conditional`, `breakpoint_hitcount`, `breakpoint_thread_filter`, `breakpoint_instance_filter`, `execution_location` |
| Execution (5) | `step_into`, `step_over`, `step_out`, `resume`, `suspend` |
| Threads (5) | `threads_list`, `thread_select`, `thread_suspend`, `thread_resume`, `stack_frames` |
| Variables (8) | `frame_select`, `variables_local`, `variables_arguments`, `variable_inspect`, `object_fields`, `array_elements`, `this_object`, `set_variable` |
| Watchpoints (5) | `watchpoint_access`, `watchpoint_modification`, `watchpoint_remove`, `method_entry_break`, `method_exit_break` |
| Exceptions (5) | `exception_break_all`, `exception_break_type`, `exception_remove`, `exception_list`, `exception_info` |
| Advanced (9) | `evaluate_expression`, `invoke_method`, `invoke_static`, `new_instance`, `hot_swap`, `force_return`, `pop_frames`, `instances_of`, `referring_objects` |
| Monitoring (9) | `class_prepare_watch`, `class_unload_watch`, `thread_start_watch`, `thread_death_watch`, `monitor_contention_watch`, `monitor_wait_watch`, `events_pending`, `event_subscribe`, `event_unsubscribe` |

---

## Testing Strategy

### Testing Principles

1. **Test-First Development**: Write tests BEFORE implementing each tool
2. **Behavior-Driven**: Tests define expected behavior, not implementation details
3. **Isolation**: Unit tests mock JDI interfaces; integration tests use real JVM
4. **Coverage Requirements**: Minimum 90% line coverage per phase
5. **Error Path Testing**: Every error condition must have a test

### Test Categories

| Category | Purpose | Mocking |
|----------|---------|---------|
| Unit Tests | Test individual components in isolation | Mock JDI, Mock Transport |
| Integration Tests | Test MCP protocol end-to-end | Mock JDI only |
| System Tests | Test against real JVM | No mocking |
| Error Tests | Test all error conditions | Various |

---

## Phase 1: Testing Guidelines

### Transport Tests

#### `StdioTransport`
| Test Case | Input | Expected Output | Error Condition |
|-----------|-------|-----------------|-----------------|
| Read valid JSON | `{"jsonrpc":"2.0","id":1,"method":"ping"}` | Parsed McpRequest object | - |
| Read malformed JSON | `{invalid json}` | - | Parse error -32700 |
| Read empty input | `` | Block waiting | - |
| Write response | McpResponse object | JSON string to stdout | - |
| Handle EOF | stdin closed | Graceful shutdown | - |

### JSON-RPC Handler Tests

#### Request Parsing
| Test Case | Input | Expected | Error Code |
|-----------|-------|----------|------------|
| Valid request | `{"jsonrpc":"2.0","id":1,"method":"test","params":{}}` | McpRequest with all fields | - |
| Missing jsonrpc | `{"id":1,"method":"test"}` | Error response | -32600 |
| Wrong jsonrpc version | `{"jsonrpc":"1.0","id":1,"method":"test"}` | Error response | -32600 |
| Missing method | `{"jsonrpc":"2.0","id":1}` | Error response | -32600 |
| Null id (notification) | `{"jsonrpc":"2.0","method":"test"}` | Process without response | - |
| String id | `{"jsonrpc":"2.0","id":"abc","method":"test"}` | Accept string id | - |
| Integer id | `{"jsonrpc":"2.0","id":123,"method":"test"}` | Accept integer id | - |

#### Response Building
| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| Success result | Result object | `{"jsonrpc":"2.0","id":X,"result":{...}}` |
| Error response | Error code + message | `{"jsonrpc":"2.0","id":X,"error":{"code":N,"message":"..."}}` |
| Notification (no response) | Notification request | No output |

### MCP Lifecycle Tests

#### `initialize`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Valid initialize | `{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{}}` | Server capabilities returned | Response contains `protocolVersion`, `capabilities`, `serverInfo` |
| Missing protocolVersion | `{"capabilities":{}}` | Error | -32602 Invalid params |
| Unsupported version | `{"protocolVersion":"1.0.0"}` | Error or negotiate | Depends on compatibility |
| Double initialize | Call initialize twice | Error | -32600 Already initialized |

#### `initialized`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| After initialize | `{}` | Success (empty) | Server ready for tool calls |
| Before initialize | `{}` | Error | -32600 Not initialized |

#### `shutdown`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Clean shutdown | `{}` | Success, then exit | Process exits cleanly |
| With active session | `{}` | Disconnect VM, then exit | VM disconnected before exit |

### Tool Registry Tests

| Test Case | Input | Expected |
|-----------|-------|----------|
| Register tool | Tool definition | Tool added to registry |
| Duplicate registration | Same tool name | Error or replace |
| List tools | `tools/list` | All registered tools with schemas |
| Call existing tool | `tools/call` with valid name | Tool executed |
| Call unknown tool | `tools/call` with invalid name | Error -32601 Method not found |
| Call with invalid params | `tools/call` with wrong schema | Error -32602 Invalid params |

### Tool: `ping`
| Test Case | Input | Expected Response |
|-----------|-------|-------------------|
| Basic ping | `{}` | `{"content":[{"type":"text","text":"pong"}]}` |

### Tool: `tools/list`
| Test Case | Input | Expected Response |
|-----------|-------|-------------------|
| List all tools | `{}` | Array of tool definitions with `name`, `description`, `inputSchema` |
| Schema validation | - | Each tool has valid JSON Schema |

---

## Phase 2: Testing Guidelines

### Connection Manager Tests

#### Tool: `debug_launch`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Launch simple class | `{"mainClass":"com.example.Main","classpath":"./build/classes"}` | Session created | `debug_status` returns connected |
| Launch with args | `{"mainClass":"Main","args":["arg1","arg2"]}` | Args passed to main() | Inspect args in debuggee |
| Launch with JVM args | `{"jvmArgs":["-Xmx256m"]}` | JVM configured | VM properties reflect args |
| Invalid classpath | `{"mainClass":"Main","classpath":"/nonexistent"}` | Error | `isError: true` with message |
| Class not found | `{"mainClass":"NonExistent"}` | Error | Class not found error |
| Already connected | Call when session exists | Error | "Already debugging" error |
| Launch timeout | Class hangs on init | Error after timeout | Timeout error |

#### Tool: `debug_attach_socket`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Attach to listening JVM | `{"host":"localhost","port":5005}` | Session created | Connected to target |
| Invalid port | `{"host":"localhost","port":99999}` | Error | Invalid port error |
| Connection refused | `{"host":"localhost","port":5006}` (no JVM) | Error | Connection refused |
| Invalid host | `{"host":"nonexistent.local","port":5005}` | Error | Host not found |

#### Tool: `debug_attach_pid`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Attach to running JVM | `{"pid":12345}` | Session created | Connected |
| Invalid PID | `{"pid":-1}` | Error | Invalid PID |
| Non-Java process | `{"pid":1}` | Error | Not a Java process |
| No debug agent | PID of JVM without debug | Error | Cannot attach |

#### Tool: `debug_disconnect`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Disconnect active | `{}` | Success | `debug_status` shows disconnected |
| Disconnect when not connected | `{}` | Error or no-op | Appropriate response |
| Disconnect with pending events | `{}` | Clean disconnect | No hanging threads |

#### Tool: `debug_status`
| Test Case | Input | Expected Response |
|-----------|-------|-------------------|
| No session | `{}` | `{"connected":false}` |
| Active session | `{}` | `{"connected":true,"vmName":"...","suspended":true/false}` |
| After disconnect | `{}` | `{"connected":false}` |

#### Tool: `vm_info`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Get VM info | `{}` | VM details | Contains `name`, `version`, `capabilities` |
| Not connected | `{}` | Error | "Not connected" error |
| Capability flags | `{}` | Boolean capability map | `canRedefineClasses`, `canWatchFieldAccess`, etc. |

---

## Phase 3: Testing Guidelines

### Breakpoint Tests

#### Tool: `breakpoint_set`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Set on valid line | `{"className":"com.example.Main","lineNumber":10}` | Breakpoint ID returned | `breakpoint_list` shows it |
| Set on invalid line | `{"className":"com.example.Main","lineNumber":9999}` | Error | "No code at line" error |
| Set on comment line | `{"className":"Main","lineNumber":5}` (comment) | Error or nearest valid | Appropriate response |
| Set on non-existent class | `{"className":"NonExistent","lineNumber":10}` | Error | "Class not found" error |
| Set duplicate | Same location twice | Return existing ID or error | Consistent behavior |
| Set in inner class | `{"className":"Main$Inner","lineNumber":5}` | Breakpoint set | Breakpoint works |
| Not connected | Any | Error | "Not connected" error |

#### Tool: `breakpoint_remove`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Remove existing | `{"breakpointId":"bp-1"}` | Success | Not in `breakpoint_list` |
| Remove non-existent | `{"breakpointId":"bp-999"}` | Error | "Breakpoint not found" |
| Remove already removed | Same ID twice | Error | "Breakpoint not found" |

#### Tool: `breakpoint_list`
| Test Case | Input | Expected |
|-----------|-------|----------|
| No breakpoints | `{}` | `{"breakpoints":[]}` |
| Multiple breakpoints | `{}` | Array with `id`, `className`, `lineNumber`, `enabled` |
| After remove | `{}` | Removed one not in list |

#### Tool: `breakpoint_enable` / `breakpoint_disable`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Disable active | `{"breakpointId":"bp-1"}` | Success | `enabled: false` in list |
| Enable disabled | `{"breakpointId":"bp-1"}` | Success | `enabled: true` in list |
| Disable already disabled | `{"breakpointId":"bp-1"}` | Success (idempotent) | No change |
| Invalid ID | `{"breakpointId":"invalid"}` | Error | Not found error |

### Execution Control Tests

#### Tool: `step_into`
| Test Case | Precondition | Expected | Validation |
|-----------|--------------|----------|------------|
| Step into method call | Stopped at method call | Enter method | New location inside method |
| Step at simple statement | Stopped at assignment | Next line | Line incremented |
| Step into native method | Stopped at native call | Step over (fallback) | Location after call |
| Not suspended | VM running | Error | "VM not suspended" |

#### Tool: `step_over`
| Test Case | Precondition | Expected | Validation |
|-----------|--------------|----------|------------|
| Step over method call | Stopped at method call | Next line after call | Line after method call |
| Step over simple statement | Stopped at assignment | Next line | Line incremented |
| Step at end of method | Last line of method | Return to caller | In calling method |
| Not suspended | VM running | Error | "VM not suspended" |

#### Tool: `step_out`
| Test Case | Precondition | Expected | Validation |
|-----------|--------------|----------|------------|
| Step out of method | Inside method | Return to caller | At call site in caller |
| Step out of main | In main() | VM exits or error | Appropriate behavior |
| Not suspended | VM running | Error | "VM not suspended" |

#### Tool: `resume`
| Test Case | Precondition | Expected | Validation |
|-----------|--------------|----------|------------|
| Resume suspended | VM suspended | VM running | `debug_status` shows running |
| Resume at breakpoint | Hit breakpoint | Run until next BP or end | Continues execution |
| Resume already running | VM running | No-op or error | Consistent behavior |
| Not connected | No session | Error | "Not connected" |

#### Tool: `suspend`
| Test Case | Precondition | Expected | Validation |
|-----------|--------------|----------|------------|
| Suspend running | VM running | VM suspended | `debug_status` shows suspended |
| Suspend already suspended | VM suspended | No-op | No change |
| Not connected | No session | Error | "Not connected" |

#### Tool: `execution_location`
| Test Case | Precondition | Expected Response |
|-----------|--------------|-------------------|
| At breakpoint | Suspended at BP | `{"className":"...","methodName":"...","lineNumber":N}` |
| Multiple threads | Multiple stopped | Location of current/first thread |
| Not suspended | VM running | Error or empty |
| Not connected | No session | Error |

### Event Handling Tests

| Test Case | Trigger | Expected |
|-----------|---------|----------|
| Breakpoint hit | Execution reaches BP | Event received, VM suspended |
| Step complete | Step finishes | Event received, new location |
| Multiple breakpoints | BP1 then BP2 | Sequential events |
| Breakpoint in thread | Thread hits BP | Correct thread suspended |

---

## Phase 4: Testing Guidelines

### Thread Management Tests

#### Tool: `threads_list`
| Test Case | Precondition | Expected Response |
|-----------|--------------|-------------------|
| Single thread app | Simple main | At least 1 thread (main) |
| Multi-threaded | App with threads | All threads with `id`, `name`, `status` |
| Thread states | Various states | Correct state: RUNNING, SLEEPING, WAITING, etc. |
| Not connected | No session | Error |

#### Tool: `thread_select`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Select valid thread | `{"threadId":"thread-1"}` | Success | Subsequent ops use this thread |
| Select invalid thread | `{"threadId":"invalid"}` | Error | "Thread not found" |
| Select terminated thread | Dead thread ID | Error | "Thread not alive" |

#### Tool: `thread_suspend` / `thread_resume`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Suspend running thread | `{"threadId":"thread-1"}` | Thread suspended | Thread status shows suspended |
| Resume suspended thread | `{"threadId":"thread-1"}` | Thread resumed | Thread status shows running |
| Suspend already suspended | Same thread | Success (idempotent) | Suspend count increased |
| Invalid thread | `{"threadId":"invalid"}` | Error | "Thread not found" |

### Stack Frame Tests

#### Tool: `stack_frames`
| Test Case | Input | Expected Response |
|-----------|-------|-------------------|
| Simple call stack | `{"threadId":"main"}` | Array of frames with `index`, `className`, `methodName`, `lineNumber` |
| Deep recursion | 100+ frames | All frames returned (or paginated) |
| With lambdas | Lambda in stack | Lambda frame info |
| Pagination | `{"startFrame":5,"count":10}` | Frames 5-14 |
| Invalid thread | `{"threadId":"invalid"}` | Error |
| Thread not suspended | Running thread | Error |

#### Tool: `frame_select`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Select frame 0 | `{"frameIndex":0}` | Success | Top frame selected |
| Select frame N | `{"frameIndex":5}` | Success | Frame 5 selected |
| Out of bounds | `{"frameIndex":999}` | Error | "Invalid frame index" |
| Negative index | `{"frameIndex":-1}` | Error | "Invalid frame index" |

### Variable Inspection Tests

#### Tool: `variables_local`
| Test Case | Precondition | Expected Response |
|-----------|--------------|-------------------|
| Primitives | Frame with int, boolean | `[{"name":"x","type":"int","value":"42"}]` |
| Objects | Frame with String | `{"name":"s","type":"String","value":"hello","objectId":"obj-1"}` |
| Null values | Variable is null | `{"name":"n","type":"Object","value":"null"}` |
| No locals | Empty frame | `[]` |
| Uninitialized | Before assignment | Variable not in list or marked |
| Not suspended | - | Error |

#### Tool: `variables_arguments`
| Test Case | Precondition | Expected Response |
|-----------|--------------|-------------------|
| Method with args | `method(int x, String y)` | `[{"name":"x",...},{"name":"y",...}]` |
| No arguments | `method()` | `[]` |
| Varargs | `method(String... args)` | Array type argument |

#### Tool: `variable_inspect`
| Test Case | Input | Expected |
|-----------|-------|----------|
| Inspect primitive | `{"variableName":"count"}` | `{"type":"int","value":"42"}` |
| Inspect String | `{"variableName":"name"}` | `{"type":"String","value":"John","length":4}` |
| Inspect object | `{"variableName":"user"}` | Fields of the object |
| Inspect array | `{"variableName":"arr"}` | `{"type":"int[]","length":5,"elements":[...]}` |
| Nested object | `{"variableName":"user.address"}` | Address object fields |
| Non-existent | `{"variableName":"nonexistent"}` | Error |
| Circular reference | Object references itself | Handle gracefully (max depth) |

#### Tool: `object_fields`
| Test Case | Input | Expected |
|-----------|-------|----------|
| Simple object | `{"objectId":"obj-1"}` | All instance fields |
| With inheritance | Subclass instance | Fields from all levels |
| Static fields | - | Include static fields flag |
| Private fields | - | All fields regardless of visibility |
| Invalid objectId | `{"objectId":"invalid"}` | Error |
| Collected object | GC'd object | Error |

#### Tool: `array_elements`
| Test Case | Input | Expected |
|-----------|-------|----------|
| Full array | `{"arrayId":"arr-1"}` | All elements |
| Slice | `{"arrayId":"arr-1","start":5,"length":10}` | Elements 5-14 |
| Empty array | Empty array | `[]` |
| Out of bounds | Start > length | Error or empty |
| Primitive array | `int[]` | Primitive values |
| Object array | `String[]` | Object references |
| Multi-dimensional | `int[][]` | Nested arrays |

#### Tool: `this_object`
| Test Case | Precondition | Expected |
|-----------|--------------|----------|
| In instance method | Non-static method | `this` object fields |
| In static method | Static method | Error or null |
| In lambda | Lambda capturing this | Enclosing this |
| In constructor | During construction | Partially initialized object |

---

## Phase 5: Testing Guidelines

### Advanced Breakpoint Tests

#### Tool: `breakpoint_conditional`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Simple condition | `{"className":"Main","lineNumber":10,"condition":"x > 5"}` | BP created | Only hits when x > 5 |
| Complex condition | `{"condition":"list.size() > 0 && list.get(0).equals(\"test\")"}` | BP created | Evaluates correctly |
| Invalid expression | `{"condition":"invalid syntax {{"}` | Error | "Invalid condition" |
| Condition throws | `{"condition":"obj.method()"}` where obj is null | Skip or error | Consistent behavior |
| Condition modifies state | `{"condition":"x++"}` | Should work (side effects) | Document behavior |

#### Tool: `breakpoint_hitcount`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Hit count 5 | `{"breakpointId":"bp-1","count":5}` | Success | Hits on 5th time only |
| Hit count 1 | `{"count":1}` | Success | Hits first time |
| Hit count 0 | `{"count":0}` | Error or always hit | Consistent behavior |
| Reset count | Set new count | Counter resets | Fresh count |

#### Tool: `watchpoint_access`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Watch field read | `{"className":"Counter","fieldName":"value"}` | Watchpoint created | Triggers on read |
| Non-existent field | `{"fieldName":"nonexistent"}` | Error | "Field not found" |
| Private field | Private field | Works | Triggers correctly |
| Static field | Static field | Works | Triggers on access |
| Capability check | - | Error if unsupported | Check `canWatchFieldAccess()` |

#### Tool: `watchpoint_modification`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Watch field write | `{"className":"Counter","fieldName":"value"}` | Watchpoint created | Triggers on write |
| Capture old/new value | - | Values in event | Shows before/after |
| Constructor init | Field set in constructor | Triggers | Initial assignment caught |
| Capability check | - | Error if unsupported | Check `canWatchFieldModification()` |

#### Tool: `method_entry_break` / `method_exit_break`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Method entry | `{"className":"Service","methodName":"process"}` | BP created | Triggers on entry |
| Method exit | Same | BP created | Triggers on exit (normal) |
| Exit with exception | Method throws | Triggers | Exit via exception |
| Overloaded method | Multiple signatures | All or specify | Document behavior |
| Constructor | `<init>` | Works | Constructor entry/exit |

---

## Phase 6: Testing Guidelines

### Exception Breakpoint Tests

#### Tool: `exception_break_all`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Caught + uncaught | `{"caught":true,"uncaught":true}` | Breaks on all | Every throw stops |
| Caught only | `{"caught":true,"uncaught":false}` | Breaks on caught | Try-catch stops |
| Uncaught only | `{"caught":false,"uncaught":true}` | Breaks on uncaught | Propagating stops |
| Neither | `{"caught":false,"uncaught":false}` | Error or no-op | Validate input |

#### Tool: `exception_break_type`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Specific exception | `{"exceptionClass":"java.lang.NullPointerException"}` | Breaks on NPE only | Other exceptions pass |
| Subclass | `{"exceptionClass":"java.lang.RuntimeException"}` | Breaks on all runtime | Subclasses included |
| Custom exception | `{"exceptionClass":"com.example.MyException"}` | Works | Custom class handled |
| Non-exception class | `{"exceptionClass":"java.lang.String"}` | Error | "Not an exception type" |
| Non-existent class | `{"exceptionClass":"NonExistent"}` | Error | "Class not found" |

#### Tool: `exception_info`
| Test Case | Precondition | Expected Response |
|-----------|--------------|-------------------|
| At exception | Stopped at exception | `{"exceptionClass":"...","message":"...","stackTrace":[...]}` |
| Not at exception | Normal breakpoint | Error or null | "No exception at current location" |
| Exception with cause | Chained exception | Cause chain included |

### Exception Event Validation

| Scenario | Expected Behavior |
|----------|-------------------|
| try-catch-finally | Correct catch location reported |
| Nested try blocks | Innermost catch location |
| Exception rethrown | Multiple events if configured |
| Exception in finally | Both exceptions reported |
| Multi-catch | Correct handler identified |

---

## Phase 7: Testing Guidelines

### Expression Evaluation Tests

#### Tool: `evaluate_expression`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Simple arithmetic | `{"expression":"2 + 2"}` | `{"value":"4","type":"int"}` | Correct result |
| Variable reference | `{"expression":"count * 2"}` | Uses local variable | Correct context |
| Method call | `{"expression":"list.size()"}` | Invokes method | Returns result |
| Static method | `{"expression":"Math.max(a, b)"}` | Static invocation | Works |
| New object | `{"expression":"new ArrayList()"}` | Object created | Returns objectId |
| String concatenation | `{"expression":"\"Hello \" + name"}` | String result | Correct value |
| Null handling | `{"expression":"null"}` | Null value | Type is null |
| Syntax error | `{"expression":"2 +"}` | Error | "Syntax error" |
| Runtime error | `{"expression":"obj.method()"}` (obj null) | Error | NPE reported |
| Side effects | `{"expression":"count++"}` | Value changed | Document behavior |

#### Tool: `invoke_method`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Void method | `{"objectId":"obj-1","methodName":"doSomething","args":[]}` | Success | Side effect occurred |
| Return value | `{"methodName":"getValue"}` | Value returned | Correct type |
| With arguments | `{"methodName":"add","args":[1,2]}` | Success | Args passed correctly |
| Wrong arg types | Mismatched types | Error | "Argument type mismatch" |
| Private method | Private method | Works | Accessibility bypassed |
| Overloaded method | `{"methodSignature":"(II)I"}` | Correct overload | Signature matching |

#### Tool: `set_variable`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Set primitive | `{"variableName":"count","value":"42"}` | Success | Variable changed |
| Set object | `{"variableName":"name","value":"new String()"}` | Success | Reference updated |
| Set to null | `{"value":null}` | Success | Variable is null |
| Type mismatch | int to String | Error | "Type mismatch" |
| Final variable | final field | Error | "Cannot modify final" |
| Non-existent | `{"variableName":"unknown"}` | Error | "Variable not found" |

### Hot Code Replace Tests

#### Tool: `hot_swap`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Simple change | Changed method body | Success | New code executed |
| Add method | New method added | Error or success | Check `canAddMethod()` |
| Change signature | Method signature changed | Error | "Schema change not supported" |
| Add field | New field added | Error | Not supported |
| Invalid bytecode | Corrupted class file | Error | "Invalid bytecode" |
| Capability check | - | Error if unsupported | Check `canRedefineClasses()` |

### Advanced Control Tests

#### Tool: `force_return`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Return value | `{"value":"42"}` | Method returns 42 | Caller sees value |
| Return void | `{}` (void method) | Method returns | Caller continues |
| Return object | `{"value":"new Result()"}` | Object returned | Reference valid |
| Type mismatch | Wrong return type | Error | "Type mismatch" |
| Capability check | - | Error if unsupported | Check `canForceEarlyReturn()` |

#### Tool: `pop_frames`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Pop one frame | `{"frameCount":1}` | Back to caller | At call site |
| Pop multiple | `{"frameCount":3}` | 3 frames popped | Correct location |
| Pop all | More than available | Error | "Cannot pop all frames" |
| Native frame | Frame is native | Error | "Cannot pop native frame" |
| Capability check | - | Error if unsupported | Check `canPopFrames()` |

#### Tool: `instances_of`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Get instances | `{"className":"java.lang.String","maxCount":100}` | Instance list | ObjectIds returned |
| No instances | Class not instantiated | Empty list | `[]` |
| With limit | `{"maxCount":5}` | At most 5 | Respects limit |
| Interface | `{"className":"java.util.List"}` | All implementations | Includes ArrayList, etc. |
| Capability check | - | Error if unsupported | Check `canGetInstanceInfo()` |

---

## Phase 8: Testing Guidelines

### Class Event Tests

#### Tool: `class_prepare_watch`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| Watch pattern | `{"classPattern":"com.example.*"}` | Watch created | Events for matching classes |
| Exact class | `{"classPattern":"com.example.Service"}` | Single class | Only that class |
| Already loaded | Class already loaded | No event | Only new loads |
| Dynamic class | Dynamically generated | Event fires | Proxy classes, etc. |

#### Tool: `thread_start_watch` / `thread_death_watch`
| Test Case | Input | Expected | Validation |
|-----------|-------|----------|------------|
| New thread | `new Thread().start()` | Event received | Thread info in event |
| Thread pool | Executor creates thread | Event received | Correct thread |
| Thread dies | Thread.run() ends | Death event | Thread ID matches |

### Monitor Event Tests (Capability Dependent)

#### Tool: `monitor_contention_watch`
| Test Case | Precondition | Expected | Validation |
|-----------|--------------|----------|------------|
| Lock contention | Thread waits for lock | Event received | Waiting thread, owner thread |
| Lock acquired | After waiting | Entered event | Acquisition confirmed |
| Capability check | - | Error if unsupported | Check `canRequestMonitorEvents()` |

#### Tool: `monitor_wait_watch`
| Test Case | Precondition | Expected | Validation |
|-----------|--------------|----------|------------|
| Object.wait() | Thread calls wait() | Event received | Object, thread, timeout |
| wait() returns | notify/timeout | Waited event | Return reason |
| Timed wait | `wait(1000)` | Timeout in event | Timeout value |

---

## Integration Test Scenarios

### End-to-End Debugging Workflows

#### Scenario 1: Simple Debug Session
```
1. debug_launch("Main", classpath)       → Session started
2. breakpoint_set("Main", 10)            → Breakpoint ID returned
3. resume()                               → VM runs, hits breakpoint
4. execution_location()                   → Line 10 confirmed
5. variables_local()                      → Variables listed
6. step_over()                            → Line 11
7. resume()                               → Program ends
8. debug_disconnect()                     → Clean exit
```
**Validation**: Each step succeeds, locations correct, variables accurate.

#### Scenario 2: Multi-threaded Debugging
```
1. debug_launch("ThreadedApp", classpath)
2. breakpoint_set("Worker", 25)
3. resume()                               → Multiple threads hit
4. threads_list()                         → All threads visible
5. thread_select("worker-1")
6. stack_frames()                         → Worker stack
7. thread_select("worker-2")
8. stack_frames()                         → Different stack
9. resume()
```
**Validation**: Thread isolation correct, switching works.

#### Scenario 3: Exception Debugging
```
1. debug_launch("BuggyApp", classpath)
2. exception_break_type("NullPointerException", true, true)
3. resume()                               → Stops at NPE
4. exception_info()                       → Exception details
5. stack_frames()                         → Stack at throw point
6. variables_local()                      → Null variable visible
```
**Validation**: Exception caught, cause identifiable.

#### Scenario 4: Attach and Inspect
```
1. (Start external JVM with debug port 5005)
2. debug_attach_socket("localhost", 5005) → Connected
3. suspend()                              → VM paused
4. threads_list()                         → Running threads
5. thread_select("main")
6. stack_frames()                         → Current stack
7. resume()
8. debug_disconnect()
```
**Validation**: Attach works, state inspectable.

---

## Error Handling Tests

### MCP Protocol Errors

| Error Code | Condition | Test |
|------------|-----------|------|
| -32700 | Parse error | Send malformed JSON |
| -32600 | Invalid request | Missing required fields |
| -32601 | Method not found | Call unknown tool |
| -32602 | Invalid params | Wrong parameter types/schema |
| -32603 | Internal error | Force internal exception |

### Debug Session Errors

| Error Condition | Expected Behavior | Test Method |
|-----------------|-------------------|-------------|
| Not connected | All debug tools return error | Call tool without session |
| VM disconnected unexpectedly | Detect and report | Kill target process |
| VM exited | Session marked ended | Let program complete |
| Thread died | Invalidate references | Access dead thread |
| Object GC'd | ObjectCollectedException | Access collected object |
| Invalid object ID | Error with message | Use fabricated ID |
| Class not loaded | Wait or error | BP on not-yet-loaded class |

### Concurrent Access Errors

| Scenario | Expected Behavior |
|----------|-------------------|
| Multiple tools simultaneously | Proper synchronization |
| Long-running evaluation | Timeout handling |
| Event during tool call | Queue events properly |

---

## Test Program: DebugPlayground

A comprehensive test program is located at `src/main/java/com/nanik/debuggee/DebugPlayground.java`.

### Running the Test Program

```bash
# Compile
./gradlew build

# Run with debug agent (suspend=y waits for debugger)
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 \
     -cp build/classes/java/main \
     com.nanik.debuggee.DebugPlayground

# Or run without waiting (suspend=n)
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -cp build/classes/java/main \
     com.nanik.debuggee.DebugPlayground
```

### DebugPlayground Coverage Map

| Phase | Feature | Method/Location | Line |
|-------|---------|-----------------|------|
| **2** | Launch/Attach | `main()` entry | 37 |
| **3** | Line Breakpoint | `runBasicOperations()` loop | 72-73 |
| **3** | Step Over | Loop body | 73 |
| **3** | Step Into/Out | `methodCallChain()` → `levelOne()` → `levelTwo()` → `levelThree()` | 124-139 |
| **4** | Primitives | `demonstrateDataTypes()` | 99-106 |
| **4** | Arrays | `demonstrateDataTypes()` | 109-111 |
| **4** | Objects/Collections | `demonstrateDataTypes()` | 113-120 |
| **4** | Null Inspection | `nullObj` variable | 115 |
| **4** | Stack Frames | `fibonacci()` recursion | 144-147 |
| **4** | Threads List | `runThreadDemo()` workers | 199 |
| **5** | Watchpoint (static) | `staticCounter` field | 26 |
| **5** | Watchpoint (instance) | `instanceCounter` field | 30 |
| **5** | Watchpoint (String) | `status` field | 31, 87, 183 |
| **5** | Method Entry/Exit | `levelOne/Two/Three()` | 128-139 |
| **6** | Caught Exception | `demonstrateExceptions()` try-catch | 156-158 |
| **6** | Rethrow | Nested try-catch | 166 |
| **6** | Exception Source | `riskyOperation()` throw | 182 |
| **7** | Expression Eval | Any suspended location | - |
| **7** | Invoke Method | Call `getValue()` on Counter | 307 |
| **7** | Set Variable | Modify `instanceCounter` | 30 |
| **8** | Thread Start | Worker thread creation | 195-206 |
| **8** | Monitor Enter | `synchronized` block | 226, 239 |
| **8** | Lambda | Stream operations | 253-254 |
| **8** | Inner Class | `Counter` class | 299-303 |
| **8** | Static Nested | `StaticHelper` class | 315 |

### Manual Testing Scenarios

#### Scenario 1: Basic Breakpoint & Stepping (Phase 2-3)
```
1. Start DebugPlayground with suspend=y
2. debug_attach_socket("localhost", 5005)
3. breakpoint_set("com.nanik.debuggee.DebugPlayground", 73)
4. resume()
   → Hits breakpoint at line 73 (inside loop)
5. variables_local()
   → See: sum=0, i=1
6. step_over()
   → Now at line 74
7. step_over() x3
   → Back at line 73, i=2
8. resume()
   → Hits again, i=3
```

#### Scenario 2: Step Into Method Chain (Phase 3)
```
1. breakpoint_set("com.nanik.debuggee.DebugPlayground", 124)
2. resume()
   → Hits at methodCallChain()
3. step_into()
   → Now inside levelOne()
4. step_into()
   → Now inside levelTwo()
5. step_into()
   → Now inside levelThree()
6. step_out()
   → Back in levelTwo() after return
7. step_out()
   → Back in levelOne()
8. step_out()
   → Back in methodCallChain()
```

#### Scenario 3: Inspect Variables (Phase 4)
```
1. breakpoint_set("com.nanik.debuggee.DebugPlayground", 118)
2. resume()
   → Hits in demonstrateDataTypes()
3. variables_local()
   → See all primitive types: b, s, i, l, f, d, c, bool
4. variable_inspect("intArray")
   → See: int[5] = {1, 2, 3, 4, 5}
5. variable_inspect("stringArray")
   → See: String[3] = {"hello", "world", "debug"}
6. this_object()
   → See: messages, scores, status fields
7. object_fields(this.messages)
   → See ArrayList contents
```

#### Scenario 4: Multi-threaded Debugging (Phase 4)
```
1. breakpoint_set("com.nanik.debuggee.DebugPlayground", 227)
2. resume()
   → Multiple threads may hit
3. threads_list()
   → See: main, Worker-0, Worker-1, Worker-2
4. thread_select("Worker-1")
5. stack_frames()
   → See doWork() → lambda → run()
6. variables_local()
   → See workerId, i
7. thread_select("Worker-2")
8. stack_frames()
   → Different stack state
```

#### Scenario 5: Watchpoints (Phase 5)
```
1. watchpoint_modification("com.nanik.debuggee.DebugPlayground", "instanceCounter")
2. resume()
   → Stops on first modification in runBasicOperations()
3. stack_frames()
   → See where modification occurred
4. resume()
   → Stops on next modification
5. watchpoint_remove(watchpointId)
6. resume()
   → Runs without stopping
```

#### Scenario 6: Exception Breakpoints (Phase 6)
```
1. exception_break_type("java.lang.IllegalArgumentException", true, false)
2. resume()
   → Stops at throw in riskyOperation()
3. exception_info()
   → See: "Intentional failure!"
4. stack_frames()
   → See: riskyOperation() ← demonstrateExceptions()
5. variables_local()
   → See: shouldFail=true
6. resume()
   → Caught by catch block, continues
```

#### Scenario 7: Lambda Debugging (Phase 8)
```
1. breakpoint_set("com.nanik.debuggee.DebugPlayground", 253)
2. resume()
   → Stops in filter lambda
3. variables_local()
   → See: n (current element)
4. step_over()
   → Next element or exit lambda
5. resume()
   → Continues stream processing
```

---

## Test Execution Requirements

### Per-Phase Completion Criteria

| Phase | Unit Test Coverage | Integration Tests | System Tests |
|-------|-------------------|-------------------|--------------|
| Phase 1 | 95% | MCP lifecycle | Manual stdio test |
| Phase 2 | 90% | Launch + attach | Real JVM connection |
| Phase 3 | 90% | Full debug cycle | Step through real code |
| Phase 4 | 90% | Inspect all types | Complex object graphs |
| Phase 5 | 85% | Watchpoint events | Field access patterns |
| Phase 6 | 85% | Exception flows | Try-catch scenarios |
| Phase 7 | 80% | Eval + modify | Hot swap test |
| Phase 8 | 80% | Event streams | Long-running monitors |

### CI/CD Integration

```yaml
# Suggested test stages
stages:
  - unit-tests:
      command: ./gradlew test
      coverage: jacoco
      threshold: 85%

  - integration-tests:
      command: ./gradlew integrationTest
      requires: unit-tests

  - system-tests:
      command: ./gradlew systemTest
      requires: integration-tests
      environment:
        - JAVA_HOME: /path/to/jdk
```

---

## Next Steps

1. **Phase 1**: Set up project structure and implement MCP protocol handler
2. Add Gson dependency to build.gradle
3. Implement stdio transport and JSON-RPC 2.0 parsing
4. Create tool registry framework
5. Build first tools: `ping`, `tools/list`
6. **Write Phase 1 tests FIRST before implementation**

