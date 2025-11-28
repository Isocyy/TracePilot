package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.debug.EventMonitorManager;
import com.nanik.tracepilot.debug.EventMonitorManager.CapturedEvent;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Tool to retrieve pending captured events from event watches.
 */
public class EventsPendingTool implements ToolHandler {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addBoolean("peek", "If true, don't remove events from queue (default: false - events are consumed)", false)
            .build();

        return new ToolDefinition(
            "events_pending",
            "Get pending events captured by event watches. By default, events are consumed (removed from queue). " +
            "Use peek=true to view without consuming.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }

        Boolean peek = request.getBoolParam("peek");
        if (peek == null) peek = false;

        EventMonitorManager manager = EventMonitorManager.getInstance();

        // Also show active watches
        Map<String, String> activeWatches = manager.getActiveWatches();

        List<CapturedEvent> events;
        if (peek) {
            events = manager.peekPendingEvents();
        } else {
            events = manager.getPendingEvents();
        }

        StringBuilder sb = new StringBuilder();
        
        // Active watches
        sb.append("Active Watches (").append(activeWatches.size()).append("):\n");
        if (activeWatches.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (Map.Entry<String, String> entry : activeWatches.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        sb.append("\n");

        // Pending events
        sb.append("Pending Events (").append(events.size()).append(")");
        if (peek) {
            sb.append(" [peek mode - not consumed]");
        }
        sb.append(":\n");

        if (events.isEmpty()) {
            sb.append("  (no pending events)\n");
        } else {
            for (CapturedEvent event : events) {
                sb.append("  [").append(TIME_FORMAT.format(new Date(event.timestamp))).append("] ");
                sb.append(event.type.toUpperCase()).append(": ");
                
                boolean first = true;
                for (Map.Entry<String, String> detail : event.details.entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(detail.getKey()).append("=").append(detail.getValue());
                    first = false;
                }
                sb.append("\n");
            }
        }

        return ToolResult.success(sb.toString());
    }
}

