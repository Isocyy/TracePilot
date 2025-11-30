package com.nanik.tracepilot.tools.impl;

import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Tool to attach to a running JVM via socket.
 *
 * The target JVM must be started with:
 * -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=<port>
 *
 * Parameters:
 * - host (optional): Hostname to connect to (default: localhost)
 * - port (required): Port number to connect to
 * - waitForPort (optional): Wait for port to become available (default: false)
 * - waitTimeout (optional): Timeout in seconds when waiting for port (default: 60)
 */
public class DebugAttachSocketTool implements ToolHandler {

    private static final int DEFAULT_WAIT_TIMEOUT = 60;
    private static final int PORT_CHECK_INTERVAL_MS = 500;
    private static final int PORT_CHECK_SOCKET_TIMEOUT_MS = 200;

    private static final ToolDefinition DEFINITION = new ToolDefinition(
        "debug_attach_socket",
        "Attach to a JVM. Target must start with: java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005. " +
        "Use waitForPort=true to poll until port is available (eliminates race conditions). " +
        "For Gradle tests: use './gradlew test --debug-jvm' and increase timeout with 'test { timeout = Duration.ofMinutes(30) }' in build.gradle.",
        new SchemaBuilder()
            .addString("host", "Hostname to connect to (default: localhost)", false, "localhost")
            .addInteger("port", "Port number to connect to", true)
            .addBoolean("waitForPort", "Wait for port to become available before attaching (default: false)", false, false)
            .addInteger("waitTimeout", "Timeout in seconds when waiting for port (default: 60, max: 300)", false, DEFAULT_WAIT_TIMEOUT)
            .build()
    );

    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(McpRequest request) {
        String host = request.getStringParam("host");
        if (host == null || host.isEmpty()) {
            host = "localhost";
        }

        Integer port = request.getIntParam("port");
        if (port == null) {
            return ToolResult.error("Missing required parameter: port");
        }

        Boolean waitForPort = request.getBoolParam("waitForPort");
        if (waitForPort == null) {
            waitForPort = false;
        }

        Integer waitTimeout = request.getIntParam("waitTimeout");
        if (waitTimeout == null) {
            waitTimeout = DEFAULT_WAIT_TIMEOUT;
        }
        // Cap timeout at 5 minutes
        waitTimeout = Math.min(waitTimeout, 300);

        DebugSession session = DebugSession.getInstance();

        if (session.isConnected()) {
            return ToolResult.error("Already connected to a VM. Use debug_disconnect first.");
        }

        // Wait for port if requested
        if (waitForPort) {
            long waitedSeconds = waitForPortAvailable(host, port, waitTimeout);
            if (waitedSeconds < 0) {
                return ToolResult.error("Timeout waiting for port " + port + " to become available after " + waitTimeout + " seconds.");
            }
        }

        try {
            session.attachSocket(host, port);

            // Start event thread to process JDI events (breakpoints, steps, etc.)
            session.startEventThread();

            StringBuilder status = new StringBuilder();
            status.append("Attached to VM via socket.\n");
            status.append("Host: ").append(host).append("\n");
            status.append("Port: ").append(port).append("\n");
            status.append("VM: ").append(session.getVm().name()).append("\n");
            status.append("Version: ").append(session.getVm().version());

            return ToolResult.success(status.toString());
        } catch (Exception e) {
            return ToolResult.error("Failed to attach to VM: " + e.getMessage());
        }
    }

    /**
     * Wait for a port to become available.
     *
     * @param host The host to check
     * @param port The port to check
     * @param timeoutSeconds Maximum time to wait in seconds
     * @return Number of seconds waited, or -1 if timeout
     */
    private long waitForPortAvailable(String host, int port, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        long deadline = startTime + (timeoutSeconds * 1000L);

        while (System.currentTimeMillis() < deadline) {
            if (isPortOpen(host, port)) {
                return (System.currentTimeMillis() - startTime) / 1000;
            }
            try {
                Thread.sleep(PORT_CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }
        return -1;
    }

    /**
     * Check if a port is open and accepting connections.
     */
    private boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PORT_CHECK_SOCKET_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

