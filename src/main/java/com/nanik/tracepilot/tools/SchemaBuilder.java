package com.nanik.tracepilot.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Helper class for building JSON Schema objects for tool input definitions.
 */
public class SchemaBuilder {
    
    private final JsonObject schema;
    private final JsonObject properties;
    private final JsonArray required;
    
    public SchemaBuilder() {
        this.schema = new JsonObject();
        this.properties = new JsonObject();
        this.required = new JsonArray();
        
        schema.addProperty("type", "object");
        schema.add("properties", properties);
    }
    
    /**
     * Add a string property (not required).
     */
    public SchemaBuilder addString(String name, String description) {
        return addString(name, description, false);
    }

    /**
     * Add a string property.
     */
    public SchemaBuilder addString(String name, String description, boolean isRequired) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", description);
        properties.add(name, prop);

        if (isRequired) {
            required.add(name);
        }
        return this;
    }
    
    /**
     * Add an integer property (not required).
     */
    public SchemaBuilder addInteger(String name, String description) {
        return addInteger(name, description, false);
    }

    /**
     * Add an integer property.
     */
    public SchemaBuilder addInteger(String name, String description, boolean isRequired) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "integer");
        prop.addProperty("description", description);
        properties.add(name, prop);

        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    /**
     * Mark fields as required.
     */
    public SchemaBuilder setRequired(String... names) {
        for (String name : names) {
            required.add(name);
        }
        return this;
    }
    
    /**
     * Add a boolean property.
     */
    public SchemaBuilder addBoolean(String name, String description, boolean isRequired) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "boolean");
        prop.addProperty("description", description);
        properties.add(name, prop);
        
        if (isRequired) {
            required.add(name);
        }
        return this;
    }
    
    /**
     * Add a boolean property with default value.
     */
    public SchemaBuilder addBoolean(String name, String description, boolean isRequired, boolean defaultValue) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "boolean");
        prop.addProperty("description", description);
        prop.addProperty("default", defaultValue);
        properties.add(name, prop);
        
        if (isRequired) {
            required.add(name);
        }
        return this;
    }
    
    /**
     * Add an integer property with default value.
     */
    public SchemaBuilder addInteger(String name, String description, boolean isRequired, int defaultValue) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "integer");
        prop.addProperty("description", description);
        prop.addProperty("default", defaultValue);
        properties.add(name, prop);
        
        if (isRequired) {
            required.add(name);
        }
        return this;
    }
    
    /**
     * Add a string property with default value.
     */
    public SchemaBuilder addString(String name, String description, boolean isRequired, String defaultValue) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", description);
        prop.addProperty("default", defaultValue);
        properties.add(name, prop);
        
        if (isRequired) {
            required.add(name);
        }
        return this;
    }
    
    /**
     * Build the final schema.
     */
    public JsonObject build() {
        if (required.size() > 0) {
            schema.add("required", required);
        }
        return schema;
    }
}

