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
 * Tool to watch for thread start events.
 */
public class ThreadStartWatchTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder().build();

        return new ToolDefinition(
            "thread_start_watch",
            "Watch for new thread creation events. Returns a watch ID. Use events_pending to retrieve captured events.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }

        VirtualMachine vm = DebugSession.getInstance().getVm();

        try {
            // Start event thread if not running
            DebugSession.getInstance().startEventThread();

            String watchId = EventMonitorManager.getInstance().watchThreadStart(vm);

            StringBuilder sb = new StringBuilder();
            sb.append("Thread start watch created.\n");
            sb.append("Watch ID: ").append(watchId).append("\n");
            sb.append("Will capture all new thread creation events.");
            
            return ToolResult.success(sb.toString());

        } catch (Exception e) {
            return ToolResult.error("Failed to create thread start watch: " + e.getMessage());
        }
    }
}

