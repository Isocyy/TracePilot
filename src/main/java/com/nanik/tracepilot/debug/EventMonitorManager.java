package com.nanik.tracepilot.debug;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages event monitoring subscriptions and captures events for later retrieval.
 * 
 * Events are stored in a queue and can be retrieved via events_pending tool.
 * Each subscription type has an ID for management.
 */
public class EventMonitorManager {
    
    private static EventMonitorManager instance;
    
    // Event request tracking
    private final Map<String, EventRequest> activeRequests = new ConcurrentHashMap<>();
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    
    // Captured events queue (limited size to prevent memory issues)
    private final Queue<CapturedEvent> capturedEvents = new ConcurrentLinkedQueue<>();
    private static final int MAX_EVENTS = 100;
    
    // Event types
    public static final String TYPE_CLASS_PREPARE = "class_prepare";
    public static final String TYPE_CLASS_UNLOAD = "class_unload";
    public static final String TYPE_THREAD_START = "thread_start";
    public static final String TYPE_THREAD_DEATH = "thread_death";
    public static final String TYPE_MONITOR_CONTEND = "monitor_contend";
    public static final String TYPE_MONITOR_WAIT = "monitor_wait";
    
    private EventMonitorManager() {}
    
    public static synchronized EventMonitorManager getInstance() {
        if (instance == null) {
            instance = new EventMonitorManager();
        }
        return instance;
    }
    
    /**
     * Reset the manager (called on disconnect)
     */
    public void reset() {
        activeRequests.clear();
        capturedEvents.clear();
        requestCounter.set(0);
    }
    
    /**
     * Create a class prepare watch request
     */
    public String watchClassPrepare(VirtualMachine vm, String classFilter) {
        EventRequestManager erm = vm.eventRequestManager();
        ClassPrepareRequest request = erm.createClassPrepareRequest();
        
        if (classFilter != null && !classFilter.isEmpty() && !classFilter.equals("*")) {
            request.addClassFilter(classFilter);
        }
        
        request.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        request.enable();
        
        String id = "cp-" + requestCounter.incrementAndGet();
        activeRequests.put(id, request);
        return id;
    }
    
    /**
     * Create a class unload watch request
     */
    public String watchClassUnload(VirtualMachine vm, String classFilter) {
        EventRequestManager erm = vm.eventRequestManager();
        ClassUnloadRequest request = erm.createClassUnloadRequest();
        
        if (classFilter != null && !classFilter.isEmpty() && !classFilter.equals("*")) {
            request.addClassFilter(classFilter);
        }
        
        request.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        request.enable();
        
        String id = "cu-" + requestCounter.incrementAndGet();
        activeRequests.put(id, request);
        return id;
    }
    
    /**
     * Create a thread start watch request
     */
    public String watchThreadStart(VirtualMachine vm) {
        EventRequestManager erm = vm.eventRequestManager();
        ThreadStartRequest request = erm.createThreadStartRequest();
        request.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        request.enable();
        
        String id = "ts-" + requestCounter.incrementAndGet();
        activeRequests.put(id, request);
        return id;
    }
    
    /**
     * Create a thread death watch request
     */
    public String watchThreadDeath(VirtualMachine vm) {
        EventRequestManager erm = vm.eventRequestManager();
        ThreadDeathRequest request = erm.createThreadDeathRequest();
        request.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        request.enable();
        
        String id = "td-" + requestCounter.incrementAndGet();
        activeRequests.put(id, request);
        return id;
    }
    
    /**
     * Create a monitor contended enter watch request
     */
    public String watchMonitorContention(VirtualMachine vm) throws UnsupportedOperationException {
        if (!vm.canRequestMonitorEvents()) {
            throw new UnsupportedOperationException("VM does not support monitor events");
        }
        
        EventRequestManager erm = vm.eventRequestManager();
        MonitorContendedEnterRequest request = erm.createMonitorContendedEnterRequest();
        request.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        request.enable();
        
        String id = "mc-" + requestCounter.incrementAndGet();
        activeRequests.put(id, request);
        return id;
    }
    
    /**
     * Remove a watch by its ID
     */
    public boolean removeWatch(VirtualMachine vm, String watchId) {
        EventRequest request = activeRequests.remove(watchId);
        if (request != null) {
            vm.eventRequestManager().deleteEventRequest(request);
            return true;
        }
        return false;
    }
    
