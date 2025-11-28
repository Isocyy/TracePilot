package com.nanik.tracepilot.tools.impl;

import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.*;

/**
 * Tool to attach to a running JVM via socket.
 * 
 * The target JVM must be started with:
 * -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=<port>
 * 
 * Parameters:
 * - host (optional): Hostname to connect to (default: localhost)
 * - port (required): Port number to connect to
 */
public class DebugAttachSocketTool implements ToolHandler {
    
    private static final ToolDefinition DEFINITION = new ToolDefinition(
        "debug_attach_socket",
        "Attach to a JVM. Target must start with: java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005. Connect first before any other debug operations.",
        new SchemaBuilder()
            .addString("host", "Hostname to connect to (default: localhost)", false, "localhost")
            .addInteger("port", "Port number to connect to", true)
            .build()
    );
    
    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }
    
    @Override
    public ToolResult execute(McpRequest request) {
        String host = request.getStringParam("host");
        if (host == null || host.isEmpty()) {
            host = "localhost";
        }
        
        Integer port = request.getIntParam("port");
        if (port == null) {
            return ToolResult.error("Missing required parameter: port");
        }
        
        DebugSession session = DebugSession.getInstance();
        
        if (session.isConnected()) {
            return ToolResult.error("Already connected to a VM. Use debug_disconnect first.");
        }
        
        try {
            session.attachSocket(host, port);

            // Start event thread to process JDI events (breakpoints, steps, etc.)
            session.startEventThread();

            String status = "Attached to VM via socket.\n" +
                "Host: " + host + "\n" +
                "Port: " + port + "\n" +
                "VM: " + session.getVm().name() + "\n" +
                "Version: " + session.getVm().version();

            return ToolResult.success(status);
        } catch (Exception e) {
            return ToolResult.error("Failed to attach to VM: " + e.getMessage());
        }
    }
}

