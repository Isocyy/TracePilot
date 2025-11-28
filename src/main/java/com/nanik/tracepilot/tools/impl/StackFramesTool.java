package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Location;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;

import java.util.List;

public class StackFramesTool implements ToolHandler {
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("threadId", "The unique ID of the thread", true)
            .addInteger("maxFrames", "Maximum number of frames to return (default: 20)", false)
            .build();
        return new ToolDefinition("stack_frames",
            "Get stack frames (call stack). Thread MUST be suspended. Use threadId from threads_list. Frame 0 is current location.",
            schema);
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }
        Long threadId = request.getLongParam("threadId");
        if (threadId == null) return ToolResult.error("threadId is required");
        
        Integer maxFrames = request.getIntParam("maxFrames");
        if (maxFrames == null) maxFrames = 20;
        
        VirtualMachine vm = DebugSession.getInstance().getVm();
        for (ThreadReference thread : vm.allThreads()) {
            if (thread.uniqueID() == threadId) {
                if (!thread.isSuspended()) {
                    return ToolResult.error("Thread '" + thread.name() + "' is not suspended.");
                }
                try {
                    List<StackFrame> frames = thread.frames(0, Math.min(maxFrames, thread.frameCount()));
                    StringBuilder sb = new StringBuilder();
                    sb.append("Stack frames for thread '").append(thread.name()).append("' (ID: ").append(threadId).append("):\n\n");
                    for (int i = 0; i < frames.size(); i++) {
                        StackFrame frame = frames.get(i);
                        Location loc = frame.location();
                        sb.append(String.format("#%d %s.%s(%s:%d)\n",
                            i, loc.declaringType().name(), loc.method().name(),
                            loc.sourceName() != null ? loc.sourceName() : "Unknown",
                            loc.lineNumber()));
                    }
                    return ToolResult.success(sb.toString());
                } catch (IncompatibleThreadStateException e) {
                    return ToolResult.error("Thread is not suspended: " + e.getMessage());
                } catch (Exception e) {
                    return ToolResult.error("Failed to get stack frames: " + e.getMessage());
                }
            }
        }
        return ToolResult.error("Thread not found with ID: " + threadId);
    }
}
