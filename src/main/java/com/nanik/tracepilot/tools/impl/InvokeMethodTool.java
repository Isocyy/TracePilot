package com.nanik.tracepilot.tools.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool to invoke an instance method on an object.
 * The thread must be suspended at a breakpoint.
 */
public class InvokeMethodTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("threadId", "Thread ID (must be suspended)", true)
            .addInteger("objectId", "Object ID to invoke method on", true)
            .addString("methodName", "Name of the method to invoke", true)
            .addString("methodSignature", "JVM signature to disambiguate overloads (e.g., '(II)I')", false)
            .addString("args", "JSON array of arguments (e.g., '[1, \"hello\", true]')", false)
            .build();

        return new ToolDefinition(
            "invoke_method",
            "Invoke instance method on object. Thread MUST be suspended. Get objectId from variables_local. Args as JSON array: '[1, \"str\"]'.",
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

        Long objectId = request.getLongParam("objectId");
        if (objectId == null) return ToolResult.error("objectId is required");

        String methodName = request.getStringParam("methodName");
        if (methodName == null || methodName.isEmpty()) return ToolResult.error("methodName is required");

        String methodSignature = request.getStringParam("methodSignature");
        String argsJson = request.getStringParam("args");

        VirtualMachine vm = DebugSession.getInstance().getVm();

        // Find thread
        ThreadReference thread = null;
        for (ThreadReference t : vm.allThreads()) {
            if (t.uniqueID() == threadId) {
                thread = t;
                break;
            }
        }
        if (thread == null) {
            return ToolResult.error("Thread not found with ID: " + threadId);
        }
        if (!thread.isSuspended()) {
            return ToolResult.error("Thread is not suspended. Method invocation requires a suspended thread.");
        }

        // Find object
        ObjectReference obj = findObjectById(vm, objectId);
        if (obj == null) {
            return ToolResult.error("Object not found with ID: " + objectId);
        }

        try {
            // Find method
            ReferenceType refType = obj.referenceType();
            List<Method> methods = refType.methodsByName(methodName);
            
            if (methods.isEmpty()) {
                return ToolResult.error("Method '" + methodName + "' not found in " + refType.name());
            }

            Method method = selectMethod(methods, methodSignature);
            if (method == null) {
                StringBuilder sb = new StringBuilder();
                sb.append("Cannot select method. Available signatures:\n");
                for (Method m : methods) {
                    sb.append("  ").append(m.name()).append(m.signature()).append("\n");
                }
                return ToolResult.error(sb.toString());
            }

            // Parse arguments
            List<Value> args = parseArguments(vm, method, argsJson);

            // Invoke the method
            Value result = obj.invokeMethod(thread, method, args, ObjectReference.INVOKE_SINGLE_THREADED);

            StringBuilder sb = new StringBuilder();
            sb.append("Method invoked successfully.\n");
            sb.append("Method: ").append(refType.name()).append(".").append(method.name())
              .append(method.signature()).append("\n");
            sb.append("Return type: ").append(method.returnTypeName()).append("\n");
            
            if (method.returnTypeName().equals("void")) {
                sb.append("Return value: (void)");
            } else {
                sb.append("Return value: ").append(VariablesLocalTool.formatValue(result));
            }

            return ToolResult.success(sb.toString());

        } catch (InvocationException e) {
            ObjectReference exception = e.exception();
            return ToolResult.error("Method threw exception: " + exception.type().name() + 
                " @" + exception.uniqueID());
        } catch (InvalidTypeException e) {
            return ToolResult.error("Invalid argument type: " + e.getMessage());
        } catch (ClassNotLoadedException e) {
            return ToolResult.error("Class not loaded: " + e.getMessage());
        } catch (IncompatibleThreadStateException e) {
            return ToolResult.error("Thread in incompatible state: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to invoke method: " + e.getMessage());
        }
    }

    private Method selectMethod(List<Method> methods, String signature) {
        if (methods.size() == 1) {
            return methods.get(0);
        }
        if (signature != null && !signature.isEmpty()) {
            for (Method m : methods) {
                if (m.signature().equals(signature)) {
                    return m;
                }
            }
        }
        // Ambiguous - return null to indicate user needs to specify signature
        return null;
    }

    private List<Value> parseArguments(VirtualMachine vm, Method method, String argsJson) 
            throws ClassNotLoadedException {
        List<Value> result = new ArrayList<>();
        
        if (argsJson == null || argsJson.trim().isEmpty()) {
            return result;
        }

        JsonArray argsArray;
        try {
            Gson gson = new Gson();
            argsArray = gson.fromJson(argsJson, JsonArray.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON array for args: " + e.getMessage());
        }

        List<Type> paramTypes = method.argumentTypes();
        if (argsArray.size() != paramTypes.size()) {
            throw new IllegalArgumentException("Expected " + paramTypes.size() + 
                " arguments but got " + argsArray.size());
        }

        for (int i = 0; i < argsArray.size(); i++) {
            JsonElement elem = argsArray.get(i);
            Type paramType = paramTypes.get(i);
            result.add(jsonToValue(vm, paramType, elem));
        }

        return result;
    }

    private Value jsonToValue(VirtualMachine vm, Type targetType, JsonElement elem) {
        if (elem.isJsonNull()) {
            if (targetType instanceof PrimitiveType) {
                throw new IllegalArgumentException("Cannot assign null to primitive type");
            }
            return null;
        }

        String typeName = targetType.name();

        if (elem.isJsonPrimitive()) {
            JsonPrimitive prim = elem.getAsJsonPrimitive();

            if (prim.isBoolean()) {
                return vm.mirrorOf(prim.getAsBoolean());
            } else if (prim.isNumber()) {
                switch (typeName) {
                    case "int": return vm.mirrorOf(prim.getAsInt());
                    case "long": return vm.mirrorOf(prim.getAsLong());
                    case "short": return vm.mirrorOf(prim.getAsShort());
                    case "byte": return vm.mirrorOf(prim.getAsByte());
                    case "float": return vm.mirrorOf(prim.getAsFloat());
                    case "double": return vm.mirrorOf(prim.getAsDouble());
                    case "char": return vm.mirrorOf((char) prim.getAsInt());
                    default:
                        // Try as generic number - assume it's a boxed type or compatible
                        return vm.mirrorOf(prim.getAsLong());
                }
            } else if (prim.isString()) {
                String str = prim.getAsString();
                if (typeName.equals("java.lang.String")) {
                    return vm.mirrorOf(str);
                } else if (typeName.equals("char") && str.length() == 1) {
                    return vm.mirrorOf(str.charAt(0));
                } else if (str.startsWith("@")) {
                    // Object reference by ID
                    long objId = Long.parseLong(str.substring(1));
                    ObjectReference objRef = findObjectById(vm, objId);
                    if (objRef == null) {
                        throw new IllegalArgumentException("Object not found: " + str);
                    }
                    return objRef;
                }
                return vm.mirrorOf(str);
            }
        }

        throw new IllegalArgumentException("Cannot convert JSON element to " + typeName);
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

