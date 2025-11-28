package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.sun.jdi.*;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

import java.util.List;

/**
 * Tool to get current execution location.
 */
public class ExecutionLocationTool implements ToolHandler {
    
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("threadId", "Thread ID (optional - shows first suspended thread)")
            .build();
        
        return new ToolDefinition(
            "execution_location",
            "Get the current execution location (class, method, line) for a suspended thread.",
            schema
        );
    }
    
    @Override
    public ToolResult execute(McpRequest request) {
        VirtualMachine vm = DebugSession.getInstance().getVm();
        if (vm == null) {
            return ToolResult.error("Not connected to a VM.");
        }
        
        String threadId = request.getStringParam("threadId");
        
        try {
            ThreadReference thread = findSuspendedThread(vm, threadId);
            if (thread == null) {
                // Check if there are any threads at all
                List<ThreadReference> threads = vm.allThreads();
                if (threads.isEmpty()) {
                    return ToolResult.error("No threads in VM.");
                }
                
                // List thread states
                StringBuilder sb = new StringBuilder();
                sb.append("No suspended thread found. Thread states:\n\n");
                for (ThreadReference t : threads) {
                    sb.append(String.format("  %s (id=%d) - %s\n",
                        t.name(),
                        t.uniqueID(),
                        t.isSuspended() ? "suspended" : "running"
                    ));
                }
                return ToolResult.error(sb.toString());
            }
            
            // Get the top frame
            StackFrame frame = thread.frame(0);
            Location location = frame.location();
            
            StringBuilder sb = new StringBuilder();
            sb.append("=== Execution Location ===\n\n");
            sb.append("Thread: ").append(thread.name()).append(" (id=").append(thread.uniqueID()).append(")\n");
            sb.append("Class: ").append(location.declaringType().name()).append("\n");
            sb.append("Method: ").append(location.method().name()).append(location.method().signature()).append("\n");
            sb.append("Line: ").append(location.lineNumber()).append("\n");
            
            try {
                String sourceName = location.sourceName();
                sb.append("Source: ").append(sourceName).append("\n");
            } catch (AbsentInformationException e) {
                sb.append("Source: (not available)\n");
            }
            
            // Show a few frames of context
            sb.append("\nStack trace (top 5):\n");
            int frameCount = Math.min(5, thread.frameCount());
            for (int i = 0; i < frameCount; i++) {
                StackFrame f = thread.frame(i);
                Location loc = f.location();
                sb.append(String.format("  #%d: %s.%s() at line %d\n",
                    i,
                    loc.declaringType().name(),
                    loc.method().name(),
                    loc.lineNumber()
                ));
            }
            
            return ToolResult.success(sb.toString());
            
        } catch (IncompatibleThreadStateException e) {
            return ToolResult.error("Thread is not suspended: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to get location: " + e.getMessage());
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
            for (ThreadReference thread : vm.allThreads()) {
                try {
                    if (thread.isSuspended() && thread.frameCount() > 0) {
                        return thread;
                    }
                } catch (IncompatibleThreadStateException e) {
                    // Skip
                }
            }
        }
        return null;
    }
}

