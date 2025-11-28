package com.nanik.tracepilot.debug;

import com.sun.jdi.request.ExceptionRequest;

/**
 * Information about an exception breakpoint.
 */
public class ExceptionBreakpointInfo {

    private final String id;
    private final String exceptionClassName;  // null means all exceptions
    private final boolean catchCaught;
    private final boolean catchUncaught;
    private ExceptionRequest request;
    private boolean enabled;
    private int hitCount;

    public ExceptionBreakpointInfo(String id, String exceptionClassName, 
                                   boolean catchCaught, boolean catchUncaught,
                                   ExceptionRequest request) {
        this.id = id;
        this.exceptionClassName = exceptionClassName;
        this.catchCaught = catchCaught;
        this.catchUncaught = catchUncaught;
        this.request = request;
        this.enabled = true;
        this.hitCount = 0;
    }

    public String getId() {
        return id;
    }

    public String getExceptionClassName() {
        return exceptionClassName;
    }

    public boolean isCatchCaught() {
        return catchCaught;
    }

    public boolean isCatchUncaught() {
        return catchUncaught;
    }

    public ExceptionRequest getRequest() {
        return request;
    }

    public void setRequest(ExceptionRequest request) {
        this.request = request;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getHitCount() {
        return hitCount;
    }

    public void incrementHitCount() {
        this.hitCount++;
    }

    public boolean isAllExceptions() {
        return exceptionClassName == null || exceptionClassName.equals("*");
    }

    @Override
    public String toString() {
        String className = isAllExceptions() ? "All exceptions" : exceptionClassName;
        String catchType = "";
        if (catchCaught && catchUncaught) {
            catchType = "caught+uncaught";
        } else if (catchCaught) {
            catchType = "caught only";
        } else if (catchUncaught) {
            catchType = "uncaught only";
        }
        String status = enabled ? "enabled" : "disabled";
        return String.format("%s: %s [%s] (%s, hits: %d)",
            id, className, status, catchType, hitCount);
    }
}

