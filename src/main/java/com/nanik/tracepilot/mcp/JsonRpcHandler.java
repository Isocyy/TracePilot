package com.nanik.tracepilot.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.nanik.tracepilot.protocol.McpError;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.protocol.McpResponse;

/**
 * Handles JSON-RPC 2.0 message parsing and response building.
 */
public class JsonRpcHandler {
    
    private final StdioTransport transport;
    
    public JsonRpcHandler(StdioTransport transport) {
        this.transport = transport;
    }
    
    /**
     * Parse a JSON string into an McpRequest.
     * 
     * @param json The raw JSON string
     * @return ParseResult containing either request or error response
     */
    public ParseResult parseRequest(String json) {
        JsonObject obj;
        try {
            obj = transport.parseJson(json);
        } catch (JsonSyntaxException e) {
            return ParseResult.error(
                McpResponse.error(null, McpError.parseError(e.getMessage()))
            );
        }
        
        if (obj == null) {
            return ParseResult.error(
                McpResponse.error(null, McpError.parseError("Empty JSON"))
            );
        }
        
        // Extract fields
        JsonElement id = obj.get("id");
        String jsonrpc = obj.has("jsonrpc") ? obj.get("jsonrpc").getAsString() : null;
        String method = obj.has("method") ? obj.get("method").getAsString() : null;
        JsonObject params = obj.has("params") && obj.get("params").isJsonObject() 
            ? obj.getAsJsonObject("params") 
            : null;
        
        McpRequest request = new McpRequest(jsonrpc, id, method, params);
        
        // Validate
        String validationError = request.validate();
        if (validationError != null) {
            return ParseResult.error(
                McpResponse.error(id, McpError.invalidRequest(validationError))
            );
        }
        
        return ParseResult.success(request);
    }
    
    /**
     * Send a success response.
     */
    public void sendSuccess(JsonElement id, Object result) {
        if (id == null || id.isJsonNull()) {
            // Notification - no response
            return;
        }
        transport.sendResponse(McpResponse.success(id, result));
    }
    
    /**
     * Send an error response.
     */
    public void sendError(JsonElement id, McpError error) {
        transport.sendResponse(McpResponse.error(id, error));
    }
    
    /**
     * Send a method not found error.
     */
    public void sendMethodNotFound(JsonElement id, String method) {
        sendError(id, McpError.methodNotFound(method));
    }
    
    /**
     * Send an invalid params error.
     */
    public void sendInvalidParams(JsonElement id, String details) {
        sendError(id, McpError.invalidParams(details));
    }
    
    /**
     * Send an internal error.
     */
    public void sendInternalError(JsonElement id, Throwable t) {
        sendError(id, McpError.internalError(t));
    }
    
    /**
     * Result of parsing a request.
     */
    public static class ParseResult {
        private final McpRequest request;
        private final McpResponse errorResponse;
        
        private ParseResult(McpRequest request, McpResponse errorResponse) {
            this.request = request;
            this.errorResponse = errorResponse;
        }
        
        public static ParseResult success(McpRequest request) {
            return new ParseResult(request, null);
        }
        
        public static ParseResult error(McpResponse errorResponse) {
            return new ParseResult(null, errorResponse);
        }
        
        public boolean isSuccess() {
            return request != null;
        }
        
        public McpRequest getRequest() {
            return request;
        }
        
        public McpResponse getErrorResponse() {
            return errorResponse;
        }
    }
}

