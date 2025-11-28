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

public class VariableInspectTool implements ToolHandler {
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("threadId", "The unique ID of the thread", true)
            .addInteger("frameIndex", "Stack frame index (0 = top frame)", false)
            .addString("variableName", "Name of the variable to inspect", true)
            .build();
        return new ToolDefinition("variable_inspect",
            "Deep inspect a variable by name, showing nested fields and values.",
            schema);
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }
        Long threadId = request.getLongParam("threadId");
        if (threadId == null) return ToolResult.error("threadId is required");
        String varName = request.getStringParam("variableName");
        if (varName == null) return ToolResult.error("variableName is required");
        
        Integer frameIndex = request.getIntParam("frameIndex");
        if (frameIndex == null) frameIndex = 0;
        
        VirtualMachine vm = DebugSession.getInstance().getVm();
        for (ThreadReference thread : vm.allThreads()) {
            if (thread.uniqueID() == threadId) {
                if (!thread.isSuspended()) {
                    return ToolResult.error("Thread is not suspended.");
                }
                try {
                    StackFrame frame = thread.frame(frameIndex);
                    LocalVariable var = frame.visibleVariableByName(varName);
                    if (var == null) {
                        return ToolResult.error("Variable '" + varName + "' not found in frame.");
                    }
                    Value val = frame.getValue(var);
                    return ToolResult.success(inspectValue(varName, var.typeName(), val));
                } catch (AbsentInformationException e) {
                    return ToolResult.error("Debug info not available. Compile with -g flag.");
                } catch (Exception e) {
                    return ToolResult.error("Failed to inspect: " + e.getMessage());
                }
            }
        }
        return ToolResult.error("Thread not found with ID: " + threadId);
    }

    private String inspectValue(String name, String type, Value val) {
        StringBuilder sb = new StringBuilder();
        sb.append("Variable: ").append(name).append("\n");
        sb.append("Type: ").append(type).append("\n");
        sb.append("Value: ").append(VariablesLocalTool.formatValue(val)).append("\n");
        
        if (val instanceof ObjectReference && !(val instanceof StringReference)) {
            ObjectReference obj = (ObjectReference) val;
            sb.append("\nFields:\n");
            ReferenceType refType = obj.referenceType();
            for (Field field : refType.allFields()) {
                Value fieldVal = obj.getValue(field);
                sb.append("  ").append(field.typeName()).append(" ").append(field.name())
                  .append(" = ").append(VariablesLocalTool.formatValue(fieldVal)).append("\n");
            }
        } else if (val instanceof ArrayReference) {
            ArrayReference arr = (ArrayReference) val;
            sb.append("\nElements (first 10):\n");
            List<Value> elements = arr.getValues(0, Math.min(10, arr.length()));
            for (int i = 0; i < elements.size(); i++) {
                sb.append("  [").append(i).append("] = ").append(VariablesLocalTool.formatValue(elements.get(i))).append("\n");
            }
            if (arr.length() > 10) {
                sb.append("  ... (").append(arr.length() - 10).append(" more elements)\n");
            }
        }
        return sb.toString();
    }
}
