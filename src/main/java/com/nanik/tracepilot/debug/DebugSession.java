package com.nanik.tracepilot.debug;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;

import java.io.IOException;
import java.util.Map;

/**
 * Manages the debug session with a target JVM.
 *
 * Wraps JDI's VirtualMachine and provides higher-level operations.
 * This is a singleton - only one debug session at a time.
 */
public class DebugSession {

    private static DebugSession instance;

    private VirtualMachine vm;
    private Process vmProcess; // Only set for launched VMs
    private ConnectionType connectionType;
    private String connectionDetails;
    private long connectedAt;
    private Event lastEvent; // Last debug event received
    private Thread eventThread; // Background thread for processing events
    private volatile boolean eventThreadRunning = false;

    // Stop reason tracking for async debugging
    private volatile StopReason lastStopReason = StopReason.none();
    private final Object stopLock = new Object();

    public enum ConnectionType {
        LAUNCH,
        ATTACH_SOCKET,
        ATTACH_PID,
        NONE
    }

    private DebugSession() {
        this.connectionType = ConnectionType.NONE;
    }
    
    public static synchronized DebugSession getInstance() {
        if (instance == null) {
            instance = new DebugSession();
        }
        return instance;
    }
    
    /**
     * Check if connected to a VM.
     */
    public boolean isConnected() {
        return vm != null;
    }
    
    /**
     * Get the current VirtualMachine.
     */
    public VirtualMachine getVm() {
        return vm;
    }
    
    /**
     * Launch a new JVM with debugging enabled.
     * Uses manual process spawning with socket attach to avoid hostname resolution issues.
     */
    public void launch(String mainClass, String classpath, String options, boolean suspend)
            throws IOException, IllegalConnectorArgumentsException {

        if (isConnected()) {
            throw new IllegalStateException("Already connected to a VM. Disconnect first.");
        }

        // Find a free port for debugging
        int port = findFreePort();

        // Build the command to launch JVM with JDWP agent
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(System.getProperty("java.home") + "/bin/java");

        // Add debug agent using localhost to avoid hostname resolution issues
        String suspendFlag = suspend ? "y" : "n";
        command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + suspendFlag + ",address=127.0.0.1:" + port);

        // Add classpath
        if (classpath != null && !classpath.isEmpty()) {
            command.add("-cp");
            command.add(classpath);
        }

        // Add any extra options
        if (options != null && !options.isEmpty()) {
            for (String opt : options.split("\\s+")) {
                if (!opt.isEmpty()) {
                    command.add(opt);
                }
            }
        }

        // Add main class
        command.add(mainClass);

        // Start the process
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        vmProcess = pb.start();

        // Start output drainers
        startOutputDrainer(vmProcess);

        // Wait a bit for the JVM to start listening
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Check if process is still running
        if (!vmProcess.isAlive()) {
            String stderr = drainStream(vmProcess.getErrorStream());
            throw new IOException("Process exited immediately. Stderr: " + stderr);
        }

