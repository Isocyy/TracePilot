package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool to evaluate simple expressions in the context of a suspended thread.
 * 
 * Supports:
 * - Variable references: varName
 * - Field access: obj.field, this.field
 * - Method calls: obj.method(), obj.method(arg1, arg2)
 * - Static method calls: ClassName.method()
 * - Literals: 42, "hello", true, null
 * - Simple arithmetic on primitives: a + b, x * 2
 */
public class EvaluateExpressionTool implements ToolHandler {

    private static final Pattern METHOD_CALL = Pattern.compile(
        "^([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\((.*)\\)$"
    );
    private static final Pattern FIELD_ACCESS = Pattern.compile(
        "^([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\.\\s*(.+)$"
    );

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("threadId", "Thread ID (must be suspended)", true)
            .addInteger("frameIndex", "Stack frame index (0 = top frame)", false)
            .addString("expression", "Expression to evaluate", true)
            .build();

        return new ToolDefinition(
            "evaluate_expression",
            "Evaluate expression in suspended thread context. Supports: vars, a.b, obj.method(), literals. Thread MUST be suspended first.",
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

        String expression = request.getStringParam("expression");
        if (expression == null || expression.trim().isEmpty()) {
            return ToolResult.error("expression is required");
        }
        expression = expression.trim();

        Integer frameIndex = request.getIntParam("frameIndex");
        if (frameIndex == null) frameIndex = 0;

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
            return ToolResult.error("Thread is not suspended.");
        }

