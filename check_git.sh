#!/bin/bash
# Phase 3 commit script
cd /Users/nchaturvedi/Desktop/crm_project/TracePilot

echo "=== Current git status ===" > /tmp/phase3_result.txt
git status --porcelain >> /tmp/phase3_result.txt 2>&1

echo "" >> /tmp/phase3_result.txt
echo "=== Adding Phase 3 files ===" >> /tmp/phase3_result.txt
git add src/main/java/com/nanik/tracepilot/tools/impl/DebugLaunchGradleTestTool.java >> /tmp/phase3_result.txt 2>&1
git add src/main/java/com/nanik/tracepilot/tools/impl/AsyncStackTraceTool.java >> /tmp/phase3_result.txt 2>&1
git add src/main/java/com/nanik/tracepilot/mcp/McpServer.java >> /tmp/phase3_result.txt 2>&1
git add check_git.sh >> /tmp/phase3_result.txt 2>&1

echo "" >> /tmp/phase3_result.txt
echo "=== Committing ===" >> /tmp/phase3_result.txt
git commit -m "Phase 3: Add debug_launch_gradle_test and async_stack_trace tools

Gradle Integration:
- debug_launch_gradle_test: Launch Gradle test with --debug-jvm and auto-attach
  - Runs ./gradlew test --debug-jvm with test filter
  - Waits for debug port (default 5005) to become available
  - Automatically attaches debugger when ready
  - Solves Gradle test timeout issues during debugging

Async Debugging:
- async_stack_trace: Show async/reactive stack traces across thread boundaries
  - Detects async frameworks (CompletableFuture, Reactor, RxJava, etc.)
  - Finds related threads in same thread pool
  - Highlights async-related frames
  - Shows logical call flow for reactive code" >> /tmp/phase3_result.txt 2>&1

echo "" >> /tmp/phase3_result.txt
echo "=== Git log ===" >> /tmp/phase3_result.txt
git log --oneline -5 >> /tmp/phase3_result.txt 2>&1

echo "DONE" >> /tmp/phase3_result.txt

