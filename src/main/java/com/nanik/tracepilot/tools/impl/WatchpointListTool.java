package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.WatchpointInfo;
import com.nanik.tracepilot.debug.WatchpointManager;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

import java.util.List;

/**
 * Tool to list all watchpoints (access and modification).
 */
public class WatchpointListTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder().build();

        return new ToolDefinition(
            "watchpoint_list",
            "List all watchpoints (field access and modification) with their IDs, type, and status.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        WatchpointManager manager = WatchpointManager.getInstance();
        List<WatchpointInfo> watchpoints = manager.getAllWatchpoints();

        if (watchpoints.isEmpty()) {
            return ToolResult.success("No watchpoints set.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Watchpoints ===\n\n");

        for (WatchpointInfo info : watchpoints) {
            sb.append(info.getId());
            sb.append("  ");
            sb.append(info.getType() == WatchpointInfo.WatchpointType.ACCESS ? "ACCESS" : "MODIFICATION");
            sb.append("  ");
            sb.append(info.getClassName());
            sb.append(".");
            sb.append(info.getFieldName());

            if (info.isPending()) {
                sb.append(" [pending]");
            } else if (!info.isEnabled()) {
                sb.append(" [disabled]");
            } else {
                sb.append(" [enabled]");
            }

            sb.append("\n");
        }

        sb.append("\nTotal: ").append(watchpoints.size()).append(" watchpoint(s)");

        return ToolResult.success(sb.toString());
    }
}

