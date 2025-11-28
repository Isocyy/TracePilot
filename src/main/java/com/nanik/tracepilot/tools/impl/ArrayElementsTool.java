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

public class ArrayElementsTool implements ToolHandler {
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("arrayId", "The unique ID of the array", true)
            .addInteger("startIndex", "Start index (default: 0)", false)
            .addInteger("count", "Number of elements to return (default: 20)", false)
            .build();
        return new ToolDefinition("array_elements",
            "Get elements of an array by its ID. Use arrayId from variables_local output. Supports pagination.",
            schema);
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }
        Long arrayId = request.getLongParam("arrayId");
        if (arrayId == null) return ToolResult.error("arrayId is required");
        
        Integer startIndex = request.getIntParam("startIndex");
        if (startIndex == null) startIndex = 0;
        Integer count = request.getIntParam("count");
        if (count == null) count = 20;
        
        VirtualMachine vm = DebugSession.getInstance().getVm();
        
        // Find array by ID
        ArrayReference arr = findArrayById(vm, arrayId);
        if (arr == null) {
            return ToolResult.error("Array not found with ID: " + arrayId);
        }
        
        int length = arr.length();
        if (startIndex >= length) {
            return ToolResult.error("Start index " + startIndex + " out of range. Array length: " + length);
        }
        
        int endIndex = Math.min(startIndex + count, length);
        List<Value> elements = arr.getValues(startIndex, endIndex - startIndex);
        
        StringBuilder sb = new StringBuilder();
        sb.append("Array: ").append(arr.type().name()).append(" @").append(arrayId).append("\n");
        sb.append("Length: ").append(length).append("\n");
        sb.append("Showing elements [").append(startIndex).append("-").append(endIndex - 1).append("]:\n\n");
        
        for (int i = 0; i < elements.size(); i++) {
            sb.append("[").append(startIndex + i).append("] = ")
              .append(VariablesLocalTool.formatValue(elements.get(i))).append("\n");
        }
        
        if (endIndex < length) {
            sb.append("\n... (").append(length - endIndex).append(" more elements)\n");
        }
        return ToolResult.success(sb.toString());
    }

    private ArrayReference findArrayById(VirtualMachine vm, long arrayId) {
        for (ThreadReference thread : vm.allThreads()) {
            if (thread.isSuspended()) {
                try {
                    for (StackFrame frame : thread.frames()) {
                        ObjectReference thisObj = frame.thisObject();
                        if (thisObj instanceof ArrayReference && thisObj.uniqueID() == arrayId) {
                            return (ArrayReference) thisObj;
                        }
                        try {
                            for (LocalVariable var : frame.visibleVariables()) {
                                Value val = frame.getValue(var);
                                if (val instanceof ArrayReference) {
                                    ArrayReference arrRef = (ArrayReference) val;
                                    if (arrRef.uniqueID() == arrayId) {
                                        return arrRef;
                                    }
                                }
                            }
                        } catch (AbsentInformationException e) {
                            // Skip
                        }
                    }
                } catch (IncompatibleThreadStateException e) {
                    // Skip
                }
            }
        }
        return null;
    }
}
