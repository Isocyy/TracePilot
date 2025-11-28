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

public class VariablesLocalTool implements ToolHandler {
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("threadId", "The unique ID of the thread", true)
            .addInteger("frameIndex", "Stack frame index (0 = top frame)", false)
            .build();
        return new ToolDefinition("variables_local",
            "Get local variables in a stack frame. Thread MUST be suspended. Object values show @id for use with object_fields.",
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
                    List<LocalVariable> locals = frame.visibleVariables();
                    Map<LocalVariable, Value> values = frame.getValues(locals);
                    
                    StringBuilder sb = new StringBuilder();
                    sb.append("Local variables at frame #").append(frameIndex).append(":\n\n");
                    for (LocalVariable var : locals) {
                        Value val = values.get(var);
                        sb.append(var.typeName()).append(" ").append(var.name())
                          .append(" = ").append(formatValue(val)).append("\n");
                    }
                    if (locals.isEmpty()) {
                        sb.append("(no local variables)\n");
                    }
                    return ToolResult.success(sb.toString());
                } catch (AbsentInformationException e) {
                    return ToolResult.error("Debug info not available. Compile with -g flag.");
                } catch (Exception e) {
                    return ToolResult.error("Failed to get locals: " + e.getMessage());
                }
            }
        }
        return ToolResult.error("Thread not found with ID: " + threadId);
    }

    public static String formatValue(Value val) {
        if (val == null) return "null";
        if (val instanceof StringReference) {
            return "\"" + ((StringReference) val).value() + "\"";
        } else if (val instanceof PrimitiveValue) {
            return val.toString();
        } else if (val instanceof ArrayReference) {
            ArrayReference arr = (ArrayReference) val;
            return arr.type().name() + "[" + arr.length() + "] @" + arr.uniqueID();
        } else if (val instanceof ObjectReference) {
            ObjectReference obj = (ObjectReference) val;
            return obj.type().name() + " @" + obj.uniqueID();
        }
        return val.toString();
    }
}
