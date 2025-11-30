package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.WatchExpressionManager;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

/**
 * Tool to add a watch expression for persistent tracking.
 * Watch expressions are evaluated on demand using watch_evaluate_all.
 */
public class WatchAddTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("expression", "Expression to watch (e.g., 'this.counter', 'list.size()', 'user.getName()')", true)
            .build();

        return new ToolDefinition(
            "watch_add",
            "Add a watch expression for persistent tracking. Use watch_evaluate_all to evaluate all watches when VM is suspended.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        String expression = request.getStringParam("expression");
        
        if (expression == null || expression.trim().isEmpty()) {
            return ToolResult.error("expression is required");
        }

        try {
            WatchExpressionManager manager = WatchExpressionManager.getInstance();
            String watchId = manager.addWatch(expression);

            StringBuilder sb = new StringBuilder();
            sb.append("Watch expression added.\n");
            sb.append("ID: ").append(watchId).append("\n");
            sb.append("Expression: ").append(expression).append("\n");
            sb.append("Total watches: ").append(manager.getWatchCount()).append("\n\n");
            sb.append("Use watch_evaluate_all to evaluate all watches when VM is suspended.");

            return ToolResult.success(sb.toString());
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to add watch: " + e.getMessage());
        }
    }
}

