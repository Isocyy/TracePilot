package com.nanik.tracepilot.debug;

import com.sun.jdi.*;
import com.sun.jdi.request.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages watchpoints (field access/modification breakpoints).
 * Supports deferred watchpoints for classes not yet loaded.
 */
public class WatchpointManager {

    private static final WatchpointManager INSTANCE = new WatchpointManager();

    private final Map<String, WatchpointInfo> watchpoints = new ConcurrentHashMap<>();
    private final Map<String, List<DeferredWatchpoint>> deferredWatchpoints = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    private WatchpointManager() {}

    public static WatchpointManager getInstance() {
        return INSTANCE;
    }

    /**
     * Set a field access watchpoint.
     */
    public String setAccessWatchpoint(String className, String fieldName) {
        return setWatchpoint(className, fieldName, WatchpointInfo.WatchpointType.ACCESS);
    }

    /**
     * Set a field modification watchpoint.
     */
    public String setModificationWatchpoint(String className, String fieldName) {
        return setWatchpoint(className, fieldName, WatchpointInfo.WatchpointType.MODIFICATION);
    }

    private String setWatchpoint(String className, String fieldName, WatchpointInfo.WatchpointType type) {
        VirtualMachine vm = DebugSession.getInstance().getVm();
        if (vm == null) {
            throw new IllegalStateException("Not connected to a VM");
        }

        List<ReferenceType> classes = vm.classesByName(className);

        if (classes.isEmpty()) {
            return createDeferredWatchpoint(vm, className, fieldName, type);
        }

        ReferenceType refType = classes.get(0);
        Field field = refType.fieldByName(fieldName);
        
        if (field == null) {
            throw new IllegalArgumentException("Field '" + fieldName + "' not found in " + className);
        }

        // Check for existing watchpoint
        for (WatchpointInfo info : watchpoints.values()) {
            if (info.getClassName().equals(className) && 
                info.getFieldName().equals(fieldName) &&
                info.getType() == type) {
                return info.getId();
            }
        }

        EventRequestManager erm = vm.eventRequestManager();
        EventRequest request;
        String prefix;

        if (type == WatchpointInfo.WatchpointType.ACCESS) {
            request = erm.createAccessWatchpointRequest(field);
            prefix = "wa-";
        } else {
            request = erm.createModificationWatchpointRequest(field);
            prefix = "wm-";
        }

        request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        request.enable();

        String id = prefix + idCounter.getAndIncrement();
        WatchpointInfo info = new WatchpointInfo(id, className, fieldName, type, request);
        watchpoints.put(id, info);
        request.putProperty("watchpointId", id);

        return id;
    }

    private String createDeferredWatchpoint(VirtualMachine vm, String className, 
                                             String fieldName, WatchpointInfo.WatchpointType type) {
        String prefix = (type == WatchpointInfo.WatchpointType.ACCESS) ? "wa-" : "wm-";
        String id = prefix + idCounter.getAndIncrement();

        WatchpointInfo info = new WatchpointInfo(id, className, fieldName, type, null);
        info.setPending(true);
        watchpoints.put(id, info);

        DeferredWatchpoint deferred = new DeferredWatchpoint(id, className, fieldName, type);
        deferredWatchpoints.computeIfAbsent(className, k -> new ArrayList<>()).add(deferred);

        return id;
    }

    public boolean removeWatchpoint(String watchpointId) {
        WatchpointInfo info = watchpoints.remove(watchpointId);
        if (info == null) {
            return false;
        }

        EventRequest request = info.getRequest();
        if (request != null) {
            try {
                VirtualMachine vm = DebugSession.getInstance().getVm();
                if (vm != null) {
                    vm.eventRequestManager().deleteEventRequest(request);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        return true;
    }

    public boolean enableWatchpoint(String watchpointId) {
        WatchpointInfo info = watchpoints.get(watchpointId);
        if (info == null) return false;
        info.setEnabled(true);
        if (info.getRequest() != null) info.getRequest().enable();
        return true;
    }

    public boolean disableWatchpoint(String watchpointId) {
        WatchpointInfo info = watchpoints.get(watchpointId);
        if (info == null) return false;
        info.setEnabled(false);
        if (info.getRequest() != null) info.getRequest().disable();
        return true;
    }

    public List<WatchpointInfo> getAllWatchpoints() {
        return new ArrayList<>(watchpoints.values());
    }

    public WatchpointInfo getWatchpoint(String watchpointId) {
        return watchpoints.get(watchpointId);
    }

    public void clearAll() {
        VirtualMachine vm = DebugSession.getInstance().getVm();
        if (vm != null) {
            for (WatchpointInfo info : watchpoints.values()) {
                if (info.getRequest() != null) {
                    try {
                        vm.eventRequestManager().deleteEventRequest(info.getRequest());
                    } catch (Exception e) {}
                }
            }
        }
        watchpoints.clear();
        deferredWatchpoints.clear();
    }

    /**
     * Called when a class is prepared - resolve any deferred watchpoints.
     */
    public void onClassPrepare(ReferenceType refType) {
        String className = refType.name();
        List<DeferredWatchpoint> deferred = deferredWatchpoints.remove(className);
        if (deferred == null || deferred.isEmpty()) return;

        VirtualMachine vm = DebugSession.getInstance().getVm();
        if (vm == null) return;

        for (DeferredWatchpoint d : deferred) {
            Field field = refType.fieldByName(d.fieldName);
            if (field == null) continue;

            try {
                EventRequestManager erm = vm.eventRequestManager();
                EventRequest request;

                if (d.type == WatchpointInfo.WatchpointType.ACCESS) {
                    request = erm.createAccessWatchpointRequest(field);
                } else {
                    request = erm.createModificationWatchpointRequest(field);
                }

                request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                request.enable();
                request.putProperty("watchpointId", d.watchpointId);

                WatchpointInfo info = watchpoints.get(d.watchpointId);
                if (info != null) {
                    info.setRequest(request);
                    info.setPending(false);
                }
            } catch (Exception e) {
                // Cannot set watchpoint
            }
        }
    }

    private static class DeferredWatchpoint {
        final String watchpointId;
        final String className;
        final String fieldName;
        final WatchpointInfo.WatchpointType type;

        DeferredWatchpoint(String watchpointId, String className,
                          String fieldName, WatchpointInfo.WatchpointType type) {
            this.watchpointId = watchpointId;
            this.className = className;
            this.fieldName = fieldName;
            this.type = type;
        }
    }
}

