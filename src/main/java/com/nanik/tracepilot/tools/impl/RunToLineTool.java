package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.BreakpointManager;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.debug.StopReason;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.Location;
import com.sun.jdi.VirtualMachine;

/**
 * Tool to run until a specific line is hit (temporary breakpoint).
 * 
 * This combines: set breakpoint + resume + wait_for_stop + remove breakpoint
 * into a single convenient operation.
 */
public class RunToLineTool implements ToolHandler {
    
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 300;
    
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("className", "Fully qualified class name (e.g., com.example.Main)", true)
            .addInteger("lineNumber", "Line number to run to", true)
            .addInteger("timeout", "Timeout in seconds (default: 30, max: 300)", false, DEFAULT_TIMEOUT_SECONDS)
            .build();
        
        return new ToolDefinition(
            "run_to_line",
            "Run until hitting a specific line. Sets a temporary breakpoint, resumes, waits for hit, then removes the breakpoint.",
            schema
        );
    }
    
    @Override
    public ToolResult execute(McpRequest request) {
        DebugSession session = DebugSession.getInstance();
        VirtualMachine vm = session.getVm();
        
        if (vm == null) {
            return ToolResult.error("Not connected to a VM.");
        }
        
        String className = request.getStringParam("className");
        Integer lineNumber = request.getIntParam("lineNumber");
        Integer timeout = request.getIntParam("timeout");
        
        if (className == null || className.isEmpty()) {
            return ToolResult.error("className is required");
        }
        if (lineNumber == null) {
            return ToolResult.error("lineNumber is required");
        }
        if (timeout == null) {
            timeout = DEFAULT_TIMEOUT_SECONDS;
        }
        timeout = Math.min(Math.max(1, timeout), MAX_TIMEOUT_SECONDS);
        
        BreakpointManager bpManager = BreakpointManager.getInstance();
        String breakpointId = null;
        
        try {
            // Step 1: Set temporary breakpoint
            breakpointId = bpManager.setLineBreakpoint(className, lineNumber);
            boolean isPending = bpManager.getBreakpoint(breakpointId).isPending();
            
            // Step 2: Clear stop reason and resume
            session.clearStopReason();
            vm.resume();
            
            // Step 3: Wait for stop
            StopReason stopReason = session.waitForStop(timeout * 1000L);
            
            // Step 4: Remove temporary breakpoint (always, even on error)
            boolean removed = bpManager.removeBreakpoint(breakpointId);
            
            // Step 5: Build response
            StringBuilder sb = new StringBuilder();
            
            if (stopReason.isStopped()) {
                Location loc = stopReason.getLocation();
                
                // Check if we hit our target breakpoint
                boolean hitTarget = false;
                if (loc != null) {
                    hitTarget = loc.declaringType().name().equals(className) 
                             && loc.lineNumber() == lineNumber;
                }
                
                if (hitTarget) {
                    sb.append("=== Run to Line Complete ===\n");
                    sb.append("Stopped at target: ").append(className).append(":").append(lineNumber).append("\n");
                } else {
                    sb.append("=== Stopped Before Target ===\n");
                    sb.append("reason: ").append(stopReason.getType().name()).append("\n");
                    if (loc != null) {
                        sb.append("location: ").append(loc.declaringType().name())
                          .append(":").append(loc.lineNumber()).append("\n");
                    }
                    sb.append("\nNote: Did not reach target ").append(className).append(":").append(lineNumber).append("\n");
                }
                
                sb.append("\n=== Stop Details ===\n");
                sb.append("type: ").append(stopReason.getType().name()).append("\n");
                
                if (stopReason.getThreadName() != null) {
                    sb.append("thread: ").append(stopReason.getThreadName())
                      .append(" (id: ").append(stopReason.getThreadId()).append(")\n");
                }
                
                if (loc != null) {
                    sb.append("class: ").append(loc.declaringType().name()).append("\n");
                    sb.append("method: ").append(loc.method().name()).append("\n");
                    sb.append("line: ").append(loc.lineNumber()).append("\n");
                }
                
                sb.append("\ntemporary_breakpoint_removed: ").append(removed).append("\n");
                
                sb.append("\n=== Next Steps ===\n");
                sb.append("Use variables_local, stack_frames to inspect, or step_*/run_to_line to continue.\n");
                
                return ToolResult.success(sb.toString());
            } else {
                // Timeout or VM disconnect
                if (stopReason.getType() == StopReason.Type.VM_DISCONNECT) {
                    return ToolResult.error("VM disconnected while running to line. Breakpoint was " + 
                        (removed ? "removed" : "not removed") + ".");
                }
                
                sb.append("=== Timeout ===\n");
                sb.append("target: ").append(className).append(":").append(lineNumber).append("\n");
                sb.append("timeout_seconds: ").append(timeout).append("\n");
                sb.append("temporary_breakpoint_removed: ").append(removed).append("\n");
                sb.append("\nThe VM is still running. The target line was not reached within the timeout.\n");
                sb.append("You can call suspend() to pause, or try again with a longer timeout.\n");
                
                return ToolResult.success(sb.toString());
            }
            
        } catch (Exception e) {
            // Clean up breakpoint on error
            if (breakpointId != null) {
                try {
                    bpManager.removeBreakpoint(breakpointId);
                } catch (Exception ignored) {
                }
            }
            return ToolResult.error("Failed to run to line: " + e.getMessage());
        }
    }
}

