package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.debug.StopReason;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.*;
import com.sun.jdi.Location;

import java.util.Map;

/**
 * Tool to wait for the VM to stop (hit breakpoint, complete step, etc.).
 * 
 * This is crucial for LLM debugging - instead of polling debug_status repeatedly,
 * the LLM can call this tool to block until something interesting happens.
 */
public class WaitForStopTool implements ToolHandler {
    
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 300; // 5 minutes max
    
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("timeout", "Timeout in seconds to wait for VM to stop (default: 30, max: 300)")
            .build();
        
        return new ToolDefinition(
            "wait_for_stop",
            "Wait for the VM to stop (breakpoint, step, exception). Returns immediately if already stopped. " +
            "Use after resume or step operations.",
            schema
        );
    }
    
    @Override
    public ToolResult execute(McpRequest request) {
        DebugSession session = DebugSession.getInstance();
        
        if (!session.isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }
        
        // Get timeout parameter
        int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        Integer timeoutParam = request.getIntParam("timeout");
        if (timeoutParam != null) {
            timeoutSeconds = Math.min(Math.max(1, timeoutParam), MAX_TIMEOUT_SECONDS);
        }
        
        long startTime = System.currentTimeMillis();
        
        // Wait for stop
        StopReason stopReason = session.waitForStop(timeoutSeconds * 1000L);
        
        long waitedMs = System.currentTimeMillis() - startTime;
        
        StringBuilder sb = new StringBuilder();
        
        if (stopReason.isStopped()) {
            // VM is stopped
            sb.append("stopped: true\n");
            sb.append("waited_ms: ").append(waitedMs).append("\n\n");
            
            sb.append("=== Stop Reason ===\n");
            sb.append("type: ").append(stopReason.getType().name()).append("\n");
            
            if (stopReason.getThreadName() != null) {
                sb.append("thread: ").append(stopReason.getThreadName());
                sb.append(" (id: ").append(stopReason.getThreadId()).append(")\n");
            }
            
            Location loc = stopReason.getLocation();
            if (loc != null) {
                sb.append("\nlocation:\n");
                sb.append("  class: ").append(loc.declaringType().name()).append("\n");
                sb.append("  method: ").append(loc.method().name()).append("\n");
                sb.append("  line: ").append(loc.lineNumber()).append("\n");
                try {
                    String sourcePath = loc.sourcePath();
                    sb.append("  source: ").append(sourcePath).append("\n");
                } catch (Exception e) {
                    // No source info available
                }
            }
            
            Map<String, String> details = stopReason.getDetails();
            if (details != null && !details.isEmpty()) {
                sb.append("\ndetails:\n");
                for (Map.Entry<String, String> entry : details.entrySet()) {
                    sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }
            
            // Add hint for next steps
            sb.append("\n=== Next Steps ===\n");
            switch (stopReason.getType()) {
                case BREAKPOINT_HIT:
                    sb.append("Use variables_local, stack_frames, or step_* to inspect/continue.\n");
                    break;
                case STEP_COMPLETE:
                    sb.append("Use variables_local to see current state, or step_* to continue stepping.\n");
                    break;
                case EXCEPTION_THROWN:
                    sb.append("Use exception_info for details, stack_frames for context.\n");
                    break;
                case WATCHPOINT_ACCESS:
                case WATCHPOINT_MODIFY:
                    sb.append("Field access detected. Use variables_local or object_fields to inspect.\n");
                    break;
                default:
                    sb.append("Use debug_status for more info, resume() to continue.\n");
            }
            
            return ToolResult.success(sb.toString());
        } else {
            // Timeout or still running
            if (stopReason.getType() == StopReason.Type.VM_DISCONNECT) {
                return ToolResult.error("VM disconnected while waiting.");
            }
            
            sb.append("stopped: false\n");
            sb.append("state: RUNNING\n");
            sb.append("waited_ms: ").append(waitedMs).append("\n");
            sb.append("timeout_seconds: ").append(timeoutSeconds).append("\n\n");
            sb.append("Timeout waiting for VM to stop.\n");
            sb.append("The VM is still running. You can:\n");
            sb.append("  - Call wait_for_stop again with a longer timeout\n");
            sb.append("  - Call suspend() to manually pause the VM\n");
            sb.append("  - Check if breakpoints are set correctly with breakpoint_list\n");
            
            return ToolResult.success(sb.toString());
        }
    }
}

