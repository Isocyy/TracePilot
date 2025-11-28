# TracePilot MCP Setup Guide

TracePilot is a Model Context Protocol (MCP) server that enables LLMs to debug Java applications.

## Prerequisites

- **Java 11+** installed and available in PATH
- **TracePilot.jar** (the fat JAR with all dependencies)

## Quick Start

### 1. Build TracePilot (if not already built)

```bash
cd /path/to/TracePilot
./gradlew fatJar
# Output: build/libs/TracePilot-1.0-SNAPSHOT-all.jar
```

### 2. Copy JAR to a permanent location

```bash
cp build/libs/TracePilot-1.0-SNAPSHOT-all.jar ~/TracePilot.jar
```

---

## Client Configuration

### Claude Desktop

**Config file location:**
- macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`
- Windows: `%APPDATA%\Claude\claude_desktop_config.json`
- Linux: `~/.config/Claude/claude_desktop_config.json`

**Configuration:**
```json
{
  "mcpServers": {
    "tracepilot": {
      "command": "java",
      "args": ["-jar", "/Users/YOUR_USERNAME/TracePilot.jar"]
    }
  }
}
```

**Steps:**
1. Create/edit the config file above
2. Replace `/Users/YOUR_USERNAME/TracePilot.jar` with your actual path
3. Restart Claude Desktop completely (quit and reopen)
4. TracePilot's 52 tools will appear in Claude's tool list

---

### Cursor IDE

**Config file:** `~/.cursor/mcp.json`

```json
{
  "mcpServers": {
    "tracepilot": {
      "command": "java",
      "args": ["-jar", "/Users/YOUR_USERNAME/TracePilot.jar"]
    }
  }
}
```

---

### VS Code + Continue Extension

**Config file:** `~/.continue/config.json`

Add to the `mcpServers` section:
```json
{
  "mcpServers": [
    {
      "name": "tracepilot",
      "command": "java",
      "args": ["-jar", "/Users/YOUR_USERNAME/TracePilot.jar"]
    }
  ]
}
```

---

## Usage Workflow

### Step 1: Start your Java application with debug agent

```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 \
     -cp your-app.jar com.example.Main
```

The application will wait for a debugger to connect.

### Step 2: Ask the LLM to debug

Example prompts:
- "Connect to the Java app on port 5005 and set a breakpoint at line 42 in Main.java"
- "Debug my Java application - it's throwing a NullPointerException"
- "Step through the calculateTotal method and show me the variables"

### Step 3: The LLM will use TracePilot tools

```
debug_attach_socket(port=5005)     → Connect to JVM
breakpoint_set(className, line)    → Set breakpoints
resume() + wait_for_stop()         → Run until breakpoint
variables_local(threadId)          → Inspect variables
step_over() + wait_for_stop()      → Step through code
```

---

## Verify Installation

Test TracePilot manually:

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' | java -jar ~/TracePilot.jar
```

Expected output:
```json
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","serverInfo":{"name":"TracePilot","version":"1.0.0"},"capabilities":{"tools":{}}}}
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "java: command not found" | Install Java 11+ and add to PATH |
| Tools not appearing in Claude | Restart Claude Desktop completely |
| "Connection refused" on port 5005 | Start target JVM with debug agent first |
| Breakpoint shows "PENDING" | Class not loaded yet; resume and it will activate |

---

## Available Tools (52 total)

| Category | Tools |
|----------|-------|
| Connection | `debug_attach_socket`, `debug_attach_pid`, `debug_launch`, `debug_disconnect` |
| Status | `debug_status`, `vm_info`, `ping` |
| Breakpoints | `breakpoint_set`, `breakpoint_remove`, `breakpoint_list`, `breakpoint_enable`, `breakpoint_disable` |
| Stepping | `step_into`, `step_over`, `step_out`, `resume`, `suspend` |
| Threads | `threads_list`, `thread_suspend`, `thread_resume` |
| Inspection | `stack_frames`, `variables_local`, `variables_arguments`, `variable_inspect`, `object_fields`, `array_elements`, `this_object` |
| Watchpoints | `watchpoint_access`, `watchpoint_modification`, `watchpoint_remove`, `watchpoint_list` |
| Methods | `method_entry_break`, `method_exit_break`, `method_breakpoint_remove`, `method_breakpoint_list` |
| Exceptions | `exception_break_on`, `exception_break_remove`, `exception_break_list`, `exception_info` |
| Evaluation | `evaluate_expression`, `invoke_method`, `invoke_static`, `set_variable` |
| Events | `class_prepare_watch`, `class_unload_watch`, `thread_start_watch`, `thread_death_watch`, `monitor_contention_watch`, `events_pending`, `event_watch_remove` |
| Control | `wait_for_stop`, `execution_location` |

