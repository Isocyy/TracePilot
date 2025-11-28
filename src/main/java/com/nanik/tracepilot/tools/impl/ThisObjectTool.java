package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.*;

public class ThisObjectTool implements ToolHandler {
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("threadId", "The unique ID of the thread", true)
            .addInteger("frameIndex", "Stack frame index (0 = top frame)", false)
            .build();
        return new ToolDefinition("this_object",
            "Get the 'this' object reference for a stack frame. Returns null for static methods.",
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
                    ObjectReference thisObj = frame.thisObject();
                    
                    if (thisObj == null) {
                        return ToolResult.success("No 'this' reference (static method or native frame).");
                    }
                    
                    StringBuilder sb = new StringBuilder();
                    sb.append("'this' object:\n");
                    sb.append("Type: ").append(thisObj.type().name()).append("\n");
                    sb.append("ID: ").append(thisObj.uniqueID()).append("\n\n");
                    sb.append("Fields:\n");
                    
                    ReferenceType refType = thisObj.referenceType();
                    for (Field field : refType.allFields()) {
                        if (!field.isStatic()) {
                            Value val = thisObj.getValue(field);
                            sb.append("  ").append(field.typeName()).append(" ").append(field.name())
                              .append(" = ").append(VariablesLocalTool.formatValue(val)).append("\n");
                        }
                    }
                    return ToolResult.success(sb.toString());
                } catch (IncompatibleThreadStateException e) {
                    return ToolResult.error("Thread is not suspended: " + e.getMessage());
                } catch (Exception e) {
                    return ToolResult.error("Failed to get 'this': " + e.getMessage());
                }
            }
        }
        return ToolResult.error("Thread not found with ID: " + threadId);
    }
}
