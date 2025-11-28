package com.nanik.tracepilot.tools;

import com.google.gson.JsonObject;

/**
 * Definition of a tool that can be exposed via MCP.
 * 
 * MCP tool definition structure:
 * {
 *   "name": "debug_launch",
 *   "description": "Launch a JVM with debugging enabled",
 *   "inputSchema": {
 *     "type": "object",
 *     "properties": {
 *       "mainClass": {"type": "string", "description": "..."},
 *       ...
 *     },
 *     "required": ["mainClass"]
 *   }
 * }
 */
public class ToolDefinition {
    
    private final String name;
    private final String description;
    private final JsonObject inputSchema;
    
    public ToolDefinition(String name, String description, JsonObject inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }
    
    /**
     * Create a tool definition with no parameters.
     */
    public static ToolDefinition noParams(String name, String description) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return new ToolDefinition(name, description, schema);
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public JsonObject getInputSchema() {
        return inputSchema;
    }
}

