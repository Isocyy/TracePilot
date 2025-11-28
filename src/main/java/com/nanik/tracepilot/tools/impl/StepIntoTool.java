package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.sun.jdi.*;
import com.sun.jdi.request.*;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

/**
 * Tool to step into the next method call.
 */
public class StepIntoTool implements ToolHandler {
    
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("threadId", "Thread ID to step (optional - uses first suspended thread)")
            .build();
        
        return new ToolDefinition(
            "step_into",
            "Step into the next method call. IMPORTANT: Call wait_for_stop after this to know when step completes. Thread must be suspended.",
            schema
        );
    }
    
    @Override
    public ToolResult execute(McpRequest request) {
        DebugSession session = DebugSession.getInstance();
        VirtualMachine vm = session.getVm();
        if (vm == null) {
            return ToolResult.error("Not connected to a VM.");
        }

        String threadId = request.getStringParam("threadId");

        try {
            ThreadReference thread = findSuspendedThread(vm, threadId);
            if (thread == null) {
                return ToolResult.error("No suspended thread found in Java code. " +
                    "All threads are either running or in native code. " +
                    "Set a breakpoint and resume to stop in Java code first.");
            }

            // Check if thread is in Java code (not native)
            try {
                StackFrame frame = thread.frame(0);
                if (frame.location().lineNumber() < 0) {
                    return ToolResult.error("Thread '" + thread.name() + "' is in native code (line -1). " +
                        "Cannot step in native methods. Set a breakpoint in Java code first.");
                }
            } catch (IncompatibleThreadStateException e) {
                return ToolResult.error("Thread is not in a valid state for stepping.");
            }

            // Create step request
            EventRequestManager erm = vm.eventRequestManager();
            StepRequest stepRequest = erm.createStepRequest(
                thread,
                StepRequest.STEP_LINE,
                StepRequest.STEP_INTO
            );
            stepRequest.addCountFilter(1); // Only fire once
            stepRequest.enable();

            // Clear stop reason before resuming
            session.clearStopReason();

            // Resume the thread to execute the step
            vm.resume();

            return ToolResult.success("Step into initiated on thread: " + thread.name() +
                "\nUse debug_status or wait_for_stop to see where execution stopped.");

        } catch (Exception e) {
            return ToolResult.error("Failed to step: " + e.getMessage());
        }
    }
    
    private ThreadReference findSuspendedThread(VirtualMachine vm, String threadId) {
        if (threadId != null && !threadId.isEmpty()) {
            try {
                long id = Long.parseLong(threadId);
                for (ThreadReference thread : vm.allThreads()) {
                    if (thread.uniqueID() == id && thread.isSuspended()) {
                        return thread;
                    }
                }
            } catch (NumberFormatException e) {
                for (ThreadReference thread : vm.allThreads()) {
                    if (thread.name().equals(threadId) && thread.isSuspended()) {
                        return thread;
                    }
                }
            }
        } else {
            // Prefer threads that are in Java code (not native methods)
            ThreadReference fallback = null;
            for (ThreadReference thread : vm.allThreads()) {
                try {
                    if (thread.isSuspended() && thread.frameCount() > 0) {
                        StackFrame frame = thread.frame(0);
                        if (frame.location().lineNumber() > 0) {
                            return thread; // In Java code
                        }
                        if (fallback == null) {
                            fallback = thread;
                        }
                    }
                } catch (IncompatibleThreadStateException e) {
                    // Skip this thread
                }
            }
            return fallback;
        }
        return null;
    }
}

