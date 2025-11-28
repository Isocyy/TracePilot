package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.*;

import java.util.List;
import java.util.Map;

public class VariablesArgumentsTool implements ToolHandler {
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("threadId", "The unique ID of the thread", true)
            .addInteger("frameIndex", "Stack frame index (0 = top frame)", false)
            .build();
        return new ToolDefinition("variables_arguments",
            "Get method arguments and their values in a stack frame. Shows parameter names, types, and values.",
            schema);
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }
        Long threadId = request.getLongParam("threadId");
        if (threadId == null) return ToolResult.error("threadId is required");
        
        Integer frameIndex = request.getIntParam("frameIndex");
        if (frameIndex == null) frameIndex = 0;
        
        VirtualMachine vm = DebugSession.getInstance().getVm();
        for (ThreadReference thread : vm.allThreads()) {
            if (thread.uniqueID() == threadId) {
                if (!thread.isSuspended()) {
                    return ToolResult.error("Thread is not suspended.");
                }
                try {
                    if (frameIndex >= thread.frameCount()) {
                        return ToolResult.error("Frame index out of range. Max: " + (thread.frameCount() - 1));
                    }
                    StackFrame frame = thread.frame(frameIndex);
                    List<LocalVariable> args = frame.location().method().arguments();
                    Map<LocalVariable, Value> values = frame.getValues(args);
                    
                    StringBuilder sb = new StringBuilder();
                    sb.append("Method arguments at frame #").append(frameIndex).append(":\n");
                    sb.append("Method: ").append(frame.location().method().name()).append("\n\n");
                    for (LocalVariable arg : args) {
                        Value val = values.get(arg);
                        sb.append(arg.typeName()).append(" ").append(arg.name())
                          .append(" = ").append(VariablesLocalTool.formatValue(val)).append("\n");
                    }
                    if (args.isEmpty()) {
                        sb.append("(no arguments)\n");
                    }
                    return ToolResult.success(sb.toString());
                } catch (AbsentInformationException e) {
                    return ToolResult.error("Debug info not available. Compile with -g flag.");
                } catch (Exception e) {
                    return ToolResult.error("Failed to get arguments: " + e.getMessage());
                }
            }
        }
        return ToolResult.error("Thread not found with ID: " + threadId);
    }
}
