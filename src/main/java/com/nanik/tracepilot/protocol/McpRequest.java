package com.nanik.tracepilot.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Represents a JSON-RPC 2.0 request message.
 * 
 * Structure:
 * {
 *   "jsonrpc": "2.0",
 *   "id": 1,           // Can be string, number, or null (notification)
 *   "method": "tools/call",
 *   "params": {...}
 * }
 */
public class McpRequest {
    
    private final String jsonrpc;
    private final JsonElement id;  // Can be String, Number, or null
    private final String method;
    private final JsonObject params;
    
    public McpRequest(String jsonrpc, JsonElement id, String method, JsonObject params) {
        this.jsonrpc = jsonrpc;
        this.id = id;
        this.method = method;
        this.params = params;
    }
    
    public String getJsonrpc() {
        return jsonrpc;
    }
    
    public JsonElement getId() {
        return id;
    }
    
    public String getMethod() {
        return method;
    }
    
    public JsonObject getParams() {
        return params != null ? params : new JsonObject();
    }
    
    /**
     * Check if this is a notification (no id = no response expected).
     */
    public boolean isNotification() {
        return id == null || id.isJsonNull();
    }
    
    /**
     * Validate the request according to JSON-RPC 2.0 spec.
     * @return null if valid, error message if invalid
     */
    public String validate() {
        if (jsonrpc == null || !jsonrpc.equals("2.0")) {
            return "jsonrpc must be exactly \"2.0\"";
        }
        if (method == null || method.isEmpty()) {
            return "method is required";
        }
        return null; // Valid
    }
    
    /**
     * Get a string parameter from params.
     */
    public String getStringParam(String name) {
        if (params == null || !params.has(name)) {
            return null;
        }
        JsonElement elem = params.get(name);
        return elem.isJsonPrimitive() ? elem.getAsString() : null;
    }
    
    /**
     * Get an integer parameter from params.
     */
    public Integer getIntParam(String name) {
        if (params == null || !params.has(name)) {
            return null;
        }
        JsonElement elem = params.get(name);
        return elem.isJsonPrimitive() ? elem.getAsInt() : null;
    }
    
    /**
     * Get a boolean parameter from params.
     */
    public Boolean getBoolParam(String name) {
        if (params == null || !params.has(name)) {
            return null;
        }
        JsonElement elem = params.get(name);
        return elem.isJsonPrimitive() ? elem.getAsBoolean() : null;
    }
    
    /**
     * Get an object parameter from params.
     */
    public JsonObject getObjectParam(String name) {
        if (params == null || !params.has(name)) {
            return null;
        }
        JsonElement elem = params.get(name);
        return elem.isJsonObject() ? elem.getAsJsonObject() : null;
    }

    /**
     * Get a long parameter from params.
     */
    public Long getLongParam(String name) {
        if (params == null || !params.has(name)) {
            return null;
        }
        JsonElement elem = params.get(name);
        return elem.isJsonPrimitive() ? elem.getAsLong() : null;
    }
    
    @Override
    public String toString() {
        return "McpRequest{jsonrpc='" + jsonrpc + "', id=" + id + 
               ", method='" + method + "', params=" + params + "}";
    }
}

