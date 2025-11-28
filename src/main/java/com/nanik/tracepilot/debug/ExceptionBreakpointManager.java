package com.nanik.tracepilot.debug;

import com.sun.jdi.*;
import com.sun.jdi.request.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages exception breakpoints.
 */
public class ExceptionBreakpointManager {

    private static final ExceptionBreakpointManager INSTANCE = new ExceptionBreakpointManager();

    private final Map<String, ExceptionBreakpointInfo> exceptionBreakpoints = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    private ExceptionBreakpointManager() {}

    public static ExceptionBreakpointManager getInstance() {
        return INSTANCE;
    }

    /**
     * Set an exception breakpoint for a specific exception class.
     * @param exceptionClassName Fully qualified exception class name (or "*" for all)
     * @param catchCaught Break on caught exceptions
     * @param catchUncaught Break on uncaught exceptions
     */
    public String setExceptionBreakpoint(String exceptionClassName, 
                                          boolean catchCaught, boolean catchUncaught) {
        VirtualMachine vm = DebugSession.getInstance().getVm();
        if (vm == null) {
            throw new IllegalStateException("Not connected to a VM");
        }

        if (!catchCaught && !catchUncaught) {
            throw new IllegalArgumentException("Must catch at least caught or uncaught exceptions");
        }

        // Check for existing breakpoint on same exception
        for (ExceptionBreakpointInfo info : exceptionBreakpoints.values()) {
            if (Objects.equals(info.getExceptionClassName(), exceptionClassName) &&
                info.isCatchCaught() == catchCaught &&
                info.isCatchUncaught() == catchUncaught) {
                return info.getId();
            }
        }

        ReferenceType exceptionType = null;
        boolean isAllExceptions = exceptionClassName == null || 
                                  exceptionClassName.equals("*") ||
                                  exceptionClassName.isEmpty();

        if (!isAllExceptions) {
            // Find the exception class
            List<ReferenceType> classes = vm.classesByName(exceptionClassName);
            if (classes.isEmpty()) {
                throw new IllegalArgumentException("Exception class not found: " + exceptionClassName);
            }
            exceptionType = classes.get(0);

            // Verify it's a Throwable
            if (!isThrowable(exceptionType)) {
                throw new IllegalArgumentException(exceptionClassName + " is not a Throwable");
            }
        }

        EventRequestManager erm = vm.eventRequestManager();
        ExceptionRequest request = erm.createExceptionRequest(exceptionType, catchCaught, catchUncaught);
        request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        request.enable();

        String id = "ex-" + idCounter.getAndIncrement();
        String storedClassName = isAllExceptions ? "*" : exceptionClassName;
        ExceptionBreakpointInfo info = new ExceptionBreakpointInfo(
            id, storedClassName, catchCaught, catchUncaught, request);
        exceptionBreakpoints.put(id, info);
        request.putProperty("exceptionBreakpointId", id);

        return id;
    }

    private boolean isThrowable(ReferenceType type) {
        if (type == null) return false;
        if (type.name().equals("java.lang.Throwable")) return true;
        
        if (type instanceof ClassType) {
            ClassType classType = (ClassType) type;
            ClassType superclass = classType.superclass();
            return isThrowable(superclass);
        }
        return false;
    }

    public boolean removeExceptionBreakpoint(String breakpointId) {
        ExceptionBreakpointInfo info = exceptionBreakpoints.remove(breakpointId);
        if (info == null) return false;

        ExceptionRequest request = info.getRequest();
        if (request != null) {
            try {
                VirtualMachine vm = DebugSession.getInstance().getVm();
                if (vm != null) {
                    vm.eventRequestManager().deleteEventRequest(request);
                }
            } catch (Exception e) {}
        }
        return true;
    }

    public boolean enableExceptionBreakpoint(String breakpointId) {
        ExceptionBreakpointInfo info = exceptionBreakpoints.get(breakpointId);
        if (info == null) return false;
        info.setEnabled(true);
        if (info.getRequest() != null) info.getRequest().enable();
        return true;
    }

    public boolean disableExceptionBreakpoint(String breakpointId) {
        ExceptionBreakpointInfo info = exceptionBreakpoints.get(breakpointId);
        if (info == null) return false;
        info.setEnabled(false);
        if (info.getRequest() != null) info.getRequest().disable();
        return true;
    }

    public List<ExceptionBreakpointInfo> getAllExceptionBreakpoints() {
        return new ArrayList<>(exceptionBreakpoints.values());
    }

    public ExceptionBreakpointInfo getExceptionBreakpoint(String id) {
        return exceptionBreakpoints.get(id);
    }

    public void clearAll() {
        VirtualMachine vm = DebugSession.getInstance().getVm();
        if (vm != null) {
            for (ExceptionBreakpointInfo info : exceptionBreakpoints.values()) {
                if (info.getRequest() != null) {
                    try {
                        vm.eventRequestManager().deleteEventRequest(info.getRequest());
                    } catch (Exception e) {}
                }
            }
        }
        exceptionBreakpoints.clear();
    }
}

