# TracePilot MCP Usage Guide

## Quick Reference for LLM Agents

### 🚀 Connection Flow
```
1. debug_attach_socket(port) OR debug_launch(mainClass, classpath)
2. Set breakpoints/watchpoints/exception breaks
3. resume() → wait_for_stop() (ALWAYS pair these)
4. Inspect: variables_local, stack_frames, evaluate_expression
5. Navigate: step_over, step_into, step_out → wait_for_stop()
6. debug_disconnect() when done
```

### ⚠️ Critical Rules

| Rule | Why |
|------|-----|
| Always call `wait_for_stop()` after `resume()` or `step_*` | Async operations - must wait for VM to stop |
| Check `debug_status()` first | Know if VM is STOPPED/RUNNING/DISCONNECTED |
| Thread must be suspended for inspection | Can't read variables from running threads |
| Use pending breakpoints | Classes may not be loaded yet - they activate on load |

### 🔧 Common Debugging Patterns

**Pattern 1: Find where exception occurs**
```
exception_break_on(exceptionClass="*", uncaught=true)
resume() → wait_for_stop()
exception_info()           # Get exception details
stack_frames(threadId)     # See call stack
variables_local(threadId, frameIndex=N)  # Check each frame
```

**Pattern 2: Track variable changes**
```
watchpoint_modification(className, fieldName)
resume() → wait_for_stop()
# Stops when field is written - inspect new value
```

**Pattern 3: Step through code**
```
breakpoint_set(className, lineNumber)
resume() → wait_for_stop()
step_over() → wait_for_stop()   # Next line
step_into() → wait_for_stop()   # Enter method
step_out() → wait_for_stop()    # Exit method
```

**Pattern 5: Evaluate and modify**
```
evaluate_expression(threadId, "variable.field")
set_variable(threadId, variableName, "newValue")
invoke_method(threadId, objectId, "methodName", args="[]")
```

### 📋 Tool Categories

| Category | Tools |
|----------|-------|
| **Connect** | `debug_attach_socket`, `debug_launch`, `debug_disconnect` |
| **Status** | `debug_status`, `vm_info`, `threads_list` |
| **Breakpoints** | `breakpoint_set/list/remove/enable/disable` |
| **Watchpoints** | `watchpoint_access/modification`, `watchpoint_list/remove` |
| **Exceptions** | `exception_break_on/list/remove`, `exception_info` |
| **Methods** | `method_entry_break`, `method_exit_break`, `method_breakpoint_list/remove` |
| **Execution** | `resume`, `suspend`, `wait_for_stop` |
| **Stepping** | `step_into`, `step_over`, `step_out` |
| **Inspection** | `variables_local`, `variables_arguments`, `variable_inspect` |
| **Objects** | `this_object`, `object_fields`, `array_elements` |
| **Evaluation** | `evaluate_expression`, `set_variable`, `invoke_method`, `invoke_static` |
| **Events** | `class_prepare_watch`, `thread_start_watch`, `events_pending` |

### 🎯 Debugging Strategy

1. **Reproduce** - Run program to see the error
2. **Hypothesize** - Form theory about cause
3. **Instrument** - Set appropriate breakpoints/watchpoints
4. **Execute** - `resume()` → `wait_for_stop()`
5. **Inspect** - Check variables, stack, expressions
6. **Iterate** - Step through or adjust breakpoints
7. **Conclude** - Identify root cause

### 💡 Pro Tips

- Use `frameIndex` parameter to inspect variables at different call stack levels
- `evaluate_expression` works for simple expressions like `obj.field` or `var`
- Object IDs (e.g., `@267`) can be used with `object_fields` for deep inspection
- Set breakpoints on lines with **executable code**, not comments or blank lines
- For multi-threaded apps, check `threads_list` to see all thread states

