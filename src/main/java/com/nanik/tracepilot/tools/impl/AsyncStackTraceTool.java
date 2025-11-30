package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.*;

import java.util.*;

/**
 * Tool to show async/reactive stack traces across thread boundaries.
 * 
 * For reactive and async code (CompletableFuture, Reactor, RxJava, ExecutorService),
 * this tool attempts to show the logical call flow across threads by:
 * 
 * 1. Showing the current thread's stack
 * 2. Identifying async framework patterns (e.g., ForkJoinPool workers, Reactor schedulers)
 * 3. Looking for related threads that might be part of the same async operation
 * 4. Showing any captured context (like originating thread name in task objects)
 */
public class AsyncStackTraceTool implements ToolHandler {

    // Common async framework thread name patterns
    private static final String[] ASYNC_THREAD_PATTERNS = {
        "ForkJoinPool",
        "parallel-",
        "boundedElastic-",
        "reactor-",
        "rxjava-",
        "Executor",
        "pool-",
        "AsyncTask",
        "CompletableFuture",
        "http-nio-",
        "tomcat-",
        "undertow-"
    };

    // Classes that often hold async context
    private static final String[] ASYNC_CONTEXT_CLASSES = {
        "java.util.concurrent.CompletableFuture",
        "java.util.concurrent.FutureTask",
        "reactor.core.publisher.Mono",
        "reactor.core.publisher.Flux",
        "io.reactivex.rxjava3.core.Observable",
        "io.reactivex.rxjava3.core.Single",
        "java.lang.Runnable",
        "java.util.concurrent.Callable"
    };

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addInteger("threadId", "Thread ID to analyze (optional - uses first suspended thread)", false)
            .addBoolean("showAllSuspended", "Show stack traces of all suspended threads (default: false)", false)
            .addInteger("maxFrames", "Maximum frames per thread (default: 15)", false)
            .build();

        return new ToolDefinition(
            "async_stack_trace",
            "Show async/reactive stack traces across threads. Helps debug CompletableFuture, Reactor, RxJava, and thread pool code.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }

        Long threadId = request.getLongParam("threadId");
        Boolean showAllSuspended = request.getBoolParam("showAllSuspended");
        Integer maxFrames = request.getIntParam("maxFrames");

        if (showAllSuspended == null) showAllSuspended = false;
        if (maxFrames == null) maxFrames = 15;

        VirtualMachine vm = DebugSession.getInstance().getVm();
        List<ThreadReference> allThreads = vm.allThreads();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Async Stack Trace Analysis ===\n\n");

        // Find the primary thread to analyze
        ThreadReference primaryThread = null;
        if (threadId != null) {
            for (ThreadReference t : allThreads) {
                if (t.uniqueID() == threadId) {
                    primaryThread = t;
                    break;
                }
            }
            if (primaryThread == null) {
                return ToolResult.error("Thread not found with ID: " + threadId);
            }
        } else {
            // Find first suspended non-system thread
            for (ThreadReference t : allThreads) {
                if (t.isSuspended() && !isSystemThread(t)) {
                    primaryThread = t;
                    break;
                }
            }
        }

        if (primaryThread == null) {
            return ToolResult.error("No suspended thread found. Suspend the VM first.");
        }

        // Analyze the primary thread
        sb.append("--- Primary Thread ---\n");
        appendThreadStack(sb, primaryThread, maxFrames);

        // Detect async framework and find related threads
        String asyncFramework = detectAsyncFramework(primaryThread);
        if (asyncFramework != null) {
            sb.append("\nDetected async framework: ").append(asyncFramework).append("\n");
        }

        // Find related async threads
        List<ThreadReference> relatedThreads = findRelatedAsyncThreads(allThreads, primaryThread);
        if (!relatedThreads.isEmpty()) {
            sb.append("\n--- Related Async Threads (").append(relatedThreads.size()).append(") ---\n\n");
            for (ThreadReference related : relatedThreads) {
                appendThreadStack(sb, related, Math.min(maxFrames, 8));
                sb.append("\n");
            }
        }

        // Optionally show all suspended threads
        if (showAllSuspended) {
            sb.append("\n--- All Suspended Threads ---\n\n");
            int count = 0;
            for (ThreadReference t : allThreads) {
                if (t.isSuspended() && t != primaryThread && !relatedThreads.contains(t)) {
                    appendThreadStack(sb, t, Math.min(maxFrames, 5));
                    sb.append("\n");
                    count++;
                    if (count >= 10) {
                        sb.append("... and ").append(allThreads.size() - count - 1 - relatedThreads.size())
                          .append(" more suspended threads\n");
                        break;
                    }
                }
            }
        }

        // Provide tips for async debugging
        sb.append("\n--- Async Debugging Tips ---\n");
        sb.append("• Use watch_add to track async values across steps\n");
        sb.append("• Set method breakpoints on subscribe/onNext for reactive streams\n");
        sb.append("• Use exception_break_on to catch errors in async callbacks\n");

        return ToolResult.success(sb.toString());
    }

