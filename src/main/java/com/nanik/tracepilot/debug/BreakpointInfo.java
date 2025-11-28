package com.nanik.tracepilot.debug;

import com.sun.jdi.request.BreakpointRequest;

/**
 * Information about a breakpoint.
 */
public class BreakpointInfo {

    private final String id;
    private final String className;
    private final int lineNumber;
    private BreakpointRequest request;
    private boolean enabled;
    private boolean pending;
    private int hitCount;

    public BreakpointInfo(String id, String className, int lineNumber, BreakpointRequest request) {
        this.id = id;
        this.className = className;
        this.lineNumber = lineNumber;
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

    public int getLineNumber() {
        return lineNumber;
    }

    public BreakpointRequest getRequest() {
        return request;
    }

    public void setRequest(BreakpointRequest request) {
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
        return String.format("%s: %s:%d [%s] (hits: %d)",
            id, className, lineNumber, status, hitCount);
    }
}

