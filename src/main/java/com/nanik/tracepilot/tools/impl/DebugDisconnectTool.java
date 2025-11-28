package com.nanik.tracepilot.tools.impl;

import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.*;

/**
 * Tool to disconnect from the current debug session.
 * 
 * This will:
 * - For launched VMs: Terminate the process
 * - For attached VMs: Detach without terminating
 */
public class DebugDisconnectTool implements ToolHandler {
    
    private static final ToolDefinition DEFINITION = ToolDefinition.noParams(
        "debug_disconnect",
        "Disconnect from the current debug session. For launched VMs, this terminates the process."
    );
    
    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }
    
    @Override
    public ToolResult execute(McpRequest request) {
        DebugSession session = DebugSession.getInstance();
        
        if (!session.isConnected()) {
            return ToolResult.error("Not connected to any VM.");
        }
        
        String connectionDetails = session.getConnectionDetails();
        DebugSession.ConnectionType connectionType = session.getConnectionType();
        
        session.disconnect();
        
        String message = "Disconnected from VM.\n" +
            "Previous connection: " + connectionDetails + "\n" +
            "Type: " + connectionType;
        
        return ToolResult.success(message);
    }
}