    private void appendThreadStack(StringBuilder sb, ThreadReference thread, int maxFrames) {
        sb.append("[").append(thread.name()).append("] (ID: ").append(thread.uniqueID()).append(")\n");

        if (!thread.isSuspended()) {
            sb.append("  (thread not suspended)\n");
            return;
        }

        try {
            int frameCount = thread.frameCount();
            if (frameCount == 0) {
                sb.append("  (no stack frames)\n");
                return;
            }

            List<StackFrame> frames = thread.frames(0, Math.min(maxFrames, frameCount));
            for (int i = 0; i < frames.size(); i++) {
                StackFrame frame = frames.get(i);
                Location loc = frame.location();
                String className = loc.declaringType().name();
                String methodName = loc.method().name();
                int line = loc.lineNumber();

                // Highlight async-related frames
                String prefix = isAsyncFrame(className) ? "→ " : "  ";
                sb.append(String.format("%s#%d %s.%s:%d\n", prefix, i, shortenClassName(className), methodName, line));
            }

            if (frames.size() < frameCount) {
                sb.append("  ... ").append(frameCount - frames.size()).append(" more frames\n");
            }
        } catch (IncompatibleThreadStateException e) {
            sb.append("  (unable to get frames: ").append(e.getMessage()).append(")\n");
        }
    }

    private boolean isSystemThread(ThreadReference thread) {
        String name = thread.name();
        return name.startsWith("Reference Handler") ||
               name.startsWith("Finalizer") ||
               name.startsWith("Signal Dispatcher") ||
               name.startsWith("Attach Listener") ||
               name.startsWith("JDI Event Handler") ||
               name.startsWith("Common-Cleaner") ||
               name.startsWith("GC ") ||
               name.startsWith("VM ");
    }

    private String detectAsyncFramework(ThreadReference thread) {
        String threadName = thread.name();

        if (threadName.contains("ForkJoinPool")) return "Java ForkJoinPool (CompletableFuture)";
        if (threadName.contains("boundedElastic") || threadName.contains("parallel")) return "Project Reactor";
        if (threadName.contains("rxjava") || threadName.contains("RxComputation")) return "RxJava";
        if (threadName.contains("http-nio")) return "Tomcat NIO";
        if (threadName.contains("undertow")) return "Undertow Async";

        // Check stack frames for async patterns
        if (thread.isSuspended()) {
            try {
                for (StackFrame frame : thread.frames(0, Math.min(10, thread.frameCount()))) {
                    String className = frame.location().declaringType().name();
                    if (className.contains("CompletableFuture")) return "Java CompletableFuture";
                    if (className.contains("reactor.core")) return "Project Reactor";
                    if (className.contains("io.reactivex")) return "RxJava";
                    if (className.contains("kotlinx.coroutines")) return "Kotlin Coroutines";
                }
            } catch (IncompatibleThreadStateException ignored) {}
        }

        return null;
    }

    private List<ThreadReference> findRelatedAsyncThreads(List<ThreadReference> allThreads,
                                                           ThreadReference primaryThread) {
        List<ThreadReference> related = new ArrayList<>();
        String primaryName = primaryThread.name();

        // Extract pool prefix if applicable
        String poolPrefix = extractPoolPrefix(primaryName);

        for (ThreadReference t : allThreads) {
            if (t == primaryThread || !t.isSuspended() || isSystemThread(t)) continue;

            String name = t.name();

            // Same thread pool
            if (poolPrefix != null && name.startsWith(poolPrefix)) {
                related.add(t);
                continue;
            }

            // Check if thread is in async-related state
            for (String pattern : ASYNC_THREAD_PATTERNS) {
                if (name.contains(pattern)) {
                    related.add(t);
                    break;
                }
            }
        }

        // Limit to avoid overwhelming output
        if (related.size() > 5) {
            return related.subList(0, 5);
        }
        return related;
    }

    private String extractPoolPrefix(String threadName) {
        // Extract prefix like "ForkJoinPool-1-worker" from "ForkJoinPool-1-worker-3"
        int lastDash = threadName.lastIndexOf('-');
        if (lastDash > 0) {
            String prefix = threadName.substring(0, lastDash);
            // Check if what follows is a number
            String suffix = threadName.substring(lastDash + 1);
            try {
                Integer.parseInt(suffix);
                return prefix;
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private boolean isAsyncFrame(String className) {
        for (String pattern : ASYNC_CONTEXT_CLASSES) {
            if (className.startsWith(pattern.substring(0, pattern.lastIndexOf('.')))) {
                return true;
            }
        }
        return className.contains("Lambda") ||
               className.contains("$$") ||
               className.contains("async") ||
               className.contains("Async");
    }

    private String shortenClassName(String fullName) {
        // Convert com.example.MyClass to c.e.MyClass for readability
        String[] parts = fullName.split("\\.");
        if (parts.length <= 2) return fullName;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            sb.append(parts[i].charAt(0)).append(".");
        }
        sb.append(parts[parts.length - 1]);
        return sb.toString();
    }
}
