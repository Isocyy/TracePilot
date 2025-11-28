package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.*;

public class ObjectFieldsTool implements ToolHandler {
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("objectId", "The unique ID of the object", true)
            .addBoolean("includeStatic", "Include static fields (default: false)", false, false)
            .build();
        return new ToolDefinition("object_fields",
            "Get object fields. Thread MUST be suspended. Get objectId from variables_local (shows as @id). Nested objects have their own @id.",
            schema);
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }
        Long objectId = request.getLongParam("objectId");
        if (objectId == null) return ToolResult.error("objectId is required");
        
        Boolean includeStatic = request.getBoolParam("includeStatic");
        if (includeStatic == null) includeStatic = false;
        
        VirtualMachine vm = DebugSession.getInstance().getVm();
        
        // Find object by ID - need to search through all threads' frames
        ObjectReference obj = findObjectById(vm, objectId);
        if (obj == null) {
            return ToolResult.error("Object not found with ID: " + objectId);
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Object: ").append(obj.type().name()).append(" @").append(objectId).append("\n\n");
        sb.append("Instance Fields:\n");
        
        ReferenceType refType = obj.referenceType();
        boolean hasFields = false;
        for (Field field : refType.allFields()) {
            if (!field.isStatic() || includeStatic) {
                Value val = field.isStatic() ? refType.getValue(field) : obj.getValue(field);
                String prefix = field.isStatic() ? "[static] " : "";
                sb.append("  ").append(prefix).append(field.typeName()).append(" ").append(field.name())
                  .append(" = ").append(VariablesLocalTool.formatValue(val)).append("\n");
                hasFields = true;
            }
        }
        if (!hasFields) {
            sb.append("  (no fields)\n");
        }
        return ToolResult.success(sb.toString());
    }

    private ObjectReference findObjectById(VirtualMachine vm, long objectId) {
        // Search through all threads' stack frames for the object
        for (ThreadReference thread : vm.allThreads()) {
            if (thread.isSuspended()) {
                try {
                    for (StackFrame frame : thread.frames()) {
                        // Check this object
                        ObjectReference thisObj = frame.thisObject();
                        if (thisObj != null && thisObj.uniqueID() == objectId) {
                            return thisObj;
                        }
                        // Check local variables
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
                            // Skip frames without debug info
                        }
                    }
                } catch (IncompatibleThreadStateException e) {
                    // Skip non-suspended threads
                }
            }
        }
        return null;
    }
}
