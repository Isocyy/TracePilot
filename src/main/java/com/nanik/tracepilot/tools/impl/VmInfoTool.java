package com.nanik.tracepilot.tools.impl;

import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.*;
import com.sun.jdi.VirtualMachine;

/**
 * Tool to get detailed information about the connected VM.
 * 
 * Returns:
 * - VM name and version
 * - VM capabilities
 * - System properties
 */
public class VmInfoTool implements ToolHandler {
    
    private static final ToolDefinition DEFINITION = ToolDefinition.noParams(
        "vm_info",
        "Get detailed information about the connected VM including capabilities and system properties."
    );
    
    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }
    
    @Override
    public ToolResult execute(McpRequest request) {
        DebugSession session = DebugSession.getInstance();
        
        if (!session.isConnected()) {
            return ToolResult.error("Not connected to any VM.");
        }
        
        VirtualMachine vm = session.getVm();
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== VM Information ===\n\n");
        
        // Basic info
        sb.append("Name: ").append(vm.name()).append("\n");
        sb.append("Version: ").append(vm.version()).append("\n");
        sb.append("Description: ").append(vm.description()).append("\n\n");
        
        // Capabilities
        sb.append("=== Capabilities ===\n");
        sb.append("Can watch field access: ").append(vm.canWatchFieldAccess()).append("\n");
        sb.append("Can watch field modification: ").append(vm.canWatchFieldModification()).append("\n");
        sb.append("Can get bytecodes: ").append(vm.canGetBytecodes()).append("\n");
        sb.append("Can get synthetic attr: ").append(vm.canGetSyntheticAttribute()).append("\n");
        sb.append("Can get owned monitor info: ").append(vm.canGetOwnedMonitorInfo()).append("\n");
        sb.append("Can get current contended monitor: ").append(vm.canGetCurrentContendedMonitor()).append("\n");
        sb.append("Can get monitor info: ").append(vm.canGetMonitorInfo()).append("\n");
        sb.append("Can redefine classes: ").append(vm.canRedefineClasses()).append("\n");
        sb.append("Can add method: ").append(vm.canAddMethod()).append("\n");
        sb.append("Can unrestrictedly redefine: ").append(vm.canUnrestrictedlyRedefineClasses()).append("\n");
        sb.append("Can pop frames: ").append(vm.canPopFrames()).append("\n");
        sb.append("Can force early return: ").append(vm.canForceEarlyReturn()).append("\n");
        sb.append("Can be modified: ").append(vm.canBeModified()).append("\n");
        sb.append("Can request VM death event: ").append(vm.canRequestVMDeathEvent()).append("\n");
        sb.append("Can get method return values: ").append(vm.canGetMethodReturnValues()).append("\n");
        sb.append("Can get instance info: ").append(vm.canGetInstanceInfo()).append("\n");
        sb.append("Can use source name filters: ").append(vm.canUseSourceNameFilters()).append("\n\n");
        
        // Class file version info
        sb.append("=== Additional Info ===\n");
        sb.append("Can get class file version: ").append(vm.canGetClassFileVersion()).append("\n");

        return ToolResult.success(sb.toString());
    }
}

