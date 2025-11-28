package com.nanik.tracepilot.tools.impl;

import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.*;

/**
 * Tool to launch a new JVM with debugging enabled.
 * 
 * Parameters:
 * - mainClass (required): The main class to run
 * - classpath (optional): Classpath for the JVM
 * - options (optional): Additional JVM options
 * - suspend (optional): Whether to suspend on start (default: true)
 */
public class DebugLaunchTool implements ToolHandler {
    
    private static final ToolDefinition DEFINITION = new ToolDefinition(
        "debug_launch",
        "Launch a new JVM with debugging enabled. The debugger will connect automatically.",
        new SchemaBuilder()
            .addString("mainClass", "Fully qualified main class name (e.g., com.example.Main)", true)
            .addString("classpath", "Classpath for the JVM (directories or JARs separated by :)", false)
            .addString("options", "Additional JVM options (e.g., -Xmx512m)", false)
            .addBoolean("suspend", "Suspend VM on start to allow setting breakpoints (default: true)", false, true)
            .build()
    );
    
    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }
    
    @Override
    public ToolResult execute(McpRequest request) {
        String mainClass = request.getStringParam("mainClass");
        if (mainClass == null || mainClass.isEmpty()) {
            return ToolResult.error("Missing required parameter: mainClass");
        }
        
        String classpath = request.getStringParam("classpath");
        String options = request.getStringParam("options");
        Boolean suspend = request.getBoolParam("suspend");
        if (suspend == null) {
            suspend = true;
        }
        
        DebugSession session = DebugSession.getInstance();
        
        if (session.isConnected()) {
            return ToolResult.error("Already connected to a VM. Use debug_disconnect first.");
        }
        
        try {
            session.launch(mainClass, classpath, options, suspend);

            // Start event thread to process JDI events (breakpoints, steps, etc.)
            session.startEventThread();

            String status = "Launched VM successfully.\n" +
                "Main class: " + mainClass + "\n" +
                "Suspended: " + suspend + "\n" +
                "VM: " + session.getVm().name() + "\n" +
                "Version: " + session.getVm().version();

            return ToolResult.success(status);
        } catch (Exception e) {
            return ToolResult.error("Failed to launch VM: " + e.getMessage());
        }
    }
}

