package com.nanik.tracepilot.tools.impl;

import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.debug.StopReason;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.*;
import com.sun.jdi.Location;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tool to get the current debug session status.
 *
 * Returns comprehensive information about:
 * - Connection status
 * - VM state (RUNNING/STOPPED)
 * - Stop reason (why we stopped: breakpoint, step, exception, etc.)
 * - Current location if stopped
 * - Thread states
 */
public class DebugStatusTool implements ToolHandler {

    private static final ToolDefinition DEFINITION = ToolDefinition.noParams(
        "debug_status",
        "Get debug status: STOPPED or RUNNING, stop reason (breakpoint/step/exception), location, and threads. Check this first to understand current state."
    );

    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(McpRequest request) {
        DebugSession session = DebugSession.getInstance();

        if (!session.isConnected()) {
            return ToolResult.success("state: DISCONNECTED\n\nNot connected to any VM.\nUse debug_launch, debug_attach_socket, or debug_attach_pid to connect.");
        }

        VirtualMachine vm = session.getVm();
        StopReason stopReason = session.getLastStopReason();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Debug Session Status ===\n\n");

        // VM State (most important for LLM debugging)
        boolean isStopped = stopReason != null && stopReason.isStopped();
        sb.append("state: ").append(isStopped ? "STOPPED" : "RUNNING").append("\n\n");

        // Stop Reason (if stopped)
        if (isStopped) {
            sb.append("=== Stop Reason ===\n");
            sb.append("type: ").append(stopReason.getType().name()).append("\n");

            if (stopReason.getThreadName() != null) {
                sb.append("thread: ").append(stopReason.getThreadName());
                sb.append(" (id: ").append(stopReason.getThreadId()).append(")\n");
            }

            Location loc = stopReason.getLocation();
            if (loc != null) {
                sb.append("location:\n");
                sb.append("  class: ").append(loc.declaringType().name()).append("\n");
                sb.append("  method: ").append(loc.method().name()).append("\n");
                sb.append("  line: ").append(loc.lineNumber()).append("\n");
                try {
                    String sourcePath = loc.sourcePath();
                    sb.append("  source: ").append(sourcePath).append("\n");
                } catch (Exception e) {
                    // No source info available
                }
            }

            Map<String, String> details = stopReason.getDetails();
            if (details != null && !details.isEmpty()) {
                sb.append("details:\n");
                for (Map.Entry<String, String> entry : details.entrySet()) {
                    sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }
            sb.append("\n");
        }

        // Connection info
        sb.append("=== Connection ===\n");
        sb.append("connection: ").append(session.getConnectionDetails()).append("\n");
        sb.append("type: ").append(session.getConnectionType()).append("\n");

        long duration = System.currentTimeMillis() - session.getConnectedAt();
        sb.append("connected_for: ").append(formatDuration(duration)).append("\n\n");

        // VM info
        sb.append("=== VM Info ===\n");
        sb.append("name: ").append(vm.name()).append("\n");
        sb.append("version: ").append(vm.version()).append("\n\n");

        // Thread states
        sb.append("=== Threads ===\n");
        try {
            List<ThreadReference> threads = vm.allThreads();
            List<String> suspendedThreadNames = new ArrayList<>();
            List<String> runningThreadNames = new ArrayList<>();

            for (ThreadReference thread : threads) {
                String threadInfo = thread.name() + " (id: " + thread.uniqueID() + ")";
                if (thread.isSuspended()) {
                    suspendedThreadNames.add(threadInfo);
                } else {
                    runningThreadNames.add(threadInfo);
                }
            }

            sb.append("total: ").append(threads.size()).append("\n");
            sb.append("suspended: ").append(suspendedThreadNames.size()).append("\n");
            sb.append("running: ").append(runningThreadNames.size()).append("\n");

            // Show suspended threads (usually the ones we care about)
            if (!suspendedThreadNames.isEmpty()) {
                sb.append("\nSuspended threads:\n");
                for (String name : suspendedThreadNames) {
                    sb.append("  - ").append(name).append("\n");
                }
            }
        } catch (Exception e) {
            sb.append("error: Unable to retrieve threads (").append(e.getMessage()).append(")\n");
        }

        return ToolResult.success(sb.toString());
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
}

