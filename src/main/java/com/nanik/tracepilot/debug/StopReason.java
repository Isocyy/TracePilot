package com.nanik.tracepilot.debug;

import com.sun.jdi.*;
import com.sun.jdi.event.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents why the debugger stopped (suspended the VM).
 * 
 * This is crucial for LLM debugging - it tells the debugger WHY it stopped
 * so it knows what to do next.
 */
public class StopReason {
    
    public enum Type {
        NONE,               // VM is running, not stopped
        BREAKPOINT_HIT,     // Stopped at a breakpoint
        STEP_COMPLETE,      // Step operation completed
        EXCEPTION_THROWN,   // Exception was thrown
        WATCHPOINT_ACCESS,  // Field was read
        WATCHPOINT_MODIFY,  // Field was modified
        METHOD_ENTRY,       // Method was entered
        METHOD_EXIT,        // Method was exited
        USER_SUSPEND,       // User called suspend
        VM_START,           // VM just started
        VM_DISCONNECT       // VM disconnected
    }
    
    private final Type type;
    private final long timestamp;
    private final ThreadReference thread;
    private final Location location;
    private final Map<String, String> details;
    
    private StopReason(Type type, ThreadReference thread, Location location, Map<String, String> details) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.thread = thread;
        this.location = location;
        this.details = details != null ? details : new LinkedHashMap<>();
    }
    
    // Factory methods
    
    public static StopReason none() {
        return new StopReason(Type.NONE, null, null, null);
    }
    
    public static StopReason userSuspend() {
        return new StopReason(Type.USER_SUSPEND, null, null, null);
    }
    
    public static StopReason vmStart() {
        return new StopReason(Type.VM_START, null, null, null);
    }
    
    public static StopReason vmDisconnect() {
        return new StopReason(Type.VM_DISCONNECT, null, null, null);
    }
    
    public static StopReason fromEvent(Event event) {
        if (event instanceof BreakpointEvent) {
            return fromBreakpointEvent((BreakpointEvent) event);
        } else if (event instanceof StepEvent) {
            return fromStepEvent((StepEvent) event);
        } else if (event instanceof ExceptionEvent) {
            return fromExceptionEvent((ExceptionEvent) event);
        } else if (event instanceof AccessWatchpointEvent) {
            return fromAccessWatchpointEvent((AccessWatchpointEvent) event);
        } else if (event instanceof ModificationWatchpointEvent) {
            return fromModificationWatchpointEvent((ModificationWatchpointEvent) event);
        } else if (event instanceof MethodEntryEvent) {
            return fromMethodEntryEvent((MethodEntryEvent) event);
        } else if (event instanceof MethodExitEvent) {
            return fromMethodExitEvent((MethodExitEvent) event);
        } else if (event instanceof VMStartEvent) {
            return vmStart();
        }
        return null; // Not a stop event
    }
    
    private static StopReason fromBreakpointEvent(BreakpointEvent event) {
        Map<String, String> details = new LinkedHashMap<>();
        Location loc = event.location();
        details.put("class", loc.declaringType().name());
        details.put("method", loc.method().name());
        details.put("line", String.valueOf(loc.lineNumber()));
        
        // Try to find breakpoint ID
        String bpId = BreakpointManager.getInstance().findBreakpointIdByLocation(loc);
        if (bpId != null) {
            details.put("breakpointId", bpId);
        }
        
        return new StopReason(Type.BREAKPOINT_HIT, event.thread(), loc, details);
    }
    
    private static StopReason fromStepEvent(StepEvent event) {
        Map<String, String> details = new LinkedHashMap<>();
        Location loc = event.location();
        details.put("class", loc.declaringType().name());
        details.put("method", loc.method().name());
        details.put("line", String.valueOf(loc.lineNumber()));
        return new StopReason(Type.STEP_COMPLETE, event.thread(), loc, details);
    }
    
    private static StopReason fromExceptionEvent(ExceptionEvent event) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("exceptionClass", event.exception().referenceType().name());
        Location catchLoc = event.catchLocation();
        if (catchLoc != null) {
            details.put("caught", "true");
            details.put("catchClass", catchLoc.declaringType().name());
            details.put("catchLine", String.valueOf(catchLoc.lineNumber()));
        } else {
            details.put("caught", "false");
        }
        return new StopReason(Type.EXCEPTION_THROWN, event.thread(), event.location(), details);
    }
    
    private static StopReason fromAccessWatchpointEvent(AccessWatchpointEvent event) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("field", event.field().name());
        details.put("fieldClass", event.field().declaringType().name());
        return new StopReason(Type.WATCHPOINT_ACCESS, event.thread(), event.location(), details);
    }
    
    private static StopReason fromModificationWatchpointEvent(ModificationWatchpointEvent event) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("field", event.field().name());
        details.put("fieldClass", event.field().declaringType().name());
        try {
            details.put("newValue", event.valueToBe().toString());
        } catch (Exception e) {
            details.put("newValue", "(unknown)");
        }
        return new StopReason(Type.WATCHPOINT_MODIFY, event.thread(), event.location(), details);
    }
    
    private static StopReason fromMethodEntryEvent(MethodEntryEvent event) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("method", event.method().name());
        details.put("class", event.method().declaringType().name());
        details.put("signature", event.method().signature());
        return new StopReason(Type.METHOD_ENTRY, event.thread(), event.location(), details);
    }
    
    private static StopReason fromMethodExitEvent(MethodExitEvent event) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("method", event.method().name());
        details.put("class", event.method().declaringType().name());
        return new StopReason(Type.METHOD_EXIT, event.thread(), event.location(), details);
    }
    
    // Getters
    
    public Type getType() { return type; }
    public long getTimestamp() { return timestamp; }
    public ThreadReference getThread() { return thread; }
    public Location getLocation() { return location; }
    public Map<String, String> getDetails() { return details; }
    
    public boolean isStopped() {
        return type != Type.NONE;
    }
    
    public String getThreadName() {
        if (thread == null) return null;
        try {
            return thread.name();
        } catch (Exception e) {
            return "(unknown)";
        }
    }
    
    public long getThreadId() {
        if (thread == null) return -1;
        return thread.uniqueID();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.name());
        
        if (thread != null) {
            sb.append(" [thread: ").append(getThreadName()).append("]");
        }
        
        if (location != null) {
            sb.append(" at ").append(location.declaringType().name());
            sb.append(":").append(location.lineNumber());
        }
        
        if (!details.isEmpty()) {
            sb.append(" {");
            boolean first = true;
            for (Map.Entry<String, String> e : details.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(e.getKey()).append("=").append(e.getValue());
                first = false;
            }
            sb.append("}");
        }
        
        return sb.toString();
    }
}

