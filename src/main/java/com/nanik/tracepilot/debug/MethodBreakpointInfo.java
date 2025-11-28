package com.nanik.tracepilot.debug;

import com.sun.jdi.request.EventRequest;

/**
 * Information about a method entry/exit breakpoint.
 */
public class MethodBreakpointInfo {

    public enum MethodBreakpointType {
        ENTRY,  // Break on method entry
        EXIT    // Break on method exit
    }

    private final String id;
    private final String className;
    private final String methodName;
    private final MethodBreakpointType type;
    private EventRequest request;
    private boolean enabled;
    private boolean pending;
    private int hitCount;

    public MethodBreakpointInfo(String id, String className, String methodName,
                                MethodBreakpointType type, EventRequest request) {
        this.id = id;
        this.className = className;
        this.methodName = methodName;
        this.type = type;
        this.request = request;
        this.enabled = true;
        this.pending = (request == null);
        this.hitCount = 0;
    }

    public String getId() {
        return id;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public MethodBreakpointType getType() {
        return type;
    }

    public EventRequest getRequest() {
        return request;
    }

    public void setRequest(EventRequest request) {
        this.request = request;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPending() {
        return pending;
    }

    public void setPending(boolean pending) {
        this.pending = pending;
    }

    public int getHitCount() {
        return hitCount;
    }

    public void incrementHitCount() {
        this.hitCount++;
    }

    @Override
    public String toString() {
        String status = pending ? "pending" : (enabled ? "enabled" : "disabled");
        String typeStr = (type == MethodBreakpointType.ENTRY) ? "entry" : "exit";
        return String.format("%s: %s.%s [%s] (%s, hits: %d)",
            id, className, methodName, status, typeStr, hitCount);
    }
}