        // Now attach via socket
        try {
            attachSocket("127.0.0.1", port);
            connectionType = ConnectionType.LAUNCH;
            connectionDetails = "Launched: " + mainClass + " (port " + port + ")";
        } catch (Exception e) {
            vmProcess.destroyForcibly();
            vmProcess = null;
            throw new IOException("Failed to attach to launched VM: " + e.getMessage(), e);
        }
    }

    /**
     * Find a free port for debugging.
     */
    private int findFreePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Start background threads to drain process stdout/stderr.
     */
    private void startOutputDrainer(Process process) {
        Thread stderrDrainer = new Thread(() -> drainStream(process.getErrorStream()), "stderr-drainer");
        Thread stdoutDrainer = new Thread(() -> drainStream(process.getInputStream()), "stdout-drainer");
        stderrDrainer.setDaemon(true);
        stdoutDrainer.setDaemon(true);
        stderrDrainer.start();
        stdoutDrainer.start();
    }

    /**
     * Drain an input stream and return its contents.
     */
    private String drainStream(java.io.InputStream stream) {
        try {
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = stream.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, len));
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return "";
        }
    }
    
    /**
     * Attach to a running JVM via socket.
     */
    public void attachSocket(String host, int port) 
            throws IOException, IllegalConnectorArgumentsException {
        
        if (isConnected()) {
            throw new IllegalStateException("Already connected to a VM. Disconnect first.");
        }
        
        AttachingConnector connector = findSocketConnector();
        if (connector == null) {
            throw new IOException("Socket attaching connector not available");
        }
        
        Map<String, Connector.Argument> args = connector.defaultArguments();
        args.get("hostname").setValue(host);
        args.get("port").setValue(String.valueOf(port));
        
        vm = connector.attach(args);
        vmProcess = null;
        connectionType = ConnectionType.ATTACH_SOCKET;
        connectionDetails = "Attached: " + host + ":" + port;
        connectedAt = System.currentTimeMillis();
    }
    
    /**
     * Attach to a running JVM via process ID.
     */
    public void attachPid(String pid) 
            throws IOException, IllegalConnectorArgumentsException {
        
        if (isConnected()) {
            throw new IllegalStateException("Already connected to a VM. Disconnect first.");
        }
        
        AttachingConnector connector = findPidConnector();
        if (connector == null) {
            throw new IOException("Process attaching connector not available");
        }
        
        Map<String, Connector.Argument> args = connector.defaultArguments();
        args.get("pid").setValue(pid);
        
        vm = connector.attach(args);
        vmProcess = null;
        connectionType = ConnectionType.ATTACH_PID;
        connectionDetails = "Attached PID: " + pid;
        connectedAt = System.currentTimeMillis();
    }
    
    /**
     * Disconnect from the VM.
     */
    public void disconnect() {
        stopEventThread();

        if (vm != null) {
            try {
                vm.dispose();
            } catch (Exception e) {
                // Ignore errors during disconnect
            }
            vm = null;
        }
        if (vmProcess != null) {
            vmProcess.destroy();
            vmProcess = null;
        }
        connectionType = ConnectionType.NONE;
        connectionDetails = null;
        connectedAt = 0;

        // Reset managers
        EventMonitorManager.getInstance().reset();
        BreakpointManager.getInstance().clearAll();
        WatchpointManager.getInstance().clearAll();
        MethodBreakpointManager.getInstance().clearAll();
        ExceptionBreakpointManager.getInstance().clearAll();
    }

    /**
     * Start the background event processing thread.
     */
    public void startEventThread() {
        if (eventThreadRunning || vm == null) {
            return;
        }

        eventThreadRunning = true;
        eventThread = new Thread(() -> {
            EventQueue queue = vm.eventQueue();
            while (eventThreadRunning && vm != null) {
                try {
                    EventSet eventSet = queue.remove(100); // 100ms timeout
                    if (eventSet != null) {
                        boolean shouldResume = true;

                        for (Event event : eventSet) {
                            // Check if this is a stop event (should NOT auto-resume)
                            if (isStopEvent(event)) {
                                StopReason reason = StopReason.fromEvent(event);
                                if (reason != null) {
                                    setStopReason(reason);
                                    shouldResume = false;
                                }
                            }
                            processEvent(event);
                        }

                        // Only auto-resume for monitoring events (class prepare, thread start, etc.)
                        // Do NOT resume for stop events (breakpoint, step, exception, watchpoint)
                        if (shouldResume) {
                            eventSet.resume();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (VMDisconnectedException e) {
                    setStopReason(StopReason.vmDisconnect());
                    break;
                } catch (Exception e) {
                    // Log and continue
                }
            }
        }, "TracePilot-EventProcessor");
        eventThread.setDaemon(true);
        eventThread.start();
    }

    /**
     * Determine if an event is a "stop event" that should suspend the VM.
     * Stop events: breakpoint, step, exception, watchpoint, method entry/exit, VM start
     * Monitor events (auto-resume): class prepare/unload, thread start/death, monitor contention
     *
     * Note: VMStartEvent is treated as a stop event because when attaching to a suspended VM,
     * we want to give the user a chance to set breakpoints before the VM runs.
     */
    private boolean isStopEvent(Event event) {
        return event instanceof BreakpointEvent ||
               event instanceof StepEvent ||
               event instanceof ExceptionEvent ||
               event instanceof AccessWatchpointEvent ||
               event instanceof ModificationWatchpointEvent ||
               event instanceof MethodEntryEvent ||
               event instanceof MethodExitEvent ||
               event instanceof VMStartEvent;
    }

    /**
     * Stop the background event processing thread.
     */
    public void stopEventThread() {
        eventThreadRunning = false;
        if (eventThread != null) {
            eventThread.interrupt();
            try {
                eventThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            eventThread = null;
        }
    }

    /**
     * Process a single JDI event.
     */
    private void processEvent(Event event) {
        // Store as last event
        setLastEvent(event);

        // Capture for monitoring
        EventMonitorManager.getInstance().captureEvent(event);

        // Handle specific event types
        if (event instanceof ClassPrepareEvent) {
            ClassPrepareEvent cpe = (ClassPrepareEvent) event;
            // Activate pending breakpoints for this class
            BreakpointManager.getInstance().onClassPrepare(cpe.referenceType());
            WatchpointManager.getInstance().onClassPrepare(cpe.referenceType());
            MethodBreakpointManager.getInstance().onClassPrepare(cpe.referenceType());
        } else if (event instanceof StepEvent) {
            // Delete the step request after it fires (JDI only allows one per thread)
            StepEvent stepEvent = (StepEvent) event;
            vm.eventRequestManager().deleteEventRequest(stepEvent.request());
        }
    }

    /**
     * Find the socket attaching connector.
     */
    private AttachingConnector findSocketConnector() {
        for (Connector connector : Bootstrap.virtualMachineManager().allConnectors()) {
            if (connector instanceof AttachingConnector &&
                connector.name().contains("SocketAttach")) {
                return (AttachingConnector) connector;
            }
        }
        return null;
    }

    /**
     * Find the process attaching connector.
     */
    private AttachingConnector findPidConnector() {
        for (Connector connector : Bootstrap.virtualMachineManager().allConnectors()) {
            if (connector instanceof AttachingConnector &&
                connector.name().contains("ProcessAttach")) {
                return (AttachingConnector) connector;
            }
        }
        return null;
    }

    // Getters
    public ConnectionType getConnectionType() {
        return connectionType;
    }

    public String getConnectionDetails() {
        return connectionDetails;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public Process getVmProcess() {
        return vmProcess;
    }

    public Event getLastEvent() {
        return lastEvent;
    }

    public void setLastEvent(Event event) {
        this.lastEvent = event;
    }

    // Stop reason tracking methods

    /**
     * Get the last stop reason.
     */
    public StopReason getLastStopReason() {
        return lastStopReason;
    }

    /**
     * Set the stop reason and notify waiters.
     */
    public void setStopReason(StopReason reason) {
        synchronized (stopLock) {
            this.lastStopReason = reason;
            stopLock.notifyAll();
        }
    }

    /**
     * Clear the stop reason (called when resuming).
     */
    public void clearStopReason() {
        this.lastStopReason = StopReason.none();
    }

    /**
     * Check if the VM is currently stopped (any thread suspended due to debug event).
     */
    public boolean isStopped() {
        return lastStopReason != null && lastStopReason.isStopped();
    }

    /**
     * Wait for the VM to stop, with timeout.
     * @param timeoutMs timeout in milliseconds
     * @return StopReason if stopped, or StopReason.none() if timeout
     */
    public StopReason waitForStop(long timeoutMs) {
        synchronized (stopLock) {
            // Already stopped?
            if (lastStopReason != null && lastStopReason.isStopped()) {
                return lastStopReason;
            }

            // Check if VM is even connected
            if (vm == null) {
                return StopReason.vmDisconnect();
            }

            long startTime = System.currentTimeMillis();
            long remaining = timeoutMs;

            while (remaining > 0) {
                try {
                    stopLock.wait(remaining);

                    // Check if stopped
                    if (lastStopReason != null && lastStopReason.isStopped()) {
                        return lastStopReason;
                    }

                    // Check if VM disconnected
                    if (vm == null) {
                        return StopReason.vmDisconnect();
                    }

                    remaining = timeoutMs - (System.currentTimeMillis() - startTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return StopReason.none();
                }
            }

            // Timeout - return none (still running)
            return StopReason.none();
        }
    }
}

