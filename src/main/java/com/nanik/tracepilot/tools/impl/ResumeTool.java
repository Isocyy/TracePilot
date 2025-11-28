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
 * Tool to resume execution.
 */
public class ResumeTool implements ToolHandler {
    
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("threadId", "Thread ID to resume (optional - resumes all if not specified)")
            .build();
        
        return new ToolDefinition(
            "resume",
            "Resume execution of the VM. IMPORTANT: Call wait_for_stop after this to know when VM stops again.",
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

        String threadId = request.getStringParam("threadId");

        try {
            if (threadId != null && !threadId.isEmpty()) {
                // Resume specific thread
                ThreadReference thread = findThread(vm, threadId);
                if (thread == null) {
                    return ToolResult.error("Thread not found: " + threadId);
                }
                thread.resume();
                return ToolResult.success("Thread " + threadId + " (" + thread.name() + ") resumed.\n" +
                    "Use debug_status or wait_for_stop to check when VM stops again.");
            } else {
                // Clear stop reason before resuming all
                session.clearStopReason();
                vm.resume();
                return ToolResult.success("All threads resumed.\n" +
                    "Use debug_status or wait_for_stop to check when VM stops again.");
            }
        } catch (Exception e) {
            return ToolResult.error("Failed to resume: " + e.getMessage());
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

