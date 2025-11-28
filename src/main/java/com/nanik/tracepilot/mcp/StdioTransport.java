package com.nanik.tracepilot.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.nanik.tracepilot.protocol.McpResponse;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Transport layer for MCP using stdin/stdout.
 * 
 * Reads JSON-RPC messages from stdin (one per line).
 * Writes JSON-RPC responses to stdout (one per line).
 * Logs to stderr for debugging.
 */
public class StdioTransport {
    
    private final BufferedReader reader;
    private final PrintWriter writer;
    private final PrintWriter logger;
    private final Gson gson;
    private final BlockingQueue<String> messageQueue;
    private volatile boolean running;
    private Thread readerThread;
    
    public StdioTransport() {
        this(System.in, System.out, System.err);
    }
    
    public StdioTransport(InputStream in, OutputStream out, OutputStream err) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
        this.logger = new PrintWriter(new OutputStreamWriter(err, StandardCharsets.UTF_8), true);
        this.gson = new GsonBuilder().create();
        this.messageQueue = new LinkedBlockingQueue<>();
        this.running = false;
    }
    
    /**
     * Start the transport (begin reading from stdin in background).
     */
    public void start() {
        running = true;
        readerThread = new Thread(this::readLoop, "StdioTransport-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
        log("Transport started");
    }
    
    /**
     * Stop the transport.
     */
    public void stop() {
        running = false;
        if (readerThread != null) {
            readerThread.interrupt();
        }
        log("Transport stopped");
    }
    
    /**
     * Background loop to read messages from stdin.
     */
    private void readLoop() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    messageQueue.offer(line);
                }
            }
        } catch (IOException e) {
            if (running) {
                log("Error reading from stdin: " + e.getMessage());
            }
        }
        log("Reader loop ended");
    }
    
    /**
     * Read the next message from the queue.
     * Blocks until a message is available or interrupted.
     */
    public String readMessage() throws InterruptedException {
        return messageQueue.take();
    }
    
    /**
     * Check if a message is available without blocking.
     */
    public String pollMessage() {
        return messageQueue.poll();
    }
    
    /**
     * Parse a JSON message string.
     */
    public JsonObject parseJson(String message) throws JsonSyntaxException {
        return gson.fromJson(message, JsonObject.class);
    }
    
    /**
     * Send a response to stdout.
     */
    public void sendResponse(McpResponse response) {
        String json = gson.toJson(response);
        writer.println(json);
        writer.flush();
    }
    
    /**
     * Send a raw JSON object to stdout.
     */
    public void sendJson(Object obj) {
        String json = gson.toJson(obj);
        writer.println(json);
        writer.flush();
    }
    
    /**
     * Log a message to stderr.
     */
    public void log(String message) {
        logger.println("[TracePilot] " + message);
        logger.flush();
    }
    
    /**
     * Log an error with exception to stderr.
     */
    public void logError(String message, Throwable t) {
        logger.println("[TracePilot ERROR] " + message + ": " + t.getMessage());
        t.printStackTrace(logger);
        logger.flush();
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public Gson getGson() {
        return gson;
    }
}

