package com.nanik.tracepilot.debug;

import com.sun.jdi.*;
import com.sun.jdi.request.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages breakpoints with unique IDs and tracks their state.
 * Supports deferred breakpoints for classes not yet loaded.
 */
public class BreakpointManager {

    private static final BreakpointManager INSTANCE = new BreakpointManager();

    private final Map<String, BreakpointInfo> breakpoints = new ConcurrentHashMap<>();
    private final Map<String, List<DeferredBreakpoint>> deferredBreakpoints = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);
    private ClassPrepareRequest classPrepareRequest;

    private BreakpointManager() {}

    public static BreakpointManager getInstance() {
        return INSTANCE;
    }

    /**
     * Set a line breakpoint. If the class is not yet loaded, creates a deferred breakpoint.
     * @return breakpoint ID and status message
     */
    public String setLineBreakpoint(String className, int lineNumber) throws AbsentInformationException {
        VirtualMachine vm = DebugSession.getInstance().getVm();
        if (vm == null) {
            throw new IllegalStateException("Not connected to a VM");
        }

        // Find the class
        List<ReferenceType> classes = vm.classesByName(className);

        if (classes.isEmpty()) {
            // Class not yet loaded - create deferred breakpoint
            return createDeferredBreakpoint(vm, className, lineNumber);
        }

        ReferenceType refType = classes.get(0);

        // Find locations at the line
        List<Location> locations = refType.locationsOfLine(lineNumber);
        if (locations.isEmpty()) {
            throw new IllegalArgumentException("No code at line " + lineNumber + " in " + className);
        }

        Location location = locations.get(0);

        // Check if breakpoint already exists at this location
        for (BreakpointInfo info : breakpoints.values()) {
            if (info.getRequest() != null && info.getRequest().location().equals(location)) {
                return info.getId(); // Return existing breakpoint ID
            }
        }

        // Create the breakpoint request
        EventRequestManager erm = vm.eventRequestManager();
        BreakpointRequest request = erm.createBreakpointRequest(location);
        request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        request.enable();

        // Generate ID and store
        String id = "bp-" + idCounter.getAndIncrement();
        BreakpointInfo info = new BreakpointInfo(id, className, lineNumber, request);
        breakpoints.put(id, info);
        
        // Store ID in request for lookup
        request.putProperty("breakpointId", id);
        
        return id;
    }
    
    /**
     * Remove a breakpoint by ID.
     */
    public boolean removeBreakpoint(String breakpointId) {
        BreakpointInfo info = breakpoints.remove(breakpointId);
        if (info == null) {
            return false;
        }
        
        BreakpointRequest request = info.getRequest();
        if (request != null) {
            try {
                VirtualMachine vm = DebugSession.getInstance().getVm();
                if (vm != null) {
                    vm.eventRequestManager().deleteEventRequest(request);
                }
            } catch (Exception e) {
                // Ignore - VM might be disconnected
            }
        }
        return true;
    }
    
    /**
     * Enable a breakpoint.
     */
    public boolean enableBreakpoint(String breakpointId) {
        BreakpointInfo info = breakpoints.get(breakpointId);
        if (info == null) {
            return false;
        }
        info.setEnabled(true);
        if (info.getRequest() != null) {
            info.getRequest().enable();
        }
        return true;
    }
    
    /**
     * Disable a breakpoint.
     */
    public boolean disableBreakpoint(String breakpointId) {
        BreakpointInfo info = breakpoints.get(breakpointId);
        if (info == null) {
            return false;
        }
        info.setEnabled(false);
        if (info.getRequest() != null) {
            info.getRequest().disable();
        }
        return true;
    }
    
    /**
     * Get all breakpoints.
     */
    public List<BreakpointInfo> getAllBreakpoints() {
        return new ArrayList<>(breakpoints.values());
    }
    
    /**
     * Get breakpoint by ID.
     */
    public BreakpointInfo getBreakpoint(String breakpointId) {
        return breakpoints.get(breakpointId);
    }
    
    /**
     * Clear all breakpoints.
     */
    public void clearAll() {
        VirtualMachine vm = DebugSession.getInstance().getVm();
        if (vm != null) {
            for (BreakpointInfo info : breakpoints.values()) {
                if (info.getRequest() != null) {
                    try {
                        vm.eventRequestManager().deleteEventRequest(info.getRequest());
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }
        breakpoints.clear();
        deferredBreakpoints.clear();
    }

    /**
     * Create a deferred breakpoint for a class not yet loaded.
     */
    private String createDeferredBreakpoint(VirtualMachine vm, String className, int lineNumber) {
        String id = "bp-" + idCounter.getAndIncrement();

        // Create a pending breakpoint info (no request yet)
        BreakpointInfo info = new BreakpointInfo(id, className, lineNumber, null);
        info.setPending(true);
        breakpoints.put(id, info);

        // Store deferred info
        DeferredBreakpoint deferred = new DeferredBreakpoint(id, className, lineNumber);
        deferredBreakpoints.computeIfAbsent(className, k -> new ArrayList<>()).add(deferred);

        // Set up class prepare request if not already done
        setupClassPrepareRequest(vm);

        return id;
    }

    /**
     * Set up a class prepare request to handle deferred breakpoints.
     */
    private void setupClassPrepareRequest(VirtualMachine vm) {
        if (classPrepareRequest == null) {
            EventRequestManager erm = vm.eventRequestManager();
            classPrepareRequest = erm.createClassPrepareRequest();
            classPrepareRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            classPrepareRequest.enable();
        }
    }

    /**
     * Called when a class is prepared - resolve any deferred breakpoints.
     */
    public void onClassPrepare(ReferenceType refType) {
        String className = refType.name();
        List<DeferredBreakpoint> deferred = deferredBreakpoints.remove(className);
        if (deferred == null || deferred.isEmpty()) {
            return;
        }

        VirtualMachine vm = DebugSession.getInstance().getVm();
        if (vm == null) {
            return;
        }

        for (DeferredBreakpoint d : deferred) {
            try {
                List<Location> locations = refType.locationsOfLine(d.lineNumber);
                if (!locations.isEmpty()) {
                    Location location = locations.get(0);
                    EventRequestManager erm = vm.eventRequestManager();
                    BreakpointRequest request = erm.createBreakpointRequest(location);
                    request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                    request.enable();
                    request.putProperty("breakpointId", d.breakpointId);

                    // Update the breakpoint info
                    BreakpointInfo info = breakpoints.get(d.breakpointId);
                    if (info != null) {
                        info.setRequest(request);
                        info.setPending(false);
                    }
                }
            } catch (AbsentInformationException e) {
                // Cannot set breakpoint - no line info
            }
        }
    }

    /**
     * Get count of pending (deferred) breakpoints.
     */
    public int getPendingCount() {
        int count = 0;
        for (BreakpointInfo info : breakpoints.values()) {
            if (info.isPending()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Find breakpoint ID by location (for stop reason tracking).
     */
    public String findBreakpointIdByLocation(Location location) {
        for (BreakpointInfo info : breakpoints.values()) {
            if (info.getRequest() != null && info.getRequest().location().equals(location)) {
                return info.getId();
            }
        }
        return null;
    }

    /**
     * Holds info about a deferred breakpoint.
     */
    private static class DeferredBreakpoint {
        final String breakpointId;
        final String className;
        final int lineNumber;

        DeferredBreakpoint(String breakpointId, String className, int lineNumber) {
            this.breakpointId = breakpointId;
            this.className = className;
            this.lineNumber = lineNumber;
        }
    }
}

