package com.nanik.tracepilot.protocol;

/**
 * MCP/JSON-RPC 2.0 error codes and error response structure.
 */
public class McpError {
    
    // JSON-RPC 2.0 standard error codes
    public static final int PARSE_ERROR = -32700;      // Invalid JSON
    public static final int INVALID_REQUEST = -32600;  // Invalid Request object
    public static final int METHOD_NOT_FOUND = -32601; // Method does not exist
    public static final int INVALID_PARAMS = -32602;   // Invalid method parameters
    public static final int INTERNAL_ERROR = -32603;   // Internal JSON-RPC error
    
    // Error response fields
    private final int code;
    private final String message;
    private final Object data;
    
    public McpError(int code, String message) {
        this(code, message, null);
    }
    
    public McpError(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Object getData() {
        return data;
    }
    
    // Factory methods for common errors
    public static McpError parseError(String details) {
        return new McpError(PARSE_ERROR, "Parse error", details);
    }
    
    public static McpError invalidRequest(String details) {
        return new McpError(INVALID_REQUEST, "Invalid Request", details);
    }
    
    public static McpError methodNotFound(String method) {
        return new McpError(METHOD_NOT_FOUND, "Method not found: " + method);
    }
    
    public static McpError invalidParams(String details) {
        return new McpError(INVALID_PARAMS, "Invalid params", details);
    }
    
    public static McpError internalError(String details) {
        return new McpError(INTERNAL_ERROR, "Internal error", details);
    }
    
    public static McpError internalError(Throwable t) {
        return new McpError(INTERNAL_ERROR, "Internal error", t.getMessage());
    }
    
    @Override
    public String toString() {
        return "McpError{code=" + code + ", message='" + message + "', data=" + data + "}";
    }
}

