package com.nanik.tracepilot.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nanik.tracepilot.protocol.McpError;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolResult;
import com.nanik.tracepilot.tools.impl.*;



/**
 * Main MCP Server for TracePilot.
 * 
 * Implements the Model Context Protocol over stdio.
 * Handles lifecycle methods (initialize, initialized, shutdown)
 * and tool dispatching.
 */
public class McpServer {
    
    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "TracePilot";
    private static final String SERVER_VERSION = "1.0.0";
    
    private final StdioTransport transport;
    private final JsonRpcHandler rpcHandler;
    private final ToolRegistry toolRegistry;
    
    private boolean initialized = false;
    private boolean running = false;
    
    public McpServer() {
        this.transport = new StdioTransport();
        this.rpcHandler = new JsonRpcHandler(transport);
        this.toolRegistry = new ToolRegistry(transport);
        
        // Register built-in tools
        registerBuiltInTools();
    }
    
    private void registerBuiltInTools() {
        // Phase 1: Core tools
        toolRegistry.register(new PingTool());

        // Phase 2: VM Connection tools
        toolRegistry.register(new DebugLaunchTool());
        toolRegistry.register(new DebugAttachSocketTool());
        toolRegistry.register(new DebugAttachPidTool());
        toolRegistry.register(new DebugDisconnectTool());
        toolRegistry.register(new DebugStatusTool());
        toolRegistry.register(new VmInfoTool());

        // Phase 3: Breakpoints & Execution Control
        toolRegistry.register(new BreakpointSetTool());
        toolRegistry.register(new BreakpointRemoveTool());
        toolRegistry.register(new BreakpointListTool());
        toolRegistry.register(new BreakpointEnableTool());
        toolRegistry.register(new BreakpointDisableTool());
        toolRegistry.register(new StepIntoTool());
        toolRegistry.register(new StepOverTool());
        toolRegistry.register(new StepOutTool());
        toolRegistry.register(new ResumeTool());
        toolRegistry.register(new SuspendTool());
        toolRegistry.register(new ExecutionLocationTool());

        // Phase 4: Inspection (Threads, Stack, Variables)
        toolRegistry.register(new ThreadsListTool());
        toolRegistry.register(new ThreadSuspendTool());
        toolRegistry.register(new ThreadResumeTool());
        toolRegistry.register(new StackFramesTool());
        toolRegistry.register(new VariablesLocalTool());
        toolRegistry.register(new VariablesArgumentsTool());
        toolRegistry.register(new VariableInspectTool());
        toolRegistry.register(new ObjectFieldsTool());
        toolRegistry.register(new ArrayElementsTool());
        toolRegistry.register(new ThisObjectTool());

        // Phase 5: Advanced Breakpoints (Watchpoints, Method Breakpoints)
        toolRegistry.register(new WatchpointAccessTool());
        toolRegistry.register(new WatchpointModificationTool());
        toolRegistry.register(new WatchpointRemoveTool());
        toolRegistry.register(new WatchpointListTool());
        toolRegistry.register(new MethodEntryBreakTool());
        toolRegistry.register(new MethodExitBreakTool());
        toolRegistry.register(new MethodBreakpointRemoveTool());
        toolRegistry.register(new MethodBreakpointListTool());

        // Phase 6: Exception Handling
        toolRegistry.register(new ExceptionBreakOnTool());
        toolRegistry.register(new ExceptionBreakRemoveTool());
        toolRegistry.register(new ExceptionBreakListTool());
        toolRegistry.register(new ExceptionInfoTool());

        // Phase 7: Advanced Features
        toolRegistry.register(new SetVariableTool());
        toolRegistry.register(new InvokeMethodTool());
        toolRegistry.register(new InvokeStaticTool());
        toolRegistry.register(new EvaluateExpressionTool());

        // Phase 8: Monitoring & Events
        toolRegistry.register(new ClassPrepareWatchTool());
        toolRegistry.register(new ClassUnloadWatchTool());
        toolRegistry.register(new ThreadStartWatchTool());
        toolRegistry.register(new ThreadDeathWatchTool());
        toolRegistry.register(new MonitorContentionWatchTool());
        toolRegistry.register(new EventsPendingTool());
        toolRegistry.register(new EventWatchRemoveTool());

        // Async Debugging Support
        toolRegistry.register(new WaitForStopTool());

        // Convenience Tools
        toolRegistry.register(new RunToLineTool());
    }
    
    /**
     * Start the MCP server.
     */
    public void start() {
        transport.log("Starting " + SERVER_NAME + " v" + SERVER_VERSION);
        transport.start();
        running = true;
        
        // Main message loop
        while (running) {
            try {
                String message = transport.readMessage();
                handleMessage(message);
            } catch (InterruptedException e) {
                transport.log("Server interrupted");
                break;
            } catch (Exception e) {
                transport.logError("Error handling message", e);
            }
        }
        
        transport.stop();
        transport.log("Server stopped");
    }
    
