package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.WatchExpressionManager;
import com.nanik.tracepilot.debug.WatchExpressionManager.WatchExpression;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

import java.util.Collection;

/**
 * Tool to list all watch expressions.
 */
public class WatchListTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder().build();

        return new ToolDefinition(
            "watch_list",
            "List all watch expressions with their IDs and last evaluated values.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        WatchExpressionManager manager = WatchExpressionManager.getInstance();
        Collection<WatchExpression> watches = manager.getAllWatches();

        if (watches.isEmpty()) {
            return ToolResult.success("No watch expressions defined.\n\nUse watch_add to add expressions to watch.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Watch Expressions (").append(watches.size()).append(") ===\n\n");

        for (WatchExpression watch : watches) {
            sb.append("ID: ").append(watch.getId()).append("\n");
            sb.append("Expression: ").append(watch.getExpression()).append("\n");
            
            if (watch.hasBeenEvaluated()) {
                if (watch.getLastError() != null) {
                    sb.append("Last Value: <error> ").append(watch.getLastError()).append("\n");
                } else if (watch.getLastValue() != null) {
                    sb.append("Last Value: ").append(watch.getLastValue()).append("\n");
                } else {
                    sb.append("Last Value: null\n");
                }
            } else {
                sb.append("Last Value: <not yet evaluated>\n");
            }
            sb.append("\n");
        }

        sb.append("Use watch_evaluate_all to refresh all values when VM is suspended.");

        return ToolResult.success(sb.toString());
    }
}

