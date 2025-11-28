package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;
import com.sun.jdi.*;
import com.sun.jdi.event.*;

/**
 * Tool to get information about the current exception when stopped at an exception event.
 */
public class ExceptionInfoTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder().build();

        return new ToolDefinition(
            "exception_info",
            "Get information about the current exception when stopped at an exception breakpoint. " +
            "Shows exception type, message, and stack trace.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        try {
            VirtualMachine vm = DebugSession.getInstance().getVm();
            if (vm == null) {
                return ToolResult.error("Not connected to a VM. Use debug_attach_socket or debug_launch first.");
            }

            // Poll the event queue for exception events
            EventQueue queue = vm.eventQueue();
            EventSet eventSet = queue.remove(100); // 100ms timeout

            if (eventSet != null) {
                for (Event event : eventSet) {
                    if (event instanceof ExceptionEvent) {
                        ExceptionEvent exEvent = (ExceptionEvent) event;
                        DebugSession.getInstance().setLastEvent(event);
                        return formatExceptionInfo(exEvent);
                    }
                }
                // Resume the event set if no exception event
                eventSet.resume();
            }

            // Check stored last event
            Event lastEvent = DebugSession.getInstance().getLastEvent();
            if (lastEvent instanceof ExceptionEvent) {
                return formatExceptionInfo((ExceptionEvent) lastEvent);
            }

            // No exception event found
            return ToolResult.error("No exception at current location. " +
                "Use this command when stopped at an exception breakpoint.");

        } catch (InterruptedException e) {
            return ToolResult.error("Interrupted while checking for exception");
        } catch (Exception e) {
            return ToolResult.error("Failed to get exception info: " + e.getMessage());
        }
    }

    private ToolResult formatExceptionInfo(ExceptionEvent exEvent) {
        try {
            ObjectReference exception = exEvent.exception();
            Location throwLocation = exEvent.location();
            Location catchLocation = exEvent.catchLocation();

            StringBuilder sb = new StringBuilder();
            sb.append("Exception Information:\n");
            sb.append("─────────────────────────────────\n");
            sb.append("Type: ").append(exception.referenceType().name()).append("\n");
            sb.append("Object ID: ").append(exception.uniqueID()).append("\n");

            // Try to get the message
            String message = getExceptionMessage(exception);
            if (message != null) {
                sb.append("Message: ").append(message).append("\n");
            }

            sb.append("\nThrow Location:\n");
            sb.append("  Class: ").append(throwLocation.declaringType().name()).append("\n");
            sb.append("  Method: ").append(throwLocation.method().name()).append("\n");
            try {
                sb.append("  Source: ").append(throwLocation.sourceName())
                  .append(":").append(throwLocation.lineNumber()).append("\n");
            } catch (AbsentInformationException e) {
                sb.append("  Line: ").append(throwLocation.lineNumber()).append("\n");
            }

            if (catchLocation != null) {
                sb.append("\nCatch Location:\n");
                sb.append("  Class: ").append(catchLocation.declaringType().name()).append("\n");
                sb.append("  Method: ").append(catchLocation.method().name()).append("\n");
                try {
                    sb.append("  Source: ").append(catchLocation.sourceName())
                      .append(":").append(catchLocation.lineNumber()).append("\n");
                } catch (AbsentInformationException e) {
                    sb.append("  Line: ").append(catchLocation.lineNumber()).append("\n");
                }
            } else {
                sb.append("\nCatch Location: Uncaught (will propagate)\n");
            }

            sb.append("\nThread: ").append(exEvent.thread().name())
              .append(" (ID: ").append(exEvent.thread().uniqueID()).append(")\n");

            return ToolResult.success(sb.toString());

        } catch (Exception e) {
            return ToolResult.error("Failed to format exception info: " + e.getMessage());
        }
    }

    private String getExceptionMessage(ObjectReference exception) {
        try {
            ReferenceType type = exception.referenceType();
            Field messageField = type.fieldByName("detailMessage");
            if (messageField != null) {
                Value value = exception.getValue(messageField);
                if (value instanceof StringReference) {
                    return ((StringReference) value).value();
                }
            }
        } catch (Exception e) {
            // Ignore - message not available
        }
        return null;
    }
}

