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
 * Tool to watch for class loading (prepare) events.
 */
public class ClassPrepareWatchTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("classFilter", "Class name pattern to watch (e.g., 'com.example.*'). Use '*' or omit for all classes.", false)
            .build();

        return new ToolDefinition(
            "class_prepare_watch",
            "Watch for class loading events. Returns a watch ID. Use events_pending to retrieve captured events.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }

        String classFilter = request.getStringParam("classFilter");
        VirtualMachine vm = DebugSession.getInstance().getVm();

        try {
            // Start event thread if not running
            DebugSession.getInstance().startEventThread();

            String watchId = EventMonitorManager.getInstance().watchClassPrepare(vm, classFilter);

            StringBuilder sb = new StringBuilder();
            sb.append("Class prepare watch created.\n");
            sb.append("Watch ID: ").append(watchId).append("\n");
            if (classFilter != null && !classFilter.isEmpty() && !classFilter.equals("*")) {
                sb.append("Filter: ").append(classFilter);
            } else {
                sb.append("Filter: (all classes)");
            }
            
            return ToolResult.success(sb.toString());

        } catch (Exception e) {
            return ToolResult.error("Failed to create class prepare watch: " + e.getMessage());
        }
    }
}

