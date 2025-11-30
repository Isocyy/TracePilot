package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tool to launch a Gradle test with debugging enabled and auto-attach.
 *
 * This tool:
 * 1. Runs ./gradlew test --debug-jvm with the specified test filter
 * 2. Waits for the debug port (5005) to become available
 * 3. Automatically attaches the debugger
 *
 * Solves the common problem of Gradle test timeouts during debugging.
 *
 * IMPORTANT: If Gradle caches test results, it skips tests entirely and
 * never opens the debug port. Use clean=true to force a fresh run.
 */
public class DebugLaunchGradleTestTool implements ToolHandler {

    private static final int DEFAULT_DEBUG_PORT = 5005;
    private static final int DEFAULT_WAIT_TIMEOUT = 120; // Increased from 60 - Gradle can be slow
    private static final int PORT_CHECK_INTERVAL_MS = 500;
    private static final int MAX_OUTPUT_LINES = 100; // Keep last N lines for error reporting

    private Process gradleProcess;
    private Thread outputDrainThread;
    private final ConcurrentLinkedQueue<String> outputLines = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean outputDrainRunning = new AtomicBoolean(false);

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("projectDir", "Path to the Gradle project directory (default: current directory)", false)
            .addString("testFilter", "Test filter (e.g., 'MyTest', '**/MyTest.java', '--tests MyClass.myMethod')", false)
            .addInteger("port", "Debug port to use (default: 5005)", false)
            .addInteger("waitTimeout", "Seconds to wait for debug port (default: 120)", false)
            .addString("gradleArgs", "Additional Gradle arguments (e.g., '--info', '-Dspring.profiles.active=test')", false)
            .addBoolean("useWrapper", "Use Gradle wrapper (./gradlew) instead of 'gradle' (default: true)", false)
            .addBoolean("clean", "Run 'clean' before test to force fresh test run (RECOMMENDED - cached tests skip debug port opening)", false)
            .build();

        return new ToolDefinition(
            "debug_launch_gradle_test",
            "Launch a Gradle test with debugging enabled and auto-attach. Runs './gradlew test --debug-jvm' and connects when ready. " +
            "IMPORTANT: Use clean=true if tests are cached (Gradle skips cached tests and never opens debug port).",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        DebugSession session = DebugSession.getInstance();

        if (session.isConnected()) {
            return ToolResult.error("Already connected to a VM. Use debug_disconnect first.");
        }

        // Parse parameters
        String projectDir = request.getStringParam("projectDir");
        String testFilter = request.getStringParam("testFilter");
        Integer port = request.getIntParam("port");
        Integer waitTimeout = request.getIntParam("waitTimeout");
        String gradleArgs = request.getStringParam("gradleArgs");
        Boolean useWrapper = request.getBoolParam("useWrapper");
        Boolean clean = request.getBoolParam("clean");

        if (port == null) port = DEFAULT_DEBUG_PORT;
        if (waitTimeout == null) waitTimeout = DEFAULT_WAIT_TIMEOUT;
        if (useWrapper == null) useWrapper = true;
        if (clean == null) clean = false;

        File workDir = projectDir != null ? new File(projectDir) : new File(".");
        if (!workDir.exists() || !workDir.isDirectory()) {
            return ToolResult.error("Project directory does not exist: " + workDir.getAbsolutePath());
        }

        // Check for build.gradle
        File buildGradle = new File(workDir, "build.gradle");
        File buildGradleKts = new File(workDir, "build.gradle.kts");
        if (!buildGradle.exists() && !buildGradleKts.exists()) {
            return ToolResult.error("Not a Gradle project: no build.gradle or build.gradle.kts found in " + workDir.getAbsolutePath());
        }

        try {
            // Build the Gradle command
            List<String> command = buildGradleCommand(workDir, useWrapper, clean, testFilter, gradleArgs);

            StringBuilder sb = new StringBuilder();
            sb.append("=== Launching Gradle Test with Debug ===\n\n");
            sb.append("Command: ").append(String.join(" ", command)).append("\n");
            sb.append("Working directory: ").append(workDir.getAbsolutePath()).append("\n");
            sb.append("Debug port: ").append(port).append("\n");
            if (clean) {
                sb.append("Clean build: YES (forces fresh test run)\n");
            }
            sb.append("\n");

            // Launch Gradle process
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            gradleProcess = pb.start();

            // Start output drain thread to prevent process from blocking
            startOutputDrain(gradleProcess);

            sb.append("Gradle process started (PID: ").append(getProcessId(gradleProcess)).append(")\n");
            sb.append("Waiting for debug port ").append(port).append(" to become available (timeout: ").append(waitTimeout).append("s)...\n\n");

            // Wait for debug port with output monitoring
            boolean portReady = waitForDebugPort("localhost", port, waitTimeout, gradleProcess);

            if (!portReady) {
                // Stop output drain and kill the process
                stopOutputDrain();
                gradleProcess.destroyForcibly();

                // Provide helpful error message based on whether clean was used
                StringBuilder errorMsg = new StringBuilder();
                errorMsg.append("Timeout waiting for debug port ").append(port).append(" after ").append(waitTimeout).append(" seconds.\n\n");

                if (!clean) {
                    errorMsg.append("LIKELY CAUSE: Gradle skipped tests because results are cached.\n");
                    errorMsg.append("SOLUTION: Retry with clean=true to force fresh test run.\n\n");
                }

                errorMsg.append("Other possible causes:\n");
                errorMsg.append("- Another process is using port ").append(port).append("\n");
                errorMsg.append("- Gradle build failed (check gradleArgs='--info' for details)\n");
                errorMsg.append("- Test filter doesn't match any tests\n\n");

                // Include last lines of Gradle output for debugging
                String gradleOutput = getRecentOutput();
                if (!gradleOutput.isEmpty()) {
                    errorMsg.append("=== Last Gradle Output ===\n");
                    errorMsg.append(gradleOutput);
                }

                return ToolResult.error(errorMsg.toString());
            }

            // Attach to the debug port
            sb.append("Debug port available! Attaching debugger...\n\n");
            session.attachSocket("localhost", port);
            session.startEventThread();

            sb.append("=== Connected Successfully ===\n");
            sb.append("VM: ").append(session.getVm().name()).append("\n");
            sb.append("Version: ").append(session.getVm().version()).append("\n\n");
            sb.append("The test JVM is suspended. Set breakpoints and use 'resume' to start.\n");
            sb.append("Use 'debug_disconnect' when done (this will also terminate the Gradle process).\n");

            return ToolResult.success(sb.toString());

        } catch (Exception e) {
            if (gradleProcess != null) {
                gradleProcess.destroyForcibly();
            }
            return ToolResult.error("Failed to launch Gradle test: " + e.getMessage());
        }
    }