        try {
            if (frameIndex >= thread.frameCount()) {
                return ToolResult.error("Frame index out of range. Max: " + (thread.frameCount() - 1));
            }
            StackFrame frame = thread.frame(frameIndex);
            
            Value result = evaluateExpression(vm, thread, frame, expression);
            
            StringBuilder sb = new StringBuilder();
            sb.append("Expression: ").append(expression).append("\n");
            sb.append("Value: ").append(VariablesLocalTool.formatValue(result)).append("\n");
            if (result != null) {
                sb.append("Type: ").append(result.type().name());
            } else {
                sb.append("Type: null");
            }
            
            return ToolResult.success(sb.toString());

        } catch (AbsentInformationException e) {
            return ToolResult.error("Debug info not available. Compile with -g flag.");
        } catch (IncompatibleThreadStateException e) {
            return ToolResult.error("Thread in incompatible state: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Evaluation failed: " + e.getMessage());
        }
    }

    private Value evaluateExpression(VirtualMachine vm, ThreadReference thread, 
                                      StackFrame frame, String expr) 
            throws AbsentInformationException, IncompatibleThreadStateException,
                   InvalidTypeException, ClassNotLoadedException, InvocationException {
        
        expr = expr.trim();
        
        // Check for null literal
        if (expr.equals("null")) {
            return null;
        }
        
        // Check for boolean literals
        if (expr.equals("true")) {
            return vm.mirrorOf(true);
        }
        if (expr.equals("false")) {
            return vm.mirrorOf(false);
        }
        
        // Check for string literal
        if (expr.startsWith("\"") && expr.endsWith("\"") && expr.length() >= 2) {
            return vm.mirrorOf(expr.substring(1, expr.length() - 1));
        }
        
        // Check for numeric literal
        try {
            if (expr.contains(".")) {
                return vm.mirrorOf(Double.parseDouble(expr));
            } else {
                return vm.mirrorOf(Integer.parseInt(expr));
            }
        } catch (NumberFormatException ignored) {
            // Not a number, continue
        }
        
        // Check for char literal
        if (expr.startsWith("'") && expr.endsWith("'") && expr.length() == 3) {
            return vm.mirrorOf(expr.charAt(1));
        }

        // Check for 'this' keyword
        if (expr.equals("this")) {
            ObjectReference thisObj = frame.thisObject();
            if (thisObj == null) {
                throw new IllegalArgumentException("No 'this' in static context");
            }
            return thisObj;
        }

        // Check for field access (a.b.c) or method call (a.b.method())
        Matcher fieldMatcher = FIELD_ACCESS.matcher(expr);
        if (fieldMatcher.matches()) {
            String base = fieldMatcher.group(1);
            String rest = fieldMatcher.group(2);

            Value baseValue = resolveSimpleName(vm, frame, base);
            return evaluateChain(vm, thread, frame, baseValue, rest);
        }

        // Check for simple method call (method())
        Matcher methodMatcher = METHOD_CALL.matcher(expr);
        if (methodMatcher.matches()) {
            String methodName = methodMatcher.group(1);
            String argsStr = methodMatcher.group(2);

            // Try on 'this' object
            ObjectReference thisObj = frame.thisObject();
            if (thisObj != null) {
                return invokeMethod(vm, thread, thisObj, methodName, argsStr);
            }
            throw new IllegalArgumentException("Cannot call method without object context");
        }

        // Try to resolve as local variable or argument
        return resolveSimpleName(vm, frame, expr);
    }

    private Value resolveSimpleName(VirtualMachine vm, StackFrame frame, String name)
            throws AbsentInformationException {
        // Try local variable
        LocalVariable var = frame.visibleVariableByName(name);
        if (var != null) {
            return frame.getValue(var);
        }

        // Try 'this' field
        ObjectReference thisObj = frame.thisObject();
        if (thisObj != null) {
            ReferenceType refType = thisObj.referenceType();
            Field field = refType.fieldByName(name);
            if (field != null) {
                return thisObj.getValue(field);
            }
        }

        throw new IllegalArgumentException("Cannot resolve: " + name);
    }

    private Value evaluateChain(VirtualMachine vm, ThreadReference thread, StackFrame frame,
                                 Value base, String rest)
            throws InvalidTypeException, ClassNotLoadedException, InvocationException,
                   IncompatibleThreadStateException {

        if (base == null) {
            throw new IllegalArgumentException("NullPointerException: cannot access member on null");
        }

        if (!(base instanceof ObjectReference)) {
            throw new IllegalArgumentException("Cannot access member on primitive value");
        }

        ObjectReference obj = (ObjectReference) base;

        // Check for method call
        Matcher methodMatcher = METHOD_CALL.matcher(rest);
        if (methodMatcher.matches()) {
            String methodName = methodMatcher.group(1);
            String argsStr = methodMatcher.group(2);
            return invokeMethod(vm, thread, obj, methodName, argsStr);
        }

        // Check for chained access (a.b.c)
        Matcher fieldMatcher = FIELD_ACCESS.matcher(rest);
        if (fieldMatcher.matches()) {
            String fieldName = fieldMatcher.group(1);
            String chainRest = fieldMatcher.group(2);

            ReferenceType refType = obj.referenceType();
            Field field = refType.fieldByName(fieldName);
            if (field == null) {
                throw new IllegalArgumentException("Field not found: " + fieldName);
            }
            Value fieldValue = obj.getValue(field);
            return evaluateChain(vm, thread, frame, fieldValue, chainRest);
        }

        // Simple field access
        ReferenceType refType = obj.referenceType();
        Field field = refType.fieldByName(rest);
        if (field != null) {
            return obj.getValue(field);
        }

        throw new IllegalArgumentException("Cannot resolve: " + rest + " on " + obj.type().name());
    }

    private Value invokeMethod(VirtualMachine vm, ThreadReference thread,
                                ObjectReference obj, String methodName, String argsStr)
            throws InvalidTypeException, ClassNotLoadedException, InvocationException,
                   IncompatibleThreadStateException {

        ReferenceType refType = obj.referenceType();
        List<Method> methods = refType.methodsByName(methodName);

        if (methods.isEmpty()) {
            throw new IllegalArgumentException("Method not found: " + methodName);
        }

        // Parse arguments (simple comma-separated)
        List<String> argTokens = parseArguments(argsStr);

        // Find method with matching argument count
        Method method = null;
        for (Method m : methods) {
            try {
                if (m.argumentTypes().size() == argTokens.size()) {
                    method = m;
                    break;
                }
            } catch (ClassNotLoadedException e) {
                // Skip
            }
        }

        if (method == null) {
            if (methods.size() == 1) {
                method = methods.get(0);
            } else {
                throw new IllegalArgumentException(
                    "Cannot find method " + methodName + " with " + argTokens.size() + " arguments");
            }
        }

        // Convert argument strings to values
        List<Value> args = new ArrayList<>();
        List<Type> paramTypes = method.argumentTypes();
        for (int i = 0; i < argTokens.size(); i++) {
            String argToken = argTokens.get(i).trim();
            Type paramType = paramTypes.get(i);
            args.add(parseSimpleValue(vm, paramType, argToken));
        }

        return obj.invokeMethod(thread, method, args, ObjectReference.INVOKE_SINGLE_THREADED);
    }

    private List<String> parseArguments(String argsStr) {
        List<String> result = new ArrayList<>();
        if (argsStr == null || argsStr.trim().isEmpty()) {
            return result;
        }

        // Simple comma split (doesn't handle nested commas in strings)
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;

        for (char c : argsStr.toCharArray()) {
            if (c == '"') {
                inString = !inString;
            }
            if (!inString) {
                if (c == '(' || c == '[') depth++;
                else if (c == ')' || c == ']') depth--;
                else if (c == ',' && depth == 0) {
                    result.add(current.toString());
                    current = new StringBuilder();
                    continue;
                }
            }
            current.append(c);
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private Value parseSimpleValue(VirtualMachine vm, Type targetType, String valueStr) {
        valueStr = valueStr.trim();

        if (valueStr.equals("null")) return null;
        if (valueStr.equals("true")) return vm.mirrorOf(true);
        if (valueStr.equals("false")) return vm.mirrorOf(false);

        if (valueStr.startsWith("\"") && valueStr.endsWith("\"")) {
            return vm.mirrorOf(valueStr.substring(1, valueStr.length() - 1));
        }

        String typeName = targetType.name();
        try {
            switch (typeName) {
                case "int": return vm.mirrorOf(Integer.parseInt(valueStr));
                case "long": return vm.mirrorOf(Long.parseLong(valueStr.replace("L", "")));
                case "double": return vm.mirrorOf(Double.parseDouble(valueStr));
                case "float": return vm.mirrorOf(Float.parseFloat(valueStr.replace("f", "")));
                case "boolean": return vm.mirrorOf(Boolean.parseBoolean(valueStr));
                case "java.lang.String": return vm.mirrorOf(valueStr);
                default:
                    return vm.mirrorOf(Integer.parseInt(valueStr));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot parse '" + valueStr + "' as " + typeName);
        }
    }
}