    /**
     * Handle an incoming message.
     */
    private void handleMessage(String message) {
        JsonRpcHandler.ParseResult parseResult = rpcHandler.parseRequest(message);
        
        if (!parseResult.isSuccess()) {
            transport.sendResponse(parseResult.getErrorResponse());
            return;
        }
        
        McpRequest request = parseResult.getRequest();
        String method = request.getMethod();
        
        transport.log("Received: " + method);
        
        try {
            switch (method) {
                case "initialize":
                    handleInitialize(request);
                    break;
                case "initialized":
                    handleInitialized(request);
                    break;
                case "shutdown":
                    handleShutdown(request);
                    break;
                case "tools/list":
                    handleToolsList(request);
                    break;
                case "tools/call":
                    handleToolsCall(request);
                    break;
                default:
                    rpcHandler.sendMethodNotFound(request.getId(), method);
                    break;
            }
        } catch (Exception e) {
            transport.logError("Error processing " + method, e);
            rpcHandler.sendInternalError(request.getId(), e);
        }
    }
    
    /**
     * Handle initialize request.
     */
    private void handleInitialize(McpRequest request) {
        if (initialized) {
            rpcHandler.sendError(request.getId(), 
                McpError.invalidRequest("Already initialized"));
            return;
        }
        
        // Build response
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", PROTOCOL_VERSION);
        
        // Server info
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", SERVER_NAME);
        serverInfo.addProperty("version", SERVER_VERSION);
        result.add("serverInfo", serverInfo);
        
        // Capabilities
        JsonObject capabilities = new JsonObject();
        JsonObject tools = new JsonObject();
        capabilities.add("tools", tools);
        result.add("capabilities", capabilities);
        
        rpcHandler.sendSuccess(request.getId(), result);
        transport.log("Initialized with protocol version " + PROTOCOL_VERSION);
    }
    
    /**
     * Handle initialized notification.
     */
    private void handleInitialized(McpRequest request) {
        initialized = true;
        transport.log("Client confirmed initialization");
        // This is a notification, no response needed
    }
    
    /**
     * Handle shutdown request.
     */
    private void handleShutdown(McpRequest request) {
        transport.log("Shutdown requested");
        running = false;
        rpcHandler.sendSuccess(request.getId(), new JsonObject());
    }
    
    /**
     * Handle tools/list request.
     */
    private void handleToolsList(McpRequest request) {
        JsonObject result = new JsonObject();
        JsonArray toolsArray = new JsonArray();

        for (ToolDefinition def : toolRegistry.getAllDefinitions()) {
            JsonObject toolObj = new JsonObject();
            toolObj.addProperty("name", def.getName());
            toolObj.addProperty("description", def.getDescription());
            toolObj.add("inputSchema", def.getInputSchema());
            toolsArray.add(toolObj);
        }

        result.add("tools", toolsArray);
        rpcHandler.sendSuccess(request.getId(), result);
    }

    /**
     * Handle tools/call request.
     */
    private void handleToolsCall(McpRequest request) {
        String toolName = request.getStringParam("name");

        if (toolName == null || toolName.isEmpty()) {
            rpcHandler.sendInvalidParams(request.getId(), "Missing 'name' parameter");
            return;
        }

        if (!toolRegistry.hasTool(toolName)) {
            rpcHandler.sendError(request.getId(),
                McpError.methodNotFound("Tool not found: " + toolName));
            return;
        }

        // Create a sub-request with the tool arguments
        JsonObject toolArgs = request.getObjectParam("arguments");
        McpRequest toolRequest = new McpRequest(
            request.getJsonrpc(),
            request.getId(),
            toolName,
            toolArgs
        );

        ToolResult result = toolRegistry.execute(toolName, toolRequest);

        if (result == null) {
            rpcHandler.sendInternalError(request.getId(),
                new RuntimeException("Tool returned null result"));
            return;
        }

        // Build MCP tool result response
        JsonObject resultObj = new JsonObject();
        JsonArray contentArray = new JsonArray();

        for (ToolResult.Content content : result.getContent()) {
            JsonObject contentObj = new JsonObject();
            contentObj.addProperty("type", content.getType());
            contentObj.addProperty("text", content.getText());
            contentArray.add(contentObj);
        }

        resultObj.add("content", contentArray);
        if (result.isError()) {
            resultObj.addProperty("isError", true);
        }

        rpcHandler.sendSuccess(request.getId(), resultObj);
    }

    /**
     * Get the tool registry (for registering additional tools).
     */
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        McpServer server = new McpServer();
        server.start();
    }
}

