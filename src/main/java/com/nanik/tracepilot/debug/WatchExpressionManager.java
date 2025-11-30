package com.nanik.tracepilot.debug;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages watch expressions that persist across debug operations.
 * Watch expressions are evaluated on demand when the VM is suspended.
 */
public class WatchExpressionManager {

    private static final WatchExpressionManager INSTANCE = new WatchExpressionManager();

    private final Map<String, WatchExpression> watches = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    private WatchExpressionManager() {}

    public static WatchExpressionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Add a watch expression.
     * @param expression The expression to watch (e.g., "this.counter", "list.size()")
     * @return The watch ID
     */
    public String addWatch(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Expression cannot be empty");
        }
        
        String id = "w-" + idCounter.getAndIncrement();
        WatchExpression watch = new WatchExpression(id, expression.trim());
        watches.put(id, watch);
        return id;
    }

    /**
     * Remove a watch expression by ID.
     * @return true if removed, false if not found
     */
    public boolean removeWatch(String watchId) {
        return watches.remove(watchId) != null;
    }

    /**
     * Get a watch expression by ID.
     */
    public WatchExpression getWatch(String watchId) {
        return watches.get(watchId);
    }

    /**
     * Get all watch expressions.
     */
    public Collection<WatchExpression> getAllWatches() {
        return watches.values();
    }

    /**
     * Get the number of watch expressions.
     */
    public int getWatchCount() {
        return watches.size();
    }

    /**
     * Clear all watch expressions.
     */
    public void clearAll() {
        watches.clear();
    }

    /**
     * Represents a single watch expression.
     */
    public static class WatchExpression {
        private final String id;
        private final String expression;
        private String lastValue;
        private String lastError;
        private long lastEvaluatedAt;

        public WatchExpression(String id, String expression) {
            this.id = id;
            this.expression = expression;
        }

        public String getId() {
            return id;
        }

        public String getExpression() {
            return expression;
        }

        public String getLastValue() {
            return lastValue;
        }

        public void setLastValue(String lastValue) {
            this.lastValue = lastValue;
            this.lastError = null;
            this.lastEvaluatedAt = System.currentTimeMillis();
        }

        public String getLastError() {
            return lastError;
        }

        public void setLastError(String lastError) {
            this.lastError = lastError;
            this.lastValue = null;
            this.lastEvaluatedAt = System.currentTimeMillis();
        }

        public long getLastEvaluatedAt() {
            return lastEvaluatedAt;
        }

        public boolean hasBeenEvaluated() {
            return lastEvaluatedAt > 0;
        }
    }
}

