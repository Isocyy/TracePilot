package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.WatchExpressionManager;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

/**
 * Tool to remove a watch expression.
 */
public class WatchRemoveTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("watchId", "ID of the watch expression to remove (e.g., 'w-1')", true)
            .build();

        return new ToolDefinition(
            "watch_remove",
            "Remove a watch expression by its ID.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        String watchId = request.getStringParam("watchId");
        
        if (watchId == null || watchId.trim().isEmpty()) {
            return ToolResult.error("watchId is required");
        }

        WatchExpressionManager manager = WatchExpressionManager.getInstance();
        
        // Get the expression before removing for the response
        WatchExpressionManager.WatchExpression watch = manager.getWatch(watchId);
        
        if (manager.removeWatch(watchId)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Watch expression removed.\n");
            sb.append("ID: ").append(watchId).append("\n");
            if (watch != null) {
                sb.append("Expression: ").append(watch.getExpression()).append("\n");
            }
            sb.append("Remaining watches: ").append(manager.getWatchCount());
            return ToolResult.success(sb.toString());
        } else {
            return ToolResult.error("Watch expression not found: " + watchId);
        }
    }
}

