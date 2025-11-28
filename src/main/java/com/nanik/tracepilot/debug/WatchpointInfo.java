package com.nanik.tracepilot.debug;

import com.sun.jdi.request.EventRequest;

/**
 * Information about a watchpoint (field access/modification).
 */
public class WatchpointInfo {

    public enum WatchpointType {
        ACCESS,       // Break on field read
        MODIFICATION  // Break on field write
    }

    private final String id;
    private final String className;
    private final String fieldName;
    private final WatchpointType type;
    private EventRequest request;
    private boolean enabled;
    private boolean pending;
    private int hitCount;

    public WatchpointInfo(String id, String className, String fieldName, 
                          WatchpointType type, EventRequest request) {
        this.id = id;
        this.className = className;
        this.fieldName = fieldName;
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

    public String getFieldName() {
        return fieldName;
    }

    public WatchpointType getType() {
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
        String typeStr = (type == WatchpointType.ACCESS) ? "access" : "modification";
        return String.format("%s: %s.%s [%s] (%s, hits: %d)",
            id, className, fieldName, status, typeStr, hitCount);
    }
}

