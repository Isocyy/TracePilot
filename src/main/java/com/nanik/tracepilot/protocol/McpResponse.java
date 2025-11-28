package com.nanik.tracepilot.protocol;

import com.google.gson.JsonElement;

/**
 * Represents a JSON-RPC 2.0 response message.
 * 
 * Success response:
 * {
 *   "jsonrpc": "2.0",
 *   "id": 1,
 *   "result": {...}
 * }
 * 
 * Error response:
 * {
 *   "jsonrpc": "2.0",
 *   "id": 1,
 *   "error": {
 *     "code": -32600,
 *     "message": "Invalid Request",
 *     "data": "optional details"
 *   }
 * }
 */
public class McpResponse {
    
    private final String jsonrpc = "2.0";
    private final JsonElement id;
    private final Object result;
    private final McpError error;
    
    private McpResponse(JsonElement id, Object result, McpError error) {
        this.id = id;
        this.result = result;
        this.error = error;
    }
    
    /**
     * Create a success response.
     */
    public static McpResponse success(JsonElement id, Object result) {
        return new McpResponse(id, result, null);
    }
    
    /**
     * Create an error response.
     */
    public static McpResponse error(JsonElement id, McpError error) {
        return new McpResponse(id, null, error);
    }
    
    /**
     * Create an error response with code and message.
     */
    public static McpResponse error(JsonElement id, int code, String message) {
        return new McpResponse(id, null, new McpError(code, message));
    }
    
    public String getJsonrpc() {
        return jsonrpc;
    }
    
    public JsonElement getId() {
        return id;
    }
    
    public Object getResult() {
        return result;
    }
    
    public McpError getError() {
        return error;
    }
    
    public boolean isError() {
        return error != null;
    }
    
    @Override
    public String toString() {
        if (error != null) {
            return "McpResponse{id=" + id + ", error=" + error + "}";
        }
        return "McpResponse{id=" + id + ", result=" + result + "}";
    }
}

