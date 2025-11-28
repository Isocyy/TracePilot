package com.nanik.tracepilot.tools.impl;

import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.*;

/**
 * Tool to attach to a running JVM via process ID.
 * 
 * Note: This requires the target JVM to have debugging enabled or
 * the debugger must have appropriate permissions.
 * 
 * Parameters:
 * - pid (required): Process ID of the target JVM
 */
public class DebugAttachPidTool implements ToolHandler {
    
    private static final ToolDefinition DEFINITION = new ToolDefinition(
        "debug_attach_pid",
        "Attach to a running JVM via process ID. May require special permissions.",
        new SchemaBuilder()
            .addString("pid", "Process ID of the target JVM", true)
            .build()
    );
    
    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }
    
    @Override
    public ToolResult execute(McpRequest request) {
        String pid = request.getStringParam("pid");
        if (pid == null || pid.isEmpty()) {
            return ToolResult.error("Missing required parameter: pid");
        }
        
        DebugSession session = DebugSession.getInstance();
        
        if (session.isConnected()) {
            return ToolResult.error("Already connected to a VM. Use debug_disconnect first.");
        }
        
        try {
            session.attachPid(pid);

            // Start event thread to process JDI events (breakpoints, steps, etc.)
            session.startEventThread();

            String status = "Attached to VM via PID.\n" +
                "PID: " + pid + "\n" +
                "VM: " + session.getVm().name() + "\n" +
                "Version: " + session.getVm().version();

            return ToolResult.success(status);
        } catch (Exception e) {
            return ToolResult.error("Failed to attach to VM: " + e.getMessage());
        }
    }
}