    private List<String> buildGradleCommand(File workDir, boolean useWrapper, boolean clean,
                                             String testFilter, String gradleArgs) {
        List<String> command = new ArrayList<>();

        // Determine Gradle executable
        if (useWrapper) {
            File gradlew = new File(workDir, System.getProperty("os.name").toLowerCase().contains("win")
                ? "gradlew.bat" : "gradlew");
            if (gradlew.exists()) {
                command.add(gradlew.getAbsolutePath());
            } else {
                command.add("gradle"); // Fallback to system Gradle
            }
        } else {
            command.add("gradle");
        }

        // Add clean task if requested (IMPORTANT: clears cached test results)
        if (clean) {
            command.add("clean");
        }

        // Add test task
        command.add("test");

        // Add debug-jvm flag
        command.add("--debug-jvm");

        // Add test filter if specified
        if (testFilter != null && !testFilter.isEmpty()) {
            if (!testFilter.startsWith("--tests")) {
                command.add("--tests");
            }
            command.add(testFilter);
        }

        // Add additional Gradle arguments
        if (gradleArgs != null && !gradleArgs.isEmpty()) {
            for (String arg : gradleArgs.split("\\s+")) {
                if (!arg.isEmpty()) {
                    command.add(arg);
                }
            }
        }

        return command;
    }

    private boolean waitForDebugPort(String host, int port, int timeoutSeconds, Process process) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);

        while (System.currentTimeMillis() < deadline) {
            // Check if process is still running
            if (!process.isAlive()) {
                return false; // Process died
            }

            // Check if port is available
            if (isPortOpen(host, port)) {
                return true;
            }

            try {
                Thread.sleep(PORT_CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    private boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 200);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private long getProcessId(Process process) {
        try {
            return process.pid();
        } catch (UnsupportedOperationException e) {
            return -1;
        }
    }

    /**
     * Start a thread to drain process output to prevent blocking.
     * Also captures output for error reporting.
     */
    private void startOutputDrain(Process process) {
        outputLines.clear();
        outputDrainRunning.set(true);

        outputDrainThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while (outputDrainRunning.get() && (line = reader.readLine()) != null) {
                    outputLines.add(line);
                    // Keep only last N lines to avoid memory issues
                    while (outputLines.size() > MAX_OUTPUT_LINES) {
                        outputLines.poll();
                    }
                }
            } catch (IOException e) {
                // Process closed or error - expected during shutdown
            }
        }, "gradle-output-drain");
        outputDrainThread.setDaemon(true);
        outputDrainThread.start();
    }

    /**
     * Stop the output drain thread.
     */
    private void stopOutputDrain() {
        outputDrainRunning.set(false);
        if (outputDrainThread != null) {
            outputDrainThread.interrupt();
            try {
                outputDrainThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Get recent output lines for error reporting.
     */
    private String getRecentOutput() {
        StringBuilder sb = new StringBuilder();
        for (String line : outputLines) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    /**
     * Get the Gradle process for cleanup.
     */
    public Process getGradleProcess() {
        return gradleProcess;
    }
}
