package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Location;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;

import java.util.List;

/**
 * Tool to list all threads in the target VM with their status.
 */
public class ThreadsListTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addBoolean("includeSystemThreads", "Include system threads (default: true)", false, true)
            .build();
        return new ToolDefinition(
            "threads_list",
            "List all threads with ID, name, status. Use thread ID for stack_frames, variables_local. Suspended threads can be inspected.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        DebugSession session = DebugSession.getInstance();
        if (!session.isConnected()) {
            return ToolResult.error("Not connected to a VM. Use debug_attach_socket or debug_launch first.");
        }

        VirtualMachine vm = session.getVm();
        Boolean includeSystemParam = request.getBoolParam("includeSystemThreads");
        boolean includeSystem = includeSystemParam == null || includeSystemParam;

        List<ThreadReference> threads = vm.allThreads();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Threads (").append(threads.size()).append(" total) ===\n\n");

        int userThreadCount = 0;
        for (ThreadReference thread : threads) {
            String name = thread.name();
            
            // Filter system threads if requested
            if (!includeSystem && isSystemThread(name)) {
                continue;
            }
            userThreadCount++;

            sb.append("Thread: ").append(name).append("\n");
            sb.append("  ID: ").append(thread.uniqueID()).append("\n");
            sb.append("  Status: ").append(getThreadStatusString(thread.status())).append("\n");
            sb.append("  Suspended: ").append(thread.isSuspended()).append("\n");
            sb.append("  Suspend Count: ").append(thread.suspendCount()).append("\n");

            // Get current location if suspended
            if (thread.isSuspended()) {
                try {
                    List<StackFrame> frames = thread.frames();
                    if (!frames.isEmpty()) {
                        Location loc = frames.get(0).location();
                        sb.append("  Location: ").append(loc.declaringType().name())
                          .append(".").append(loc.method().name())
                          .append("() at line ").append(loc.lineNumber()).append("\n");
                    }
                } catch (IncompatibleThreadStateException e) {
                    sb.append("  Location: <unavailable>\n");
                }
            }
            sb.append("\n");
        }

        if (!includeSystem) {
            sb.append("(Showing ").append(userThreadCount).append(" user threads, ")
              .append(threads.size() - userThreadCount).append(" system threads hidden)\n");
        }

        return ToolResult.success(sb.toString());
    }

    private boolean isSystemThread(String name) {
        return name.equals("Reference Handler") ||
               name.equals("Finalizer") ||
               name.equals("Signal Dispatcher") ||
               name.equals("Common-Cleaner") ||
               name.startsWith("GC ") ||
               name.startsWith("C1 ") ||
               name.startsWith("C2 ") ||
               name.equals("Attach Listener") ||
               name.equals("Notification Thread");
    }

    private String getThreadStatusString(int status) {
        switch (status) {
            case ThreadReference.THREAD_STATUS_UNKNOWN: return "UNKNOWN";
            case ThreadReference.THREAD_STATUS_ZOMBIE: return "ZOMBIE";
            case ThreadReference.THREAD_STATUS_RUNNING: return "RUNNING";
            case ThreadReference.THREAD_STATUS_SLEEPING: return "SLEEPING";
            case ThreadReference.THREAD_STATUS_MONITOR: return "WAITING_ON_MONITOR";
            case ThreadReference.THREAD_STATUS_WAIT: return "WAITING";
            case ThreadReference.THREAD_STATUS_NOT_STARTED: return "NOT_STARTED";
            default: return "UNKNOWN(" + status + ")";
        }
    }
}

