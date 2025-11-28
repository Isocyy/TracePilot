package com.nanik.tracepilot.debug;

import com.sun.jdi.*;
import com.sun.jdi.request.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages method entry/exit breakpoints.
 */
public class MethodBreakpointManager {

    private static final MethodBreakpointManager INSTANCE = new MethodBreakpointManager();

    private final Map<String, MethodBreakpointInfo> methodBreakpoints = new ConcurrentHashMap<>();
    private final Map<String, List<DeferredMethodBreakpoint>> deferredBreakpoints = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    private MethodBreakpointManager() {}

    public static MethodBreakpointManager getInstance() {
        return INSTANCE;
    }

    /**
     * Set a method entry breakpoint.
     * @param className Fully qualified class name
     * @param methodName Method name (use "*" for all methods in the class)
     */
    public String setMethodEntryBreakpoint(String className, String methodName) {
        return setMethodBreakpoint(className, methodName, MethodBreakpointInfo.MethodBreakpointType.ENTRY);
    }

    /**
     * Set a method exit breakpoint.
     */
    public String setMethodExitBreakpoint(String className, String methodName) {
        return setMethodBreakpoint(className, methodName, MethodBreakpointInfo.MethodBreakpointType.EXIT);
    }

    private String setMethodBreakpoint(String className, String methodName, 
                                        MethodBreakpointInfo.MethodBreakpointType type) {
        VirtualMachine vm = DebugSession.getInstance().getVm();
        if (vm == null) {
            throw new IllegalStateException("Not connected to a VM");
        }

        List<ReferenceType> classes = vm.classesByName(className);

        if (classes.isEmpty()) {
            return createDeferredBreakpoint(className, methodName, type);
        }

        ReferenceType refType = classes.get(0);
        
        // Validate method exists (unless wildcard)
        if (!"*".equals(methodName)) {
            List<Method> methods = refType.methodsByName(methodName);
            if (methods.isEmpty()) {
                throw new IllegalArgumentException("Method '" + methodName + "' not found in " + className);
            }
        }

        // Check for existing
        for (MethodBreakpointInfo info : methodBreakpoints.values()) {
            if (info.getClassName().equals(className) && 
                info.getMethodName().equals(methodName) &&
                info.getType() == type) {
                return info.getId();
            }
        }

        EventRequestManager erm = vm.eventRequestManager();
        EventRequest request;
        String prefix;

        if (type == MethodBreakpointInfo.MethodBreakpointType.ENTRY) {
            MethodEntryRequest entryReq = erm.createMethodEntryRequest();
            entryReq.addClassFilter(refType);
            request = entryReq;
            prefix = "me-";
        } else {
            MethodExitRequest exitReq = erm.createMethodExitRequest();
            exitReq.addClassFilter(refType);
            request = exitReq;
            prefix = "mx-";
        }

        request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        request.enable();

        String id = prefix + idCounter.getAndIncrement();
        MethodBreakpointInfo info = new MethodBreakpointInfo(id, className, methodName, type, request);
        methodBreakpoints.put(id, info);
        request.putProperty("methodBreakpointId", id);
        request.putProperty("methodName", methodName);

        return id;
    }

    private String createDeferredBreakpoint(String className, String methodName,
                                             MethodBreakpointInfo.MethodBreakpointType type) {
        String prefix = (type == MethodBreakpointInfo.MethodBreakpointType.ENTRY) ? "me-" : "mx-";
        String id = prefix + idCounter.getAndIncrement();

        MethodBreakpointInfo info = new MethodBreakpointInfo(id, className, methodName, type, null);
        info.setPending(true);
        methodBreakpoints.put(id, info);

        DeferredMethodBreakpoint deferred = new DeferredMethodBreakpoint(id, className, methodName, type);
        deferredBreakpoints.computeIfAbsent(className, k -> new ArrayList<>()).add(deferred);

        return id;
    }

    public boolean removeMethodBreakpoint(String breakpointId) {
        MethodBreakpointInfo info = methodBreakpoints.remove(breakpointId);
        if (info == null) return false;

        EventRequest request = info.getRequest();
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

    public List<MethodBreakpointInfo> getAllMethodBreakpoints() {
        return new ArrayList<>(methodBreakpoints.values());
    }

    public MethodBreakpointInfo getMethodBreakpoint(String id) {
        return methodBreakpoints.get(id);
    }

    public void clearAll() {
        VirtualMachine vm = DebugSession.getInstance().getVm();
        if (vm != null) {
            for (MethodBreakpointInfo info : methodBreakpoints.values()) {
                if (info.getRequest() != null) {
                    try {
                        vm.eventRequestManager().deleteEventRequest(info.getRequest());
                    } catch (Exception e) {}
                }
            }
        }
        methodBreakpoints.clear();
        deferredBreakpoints.clear();
    }

    /**
     * Called when a class is prepared - resolve any deferred method breakpoints.
     */
    public void onClassPrepare(ReferenceType refType) {
        String className = refType.name();
        List<DeferredMethodBreakpoint> deferred = deferredBreakpoints.remove(className);
        if (deferred == null || deferred.isEmpty()) return;

        VirtualMachine vm = DebugSession.getInstance().getVm();
        if (vm == null) return;

        EventRequestManager erm = vm.eventRequestManager();

        for (DeferredMethodBreakpoint d : deferred) {
            try {
                EventRequest request;

                if (d.type == MethodBreakpointInfo.MethodBreakpointType.ENTRY) {
                    MethodEntryRequest entryReq = erm.createMethodEntryRequest();
                    entryReq.addClassFilter(refType);
                    request = entryReq;
                } else {
                    MethodExitRequest exitReq = erm.createMethodExitRequest();
                    exitReq.addClassFilter(refType);
                    request = exitReq;
                }

                request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                request.enable();
                request.putProperty("methodBreakpointId", d.breakpointId);
                request.putProperty("methodName", d.methodName);

                MethodBreakpointInfo info = methodBreakpoints.get(d.breakpointId);
                if (info != null) {
                    info.setRequest(request);
                    info.setPending(false);
                }
            } catch (Exception e) {
                // Cannot set method breakpoint
            }
        }
    }

    private static class DeferredMethodBreakpoint {
        final String breakpointId;
        final String className;
        final String methodName;
        final MethodBreakpointInfo.MethodBreakpointType type;

        DeferredMethodBreakpoint(String breakpointId, String className,
                                  String methodName, MethodBreakpointInfo.MethodBreakpointType type) {
            this.breakpointId = breakpointId;
            this.className = className;
            this.methodName = methodName;
            this.type = type;
        }
    }
}

