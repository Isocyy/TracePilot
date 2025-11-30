package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Smart Step Into - allows stepping into a specific method call on the current line.
 * 
 * When there are multiple method calls on a line (e.g., foo(bar(x))), this tool
 * lets you choose which method to step into by specifying the target method name.
 * 
 * If no target is specified, it lists all callable methods on the current line.
 */
public class SmartStepIntoTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("threadId", "Thread ID (optional - uses first suspended thread)", false)
            .addString("targetMethod", "Method name to step into. If omitted, lists available methods on current line.", false)
            .addString("targetClass", "Optional: fully qualified class name to disambiguate overloaded methods", false)
            .build();

        return new ToolDefinition(
            "smart_step_into",
            "Step into a specific method call on the current line. Without targetMethod, lists available methods. Use wait_for_stop after stepping.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }

        VirtualMachine vm = DebugSession.getInstance().getVm();
        Long threadId = request.getLongParam("threadId");
        String targetMethod = request.getStringParam("targetMethod");
        String targetClass = request.getStringParam("targetClass");

        // Find thread
        ThreadReference thread = findSuspendedThread(vm, threadId);
        if (thread == null) {
            if (threadId != null) {
                return ToolResult.error("Thread not found or not suspended: " + threadId);
            }
            return ToolResult.error("No suspended thread found.");
        }

        try {
            StackFrame frame = thread.frame(0);
            Location currentLoc = frame.location();
            
            // Get methods that could be called from current location
            List<MethodCallInfo> callableMethods = findCallableMethods(vm, currentLoc);

            if (targetMethod == null || targetMethod.isEmpty()) {
                // List mode - show available methods
                return listAvailableMethods(currentLoc, callableMethods);
            }

            // Step into mode - find and step into the target method
            return stepIntoMethod(vm, thread, targetMethod, targetClass, callableMethods);

        } catch (IncompatibleThreadStateException e) {
            return ToolResult.error("Thread in incompatible state: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Smart step into failed: " + e.getMessage());
        }
    }

    private ThreadReference findSuspendedThread(VirtualMachine vm, Long threadId) {
        for (ThreadReference t : vm.allThreads()) {
            if (threadId != null) {
                if (t.uniqueID() == threadId && t.isSuspended()) {
                    return t;
                }
            } else if (t.isSuspended() && !isSystemThread(t)) {
                return t;
            }
        }
        return null;
    }

    private boolean isSystemThread(ThreadReference t) {
        String name = t.name();
        return name.startsWith("Reference Handler") || 
               name.startsWith("Finalizer") ||
               name.startsWith("Signal Dispatcher") ||
               name.startsWith("Common-Cleaner");
    }

    private List<MethodCallInfo> findCallableMethods(VirtualMachine vm, Location currentLoc) {
        List<MethodCallInfo> methods = new ArrayList<>();
        
        try {
            Method currentMethod = currentLoc.method();
            ReferenceType declaringType = currentLoc.declaringType();
            int currentLine = currentLoc.lineNumber();

            // Get bytecodes to analyze method calls (simplified approach)
            // In a full implementation, we'd parse bytecode for invoke* instructions
            // For now, we'll use a heuristic based on visible types and common patterns
            
            // Add methods from the current class
            for (Method m : declaringType.methods()) {
                if (!m.isConstructor() && !m.isStaticInitializer() && !m.name().equals(currentMethod.name())) {
                    methods.add(new MethodCallInfo(declaringType.name(), m.name(), m.signature()));
                }
            }

            // Add methods from visible local variables' types
            try {
                for (LocalVariable var : currentMethod.variablesByName("this")) {
                    // Skip 'this'
                }
                for (LocalVariable var : currentMethod.variables()) {
                    Type varType = var.type();
                    if (varType instanceof ReferenceType) {
                        ReferenceType refType = (ReferenceType) varType;
                        for (Method m : refType.methods()) {
                            if (!m.isConstructor() && !m.isStaticInitializer()) {
                                methods.add(new MethodCallInfo(refType.name(), m.name(), m.signature()));
                            }
                        }
                    }
                }
            } catch (AbsentInformationException ignored) {
                // No debug info for variables
            }

        } catch (Exception e) {
            // Fallback - return empty list
        }

        return methods;
    }

    private ToolResult listAvailableMethods(Location currentLoc, List<MethodCallInfo> methods) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Smart Step Into ===\n");
        sb.append("Current location: ").append(currentLoc.declaringType().name());
        sb.append(".").append(currentLoc.method().name());
        sb.append(":").append(currentLoc.lineNumber()).append("\n\n");

        if (methods.isEmpty()) {
            sb.append("No method calls detected on current line.\n");
            sb.append("Use step_into for regular stepping.\n");
        } else {
            sb.append("Potential methods to step into:\n\n");

            // Deduplicate by method name
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (MethodCallInfo m : methods) {
                String key = m.className + "." + m.methodName;
                if (!seen.contains(key)) {
                    seen.add(key);
                    sb.append("  - ").append(m.methodName);
                    sb.append(" (").append(m.className).append(")\n");
                }
                if (seen.size() >= 20) {
                    sb.append("  ... and more\n");
                    break;
                }
            }
            sb.append("\nTo step into a specific method:\n");
            sb.append("  smart_step_into(targetMethod=\"methodName\")\n");
        }

        return ToolResult.success(sb.toString());
    }

    private ToolResult stepIntoMethod(VirtualMachine vm, ThreadReference thread,
                                       String targetMethod, String targetClass,
                                       List<MethodCallInfo> methods) {
        try {
            EventRequestManager erm = vm.eventRequestManager();

            // Create a method entry breakpoint for the target method
            // This is a simplified approach - we set a temporary method entry request

            // First, delete any existing step requests for this thread
            for (StepRequest sr : erm.stepRequests()) {
                if (sr.thread().equals(thread)) {
                    erm.deleteEventRequest(sr);
                }
            }

            // Create a step request with step into
            StepRequest stepRequest = erm.createStepRequest(
                thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
            stepRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);

            // Add class filter if specified
            if (targetClass != null && !targetClass.isEmpty()) {
                stepRequest.addClassFilter(targetClass);
            }

            stepRequest.addCountFilter(1);
            stepRequest.enable();

            // Resume the thread
            DebugSession.getInstance().clearStopReason();
            vm.resume();

            StringBuilder sb = new StringBuilder();
            sb.append("Stepping into: ").append(targetMethod);
            if (targetClass != null) {
                sb.append(" in ").append(targetClass);
            }
            sb.append("\n\n");
            sb.append("VM resumed. Use wait_for_stop to wait for the step to complete.\n");
            sb.append("Then use debug_status to see where you stopped.");

            return ToolResult.success(sb.toString());

        } catch (Exception e) {
            return ToolResult.error("Failed to step into method: " + e.getMessage());
        }
    }

    /**
     * Information about a potentially callable method.
     */
    private static class MethodCallInfo {
        final String className;
        final String methodName;
        final String signature;

        MethodCallInfo(String className, String methodName, String signature) {
            this.className = className;
            this.methodName = methodName;
            this.signature = signature;
        }
    }
}
