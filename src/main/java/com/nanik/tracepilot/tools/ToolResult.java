package com.nanik.tracepilot.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of a tool execution.
 * 
 * MCP tool results have this structure:
 * {
 *   "content": [
 *     {"type": "text", "text": "..."}
 *   ],
 *   "isError": false
 * }
 */
public class ToolResult {
    
    private final List<Content> content;
    private final boolean isError;
    
    private ToolResult(List<Content> content, boolean isError) {
        this.content = content;
        this.isError = isError;
    }
    
    /**
     * Create a success result with text content.
     */
    public static ToolResult success(String text) {
        List<Content> content = new ArrayList<>();
        content.add(new Content("text", text));
        return new ToolResult(content, false);
    }
    
    /**
     * Create a success result with multiple text contents.
     */
    public static ToolResult success(List<String> texts) {
        List<Content> content = new ArrayList<>();
        for (String text : texts) {
            content.add(new Content("text", text));
        }
        return new ToolResult(content, false);
    }
    
    /**
     * Create an error result.
     */
    public static ToolResult error(String errorMessage) {
        List<Content> content = new ArrayList<>();
        content.add(new Content("text", errorMessage));
        return new ToolResult(content, true);
    }
    
    public List<Content> getContent() {
        return content;
    }
    
    public boolean isError() {
        return isError;
    }
    
    /**
     * Content item in a tool result.
     */
    public static class Content {
        private final String type;
        private final String text;
        
        public Content(String type, String text) {
            this.type = type;
            this.text = text;
        }
        
        public String getType() {
            return type;
        }
        
        public String getText() {
            return text;
        }
    }
}

