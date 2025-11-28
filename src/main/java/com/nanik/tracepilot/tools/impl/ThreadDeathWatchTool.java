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
 * Tool to watch for thread death events.
 */
public class ThreadDeathWatchTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder().build();

        return new ToolDefinition(
            "thread_death_watch",
            "Watch for thread termination events. Returns a watch ID. Use events_pending to retrieve captured events.",
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

            String watchId = EventMonitorManager.getInstance().watchThreadDeath(vm);

            StringBuilder sb = new StringBuilder();
            sb.append("Thread death watch created.\n");
            sb.append("Watch ID: ").append(watchId).append("\n");
            sb.append("Will capture all thread termination events.");
            
            return ToolResult.success(sb.toString());

        } catch (Exception e) {
            return ToolResult.error("Failed to create thread death watch: " + e.getMessage());
        }
    }
}

