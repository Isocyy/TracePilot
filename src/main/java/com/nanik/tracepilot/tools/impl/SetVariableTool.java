package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.*;

/**
 * Tool to set/modify the value of a local variable in a stack frame.
 */
public class SetVariableTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("threadId", "The unique ID of the thread", true)
            .addInteger("frameIndex", "Stack frame index (0 = top frame)", false)
            .addString("variableName", "Name of the variable to set", true)
            .addString("value", "New value (e.g., '42', '\"hello\"', 'true', 'null', or objectId for references)", true)
            .build();

        return new ToolDefinition(
            "set_variable",
            "Set the value of a local variable in a stack frame. Supports primitives, strings, null, and object references by ID.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }

        Long threadId = request.getLongParam("threadId");
        if (threadId == null) return ToolResult.error("threadId is required");

        String varName = request.getStringParam("variableName");
        if (varName == null || varName.isEmpty()) return ToolResult.error("variableName is required");

        String valueStr = request.getStringParam("value");
        if (valueStr == null) return ToolResult.error("value is required");

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
                    LocalVariable var = frame.visibleVariableByName(varName);
                    if (var == null) {
                        return ToolResult.error("Variable '" + varName + "' not found in frame.");
                    }

                    Value oldValue = frame.getValue(var);
                    Value newValue = parseValue(vm, var.type(), valueStr);
                    frame.setValue(var, newValue);

                    StringBuilder sb = new StringBuilder();
                    sb.append("Variable '").append(varName).append("' updated.\n");
                    sb.append("Type: ").append(var.typeName()).append("\n");
                    sb.append("Old value: ").append(VariablesLocalTool.formatValue(oldValue)).append("\n");
                    sb.append("New value: ").append(VariablesLocalTool.formatValue(newValue));

                    return ToolResult.success(sb.toString());

                } catch (AbsentInformationException e) {
                    return ToolResult.error("Debug info not available. Compile with -g flag.");
                } catch (InvalidTypeException e) {
                    return ToolResult.error("Type mismatch: " + e.getMessage());
                } catch (ClassNotLoadedException e) {
                    return ToolResult.error("Class not loaded: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    return ToolResult.error(e.getMessage());
                } catch (Exception e) {
                    return ToolResult.error("Failed to set variable: " + e.getMessage());
                }
            }
        }
        return ToolResult.error("Thread not found with ID: " + threadId);
    }

    /**
     * Parse a string value into a JDI Value based on the target type.
     */
    private Value parseValue(VirtualMachine vm, Type targetType, String valueStr) 
            throws ClassNotLoadedException {
        
        String trimmed = valueStr.trim();
        
        // Handle null
        if (trimmed.equals("null")) {
            if (targetType instanceof PrimitiveType) {
                throw new IllegalArgumentException("Cannot assign null to primitive type " + targetType.name());
            }
            return null;
        }

        String typeName = targetType.name();

        // Handle primitives
        if (targetType instanceof PrimitiveType) {
            return parsePrimitive(vm, typeName, trimmed);
        }

        // Handle String
        if (typeName.equals("java.lang.String")) {
            return parseString(vm, trimmed);
        }

        // Handle object reference by ID (format: @123 or just 123)
        return parseObjectReference(vm, trimmed);
    }

    private Value parsePrimitive(VirtualMachine vm, String typeName, String valueStr) {
        try {
            switch (typeName) {
                case "int":
                    return vm.mirrorOf(Integer.parseInt(valueStr));
                case "long":
                    String longStr = valueStr.endsWith("L") || valueStr.endsWith("l") 
                        ? valueStr.substring(0, valueStr.length() - 1) : valueStr;
                    return vm.mirrorOf(Long.parseLong(longStr));
                case "short":
                    return vm.mirrorOf(Short.parseShort(valueStr));
                case "byte":
                    return vm.mirrorOf(Byte.parseByte(valueStr));
                case "float":
                    String floatStr = valueStr.endsWith("f") || valueStr.endsWith("F")
                        ? valueStr.substring(0, valueStr.length() - 1) : valueStr;
                    return vm.mirrorOf(Float.parseFloat(floatStr));
                case "double":
                    String doubleStr = valueStr.endsWith("d") || valueStr.endsWith("D")
                        ? valueStr.substring(0, valueStr.length() - 1) : valueStr;
                    return vm.mirrorOf(Double.parseDouble(doubleStr));
                case "boolean":
                    return vm.mirrorOf(Boolean.parseBoolean(valueStr));
                case "char":
                    if (valueStr.length() == 1) {
                        return vm.mirrorOf(valueStr.charAt(0));
                    } else if (valueStr.length() >= 2 && valueStr.startsWith("'") && valueStr.endsWith("'")) {
                        String inner = valueStr.substring(1, valueStr.length() - 1);
                        if (inner.length() == 1) {
                            return vm.mirrorOf(inner.charAt(0));
                        } else if (inner.startsWith("\\")) {
                            return vm.mirrorOf(parseEscapeChar(inner));
                        }
                    }
                    throw new IllegalArgumentException("Invalid char value: " + valueStr);
                default:
                    throw new IllegalArgumentException("Unknown primitive type: " + typeName);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot parse '" + valueStr + "' as " + typeName);
        }
    }

    private char parseEscapeChar(String escape) {
        switch (escape) {
            case "\\n": return '\n';
            case "\\t": return '\t';
            case "\\r": return '\r';
            case "\\\\": return '\\';
            case "\\'": return '\'';
            case "\\\"": return '"';
            case "\\0": return '\0';
            default:
                throw new IllegalArgumentException("Unknown escape sequence: " + escape);
        }
    }

    private Value parseString(VirtualMachine vm, String valueStr) {
        // Handle quoted strings
        if (valueStr.startsWith("\"") && valueStr.endsWith("\"") && valueStr.length() >= 2) {
            String unquoted = valueStr.substring(1, valueStr.length() - 1);
            return vm.mirrorOf(unquoted);
        }
        // Accept unquoted strings too
        return vm.mirrorOf(valueStr);
    }

    private ObjectReference parseObjectReference(VirtualMachine vm, String valueStr) {
        // Accept format: @123 or just 123
        String idStr = valueStr.startsWith("@") ? valueStr.substring(1) : valueStr;
        
        try {
            long objectId = Long.parseLong(idStr);
            ObjectReference obj = findObjectById(vm, objectId);
            if (obj == null) {
                throw new IllegalArgumentException("Object not found with ID: " + objectId);
            }
            return obj;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "For object types, provide an object ID (e.g., @123 or 123). Got: " + valueStr);
        }
    }

    private ObjectReference findObjectById(VirtualMachine vm, long objectId) {
        for (ThreadReference thread : vm.allThreads()) {
            if (thread.isSuspended()) {
                try {
                    for (StackFrame frame : thread.frames()) {
                        ObjectReference thisObj = frame.thisObject();
                        if (thisObj != null && thisObj.uniqueID() == objectId) {
                            return thisObj;
                        }
                        try {
                            for (LocalVariable var : frame.visibleVariables()) {
                                Value val = frame.getValue(var);
                                if (val instanceof ObjectReference) {
                                    ObjectReference objRef = (ObjectReference) val;
                                    if (objRef.uniqueID() == objectId) {
                                        return objRef;
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

