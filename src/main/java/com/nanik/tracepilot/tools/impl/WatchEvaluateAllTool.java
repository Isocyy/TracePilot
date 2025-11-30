package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.debug.WatchExpressionManager;
import com.nanik.tracepilot.debug.WatchExpressionManager.WatchExpression;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.*;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool to evaluate all watch expressions at once.
 * Requires VM to be suspended.
 */
public class WatchEvaluateAllTool implements ToolHandler {

    private static final Pattern METHOD_CALL = Pattern.compile(
        "^([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\((.*)\\)$"
    );
    private static final Pattern FIELD_ACCESS = Pattern.compile(
        "^([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\.\\s*(.+)$"
    );

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("threadId", "Thread ID to use for evaluation (must be suspended)", true)
            .addInteger("frameIndex", "Stack frame index (0 = top frame)", false)
            .build();

        return new ToolDefinition(
            "watch_evaluate_all",
            "Evaluate all watch expressions in the context of a suspended thread. Returns current values for all watches.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }

        Long threadId = request.getLongParam("threadId");
        if (threadId == null) {
            return ToolResult.error("threadId is required");
        }

        Integer frameIndex = request.getIntParam("frameIndex");
        if (frameIndex == null) frameIndex = 0;

        WatchExpressionManager manager = WatchExpressionManager.getInstance();
        Collection<WatchExpression> watches = manager.getAllWatches();

        if (watches.isEmpty()) {
            return ToolResult.success("No watch expressions defined.\n\nUse watch_add to add expressions to watch.");
        }

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
            return ToolResult.error("Thread is not suspended. Suspend the VM first.");
        }

        StackFrame frame;
        try {
            if (frameIndex >= thread.frameCount()) {
                return ToolResult.error("Frame index out of range. Max: " + (thread.frameCount() - 1));
            }
            frame = thread.frame(frameIndex);
        } catch (IncompatibleThreadStateException e) {
            return ToolResult.error("Thread in incompatible state: " + e.getMessage());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Watch Expressions (").append(watches.size()).append(") ===\n\n");

        int successCount = 0;
        int errorCount = 0;

        for (WatchExpression watch : watches) {
            sb.append(watch.getId()).append(": ").append(watch.getExpression()).append("\n");
            
            try {
                Value result = evaluateExpression(vm, thread, frame, watch.getExpression());
                String valueStr = VariablesLocalTool.formatValue(result);
                watch.setLastValue(valueStr);
                sb.append("  = ").append(valueStr).append("\n");
                successCount++;
            } catch (Exception e) {
                String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                watch.setLastError(error);
                sb.append("  = <error> ").append(error).append("\n");
                errorCount++;
            }
            sb.append("\n");
        }

        sb.append("---\n");
        sb.append("Evaluated: ").append(successCount).append(" success, ").append(errorCount).append(" errors");

        return ToolResult.success(sb.toString());
    }

    // Expression evaluation methods - simplified version from EvaluateExpressionTool
    private Value evaluateExpression(VirtualMachine vm, ThreadReference thread, 
                                      StackFrame frame, String expr) throws Exception {
        expr = expr.trim();
        
        if (expr.equals("null")) return null;
        if (expr.equals("true")) return vm.mirrorOf(true);
        if (expr.equals("false")) return vm.mirrorOf(false);
        
        if (expr.startsWith("\"") && expr.endsWith("\"") && expr.length() >= 2) {
            return vm.mirrorOf(expr.substring(1, expr.length() - 1));
        }
        
        try {
            if (expr.contains(".") && !expr.contains("(")) {
                // Could be a number or field access - try number first
                return vm.mirrorOf(Double.parseDouble(expr));
            } else if (!expr.contains(".")) {
                return vm.mirrorOf(Integer.parseInt(expr));
            }
        } catch (NumberFormatException ignored) {}
        
        if (expr.equals("this")) {
            ObjectReference thisObj = frame.thisObject();
            if (thisObj == null) throw new IllegalArgumentException("No 'this' in static context");
            return thisObj;
        }

        // Field access or method call chain
        Matcher fieldMatcher = FIELD_ACCESS.matcher(expr);
        if (fieldMatcher.matches()) {
            String base = fieldMatcher.group(1);
            String rest = fieldMatcher.group(2);
            Value baseValue = resolveSimpleName(frame, base);
            return evaluateChain(vm, thread, baseValue, rest);
        }

        // Simple method call
        Matcher methodMatcher = METHOD_CALL.matcher(expr);
        if (methodMatcher.matches()) {
            ObjectReference thisObj = frame.thisObject();
            if (thisObj != null) {
                return invokeSimpleMethod(vm, thread, thisObj, 
                    methodMatcher.group(1), methodMatcher.group(2));
            }
        }

        return resolveSimpleName(frame, expr);
    }

    private Value resolveSimpleName(StackFrame frame, String name) throws AbsentInformationException {
        LocalVariable var = frame.visibleVariableByName(name);
        if (var != null) {
            return frame.getValue(var);
        }

        ObjectReference thisObj = frame.thisObject();
        if (thisObj != null) {
            Field field = thisObj.referenceType().fieldByName(name);
            if (field != null) {
                return thisObj.getValue(field);
            }
        }

        throw new IllegalArgumentException("Cannot resolve: " + name);
    }

    private Value evaluateChain(VirtualMachine vm, ThreadReference thread, Value base, String rest)
            throws Exception {
        if (base == null) {
            throw new IllegalArgumentException("NullPointerException: cannot access member on null");
        }
        if (!(base instanceof ObjectReference)) {
            throw new IllegalArgumentException("Cannot access member on primitive value");
        }

        ObjectReference obj = (ObjectReference) base;

        Matcher methodMatcher = METHOD_CALL.matcher(rest);
        if (methodMatcher.matches()) {
            return invokeSimpleMethod(vm, thread, obj, methodMatcher.group(1), methodMatcher.group(2));
        }

        Matcher fieldMatcher = FIELD_ACCESS.matcher(rest);
        if (fieldMatcher.matches()) {
            String fieldName = fieldMatcher.group(1);
            String chainRest = fieldMatcher.group(2);
            Field field = obj.referenceType().fieldByName(fieldName);
            if (field == null) throw new IllegalArgumentException("Field not found: " + fieldName);
            return evaluateChain(vm, thread, obj.getValue(field), chainRest);
        }

        Field field = obj.referenceType().fieldByName(rest);
        if (field != null) {
            return obj.getValue(field);
        }

        throw new IllegalArgumentException("Cannot resolve: " + rest);
    }

    private Value invokeSimpleMethod(VirtualMachine vm, ThreadReference thread,
                                      ObjectReference obj, String methodName, String argsStr)
            throws Exception {
        java.util.List<Method> methods = obj.referenceType().methodsByName(methodName);
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("Method not found: " + methodName);
        }

        // For simplicity, use first no-arg method or first method
        Method method = null;
        for (Method m : methods) {
            try {
                if (m.argumentTypes().isEmpty()) {
                    method = m;
                    break;
                }
            } catch (ClassNotLoadedException ignored) {}
        }
        if (method == null) method = methods.get(0);

        return obj.invokeMethod(thread, method, java.util.Collections.emptyList(),
            ObjectReference.INVOKE_SINGLE_THREADED);
    }
}
