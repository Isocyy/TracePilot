package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.debug.StopReason;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Location;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;

import java.util.List;

public class StackFramesTool implements ToolHandler {
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 100;

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
        ThreadReference targetThread = null;

        // Find the thread
        for (ThreadReference thread : vm.allThreads()) {
            if (thread.uniqueID() == threadId) {
                targetThread = thread;
                break;
            }
        }

        if (targetThread == null) {
            return ToolResult.error("Thread not found with ID: " + threadId);
        }

        // Retry logic for transient failures
        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return getStackFramesWithRetry(targetThread, threadId, maxFrames);
            } catch (IncompatibleThreadStateException e) {
                lastException = e;
                // Thread may have been briefly resumed, wait and retry
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                lastException = e;
                break; // Non-retryable error
            }
        }

        // All retries failed - try to provide fallback info from StopReason
        return getFallbackStackInfo(threadId, lastException);
    }

    private ToolResult getStackFramesWithRetry(ThreadReference thread, long threadId, int maxFrames)
            throws IncompatibleThreadStateException {
        if (!thread.isSuspended()) {
            throw new IncompatibleThreadStateException("Thread '" + thread.name() + "' is not suspended.");
        }

        int frameCount = thread.frameCount();
        if (frameCount == 0) {
            return ToolResult.success("Stack frames for thread '" + thread.name() +
                "' (ID: " + threadId + "):\n\n(no stack frames - thread may be starting or ending)\n");
        }

        List<StackFrame> frames = thread.frames(0, Math.min(maxFrames, frameCount));
        StringBuilder sb = new StringBuilder();
        sb.append("Stack frames for thread '").append(thread.name())
          .append("' (ID: ").append(threadId).append("):\n\n");

        for (int i = 0; i < frames.size(); i++) {
            StackFrame frame = frames.get(i);
            Location loc = frame.location();
            String sourceName = getSourceNameSafe(loc);
            sb.append(String.format("#%d %s.%s(%s:%d)\n",
                i, loc.declaringType().name(), loc.method().name(),
                sourceName, loc.lineNumber()));
        }

        if (frames.size() < frameCount) {
            sb.append("\n... ").append(frameCount - frames.size()).append(" more frames (use maxFrames to see more)\n");
        }

        return ToolResult.success(sb.toString());
    }

    private String getSourceNameSafe(Location loc) {
        try {
            String name = loc.sourceName();
            return name != null ? name : "Unknown";
        } catch (AbsentInformationException e) {
            // No debug info for source file name
            return loc.declaringType().name().replace('.', '/') + ".java";
        }
    }

    private ToolResult getFallbackStackInfo(long threadId, Exception lastException) {
        StringBuilder sb = new StringBuilder();
        sb.append("Failed to get full stack frames: ").append(lastException.getMessage()).append("\n\n");

        // Try to get info from StopReason
        StopReason stopReason = DebugSession.getInstance().getLastStopReason();
        if (stopReason != null && stopReason.getThreadId() == threadId && stopReason.getLocation() != null) {
            Location loc = stopReason.getLocation();
            sb.append("=== Fallback: Last Known Location ===\n");
            sb.append("class: ").append(loc.declaringType().name()).append("\n");
            sb.append("method: ").append(loc.method().name()).append("\n");
            sb.append("line: ").append(loc.lineNumber()).append("\n");
            sb.append("\nNote: Full stack trace unavailable. Thread may have changed state.\n");
            sb.append("Try: threads_list to check thread status, then retry.\n");
            return ToolResult.success(sb.toString());
        }

        sb.append("No fallback location available.\n");
        sb.append("Suggestions:\n");
        sb.append("1. Use threads_list to verify thread is suspended\n");
        sb.append("2. Use execution_location for current position\n");
        sb.append("3. Retry after a brief pause\n");
        return ToolResult.error(sb.toString());
    }
}
