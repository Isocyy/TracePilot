package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

/**
 * Tool to suspend execution.
 */
public class SuspendTool implements ToolHandler {
    
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("threadId", "Thread ID to suspend (optional - suspends all if not specified)")
            .build();
        
        return new ToolDefinition(
            "suspend",
            "Suspend execution of the VM or a specific thread.",
            schema
        );
    }
    
    @Override
    public ToolResult execute(McpRequest request) {
        VirtualMachine vm = DebugSession.getInstance().getVm();
        if (vm == null) {
            return ToolResult.error("Not connected to a VM.");
        }
        
        String threadId = request.getStringParam("threadId");
        
        try {
            if (threadId != null && !threadId.isEmpty()) {
                // Suspend specific thread
                ThreadReference thread = findThread(vm, threadId);
                if (thread == null) {
                    return ToolResult.error("Thread not found: " + threadId);
                }
                thread.suspend();
                return ToolResult.success("Thread " + threadId + " (" + thread.name() + ") suspended.");
            } else {
                // Suspend all
                vm.suspend();
                return ToolResult.success("All threads suspended.");
            }
        } catch (Exception e) {
            return ToolResult.error("Failed to suspend: " + e.getMessage());
        }
    }
    
    private ThreadReference findThread(VirtualMachine vm, String threadId) {
        try {
            long id = Long.parseLong(threadId);
            for (ThreadReference thread : vm.allThreads()) {
                if (thread.uniqueID() == id) {
                    return thread;
                }
            }
        } catch (NumberFormatException e) {
            // Try by name
            for (ThreadReference thread : vm.allThreads()) {
                if (thread.name().equals(threadId)) {
                    return thread;
                }
            }
        }
        return null;
    }
}

