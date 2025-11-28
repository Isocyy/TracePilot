package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;

public class ThreadSuspendTool implements ToolHandler {
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("threadId", "The unique ID of the thread to suspend", true)
            .build();
        return new ToolDefinition("thread_suspend", "Suspend a specific thread by its ID", schema);
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }
        Long threadId = request.getLongParam("threadId");
        if (threadId == null) return ToolResult.error("threadId is required");

        VirtualMachine vm = DebugSession.getInstance().getVm();
        for (ThreadReference thread : vm.allThreads()) {
            if (thread.uniqueID() == threadId) {
                if (thread.isSuspended()) {
                    return ToolResult.success("Thread '" + thread.name() + "' is already suspended.");
                }
                thread.suspend();
                return ToolResult.success("Thread suspended.\nName: " + thread.name() +
                    "\nID: " + threadId + "\nSuspend Count: " + thread.suspendCount());
            }
        }
        return ToolResult.error("Thread not found with ID: " + threadId);
    }
}

