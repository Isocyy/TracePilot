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
 * Tool to remove an event watch by its ID.
 */
public class EventWatchRemoveTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("watchId", "ID of the watch to remove (e.g., 'cp-1', 'ts-2')", true)
            .build();

        return new ToolDefinition(
            "event_watch_remove",
            "Remove an event watch by its ID. Watch IDs are returned when creating watches " +
            "(cp- for class prepare, cu- for class unload, ts- for thread start, td- for thread death, mc- for monitor contention).",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }

        String watchId = request.getStringParam("watchId");
        if (watchId == null || watchId.isEmpty()) {
            return ToolResult.error("watchId is required");
        }

        VirtualMachine vm = DebugSession.getInstance().getVm();

        boolean removed = EventMonitorManager.getInstance().removeWatch(vm, watchId);

        if (removed) {
            return ToolResult.success("Event watch removed: " + watchId);
        } else {
            return ToolResult.error("Watch not found: " + watchId);
        }
    }
}