    /**
     * Get all active watches
     */
    public Map<String, String> getActiveWatches() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, EventRequest> entry : activeRequests.entrySet()) {
            String id = entry.getKey();
            EventRequest req = entry.getValue();
            String type = getRequestType(req);
            result.put(id, type + (req.isEnabled() ? " [enabled]" : " [disabled]"));
        }
        return result;
    }

    private String getRequestType(EventRequest req) {
        if (req instanceof ClassPrepareRequest) return TYPE_CLASS_PREPARE;
        if (req instanceof ClassUnloadRequest) return TYPE_CLASS_UNLOAD;
        if (req instanceof ThreadStartRequest) return TYPE_THREAD_START;
        if (req instanceof ThreadDeathRequest) return TYPE_THREAD_DEATH;
        if (req instanceof MonitorContendedEnterRequest) return TYPE_MONITOR_CONTEND;
        if (req instanceof MonitorWaitRequest) return TYPE_MONITOR_WAIT;
        return "unknown";
    }

    /**
     * Record a captured event
     */
    public void captureEvent(Event event) {
        CapturedEvent captured = CapturedEvent.from(event);
        if (captured != null) {
            capturedEvents.add(captured);
            // Trim if too many events
            while (capturedEvents.size() > MAX_EVENTS) {
                capturedEvents.poll();
            }
        }
    }

    /**
     * Get and clear all pending events
     */
    public List<CapturedEvent> getPendingEvents() {
        List<CapturedEvent> result = new ArrayList<>();
        CapturedEvent event;
        while ((event = capturedEvents.poll()) != null) {
            result.add(event);
        }
        return result;
    }

    /**
     * Peek at pending events without removing them
     */
    public List<CapturedEvent> peekPendingEvents() {
        return new ArrayList<>(capturedEvents);
    }

    /**
     * Get count of pending events
     */
    public int getPendingEventCount() {
        return capturedEvents.size();
    }

    /**
     * Data class for captured events
     */
    public static class CapturedEvent {
        public final String type;
        public final long timestamp;
        public final Map<String, String> details;

        public CapturedEvent(String type, Map<String, String> details) {
            this.type = type;
            this.timestamp = System.currentTimeMillis();
            this.details = details;
        }

        public static CapturedEvent from(Event event) {
            Map<String, String> details = new LinkedHashMap<>();
            String type;

            if (event instanceof ClassPrepareEvent) {
                ClassPrepareEvent e = (ClassPrepareEvent) event;
                type = TYPE_CLASS_PREPARE;
                details.put("className", e.referenceType().name());
                details.put("threadName", e.thread().name());
            } else if (event instanceof ClassUnloadEvent) {
                ClassUnloadEvent e = (ClassUnloadEvent) event;
                type = TYPE_CLASS_UNLOAD;
                details.put("className", e.className());
            } else if (event instanceof ThreadStartEvent) {
                ThreadStartEvent e = (ThreadStartEvent) event;
                type = TYPE_THREAD_START;
                details.put("threadId", String.valueOf(e.thread().uniqueID()));
                details.put("threadName", e.thread().name());
            } else if (event instanceof ThreadDeathEvent) {
                ThreadDeathEvent e = (ThreadDeathEvent) event;
                type = TYPE_THREAD_DEATH;
                details.put("threadId", String.valueOf(e.thread().uniqueID()));
                details.put("threadName", e.thread().name());
            } else if (event instanceof MonitorContendedEnterEvent) {
                MonitorContendedEnterEvent e = (MonitorContendedEnterEvent) event;
                type = TYPE_MONITOR_CONTEND;
                details.put("threadId", String.valueOf(e.thread().uniqueID()));
                details.put("threadName", e.thread().name());
                details.put("monitorClass", e.monitor().type().name());
                details.put("monitorId", String.valueOf(e.monitor().uniqueID()));
            } else if (event instanceof MonitorWaitEvent) {
                MonitorWaitEvent e = (MonitorWaitEvent) event;
                type = TYPE_MONITOR_WAIT;
                details.put("threadId", String.valueOf(e.thread().uniqueID()));
                details.put("threadName", e.thread().name());
                details.put("monitorClass", e.monitor().type().name());
                details.put("timeout", String.valueOf(e.timeout()));
            } else {
                return null;
            }

            return new CapturedEvent(type, details);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(type).append("] ");
            for (Map.Entry<String, String> e : details.entrySet()) {
                sb.append(e.getKey()).append("=").append(e.getValue()).append(", ");
            }
            if (!details.isEmpty()) {
                sb.setLength(sb.length() - 2);
            }
            return sb.toString();
        }
    }
}

