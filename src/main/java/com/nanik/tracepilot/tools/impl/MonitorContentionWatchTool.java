package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.debug.EventMonitorManager;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.VirtualMachine;

/**
 * Tool to watch for monitor contention events.
 * Triggers when a thread is blocked trying to enter a synchronized block.
 */
public class MonitorContentionWatchTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder().build();

        return new ToolDefinition(
            "monitor_contention_watch",
            "Watch for monitor contention events (when a thread is blocked waiting for a lock). " +
            "Requires VM support for monitor events. Use events_pending to retrieve captured events.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }

        VirtualMachine vm = DebugSession.getInstance().getVm();

        // Check capability
        if (!vm.canRequestMonitorEvents()) {
            return ToolResult.error("VM does not support monitor events.");
        }

        try {
            // Start event thread if not running
            DebugSession.getInstance().startEventThread();

            String watchId = EventMonitorManager.getInstance().watchMonitorContention(vm);

            StringBuilder sb = new StringBuilder();
            sb.append("Monitor contention watch created.\n");
            sb.append("Watch ID: ").append(watchId).append("\n");
            sb.append("Will capture events when threads block waiting for locks.");
            
            return ToolResult.success(sb.toString());

        } catch (UnsupportedOperationException e) {
            return ToolResult.error("VM does not support monitor events: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to create monitor contention watch: " + e.getMessage());
        }
    }
}

