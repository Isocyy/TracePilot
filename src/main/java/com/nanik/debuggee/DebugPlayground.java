package com.nanik.debuggee;

import java.util.*;
import java.util.concurrent.*;

/**
 * DebugPlayground - A comprehensive test program for TracePilot debugger.
 * 
 * This program covers all debugging scenarios:
 * - Basic control flow (loops, conditions)
 * - Method calls (step into/over/out)
 * - Multiple threads and synchronization
 * - Exception handling (caught/uncaught)
 * - Various data types (primitives, objects, arrays, collections)
 * - Field access/modification (watchpoints)
 * - Recursion
 * - Lambdas and streams
 * - Inner classes
 * - Static methods and fields
 * 
 * Run with: java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 com.nanik.debuggee.DebugPlayground
 */
public class DebugPlayground {
    
    // ========== Static Fields (for watchpoints) ==========
    private static int staticCounter = 0;
    private static final String APP_NAME = "DebugPlayground";
    
    // ========== Instance Fields (for watchpoints) ==========
    private int instanceCounter = 0;
    private String status = "initialized";
    private List<String> messages = new ArrayList<>();
    private Map<String, Integer> scores = new HashMap<>();
    
    // ========== Entry Point ==========
    public static void main(String[] args) {
        System.out.println("=== DebugPlayground Started ===");      // Line 37 - First breakpoint
        System.out.println("PID: " + ProcessHandle.current().pid());
        
        DebugPlayground playground = new DebugPlayground();
        
        // Phase 1: Basic Operations
        playground.runBasicOperations();
        
        // Phase 2: Data Types
        playground.demonstrateDataTypes();
        
        // Phase 3: Method Calls (for stepping)
        playground.methodCallChain();
        
        // Phase 4: Recursion
        int fib = playground.fibonacci(10);
        System.out.println("Fibonacci(10) = " + fib);
        
        // Phase 5: Exception Handling
        playground.demonstrateExceptions();
        
        // Phase 6: Threads
        playground.runThreadDemo();
        
        // Phase 7: Lambdas and Streams
        playground.demonstrateLambdas();
        
        // Phase 8: Inner Classes
        playground.demonstrateInnerClasses();
        
        System.out.println("=== DebugPlayground Finished ===");     // Line 63
    }
    
    // ========== Phase 1: Basic Operations ==========
    public void runBasicOperations() {
        System.out.println("\n--- Basic Operations ---");
        
        // Simple loop - good for breakpoints and stepping
        int sum = 0;
        for (int i = 1; i <= 5; i++) {          // Line 72 - Loop breakpoint
            sum += i;                            // Line 73 - Step test
            staticCounter++;                     // Watchpoint: static field modification
            instanceCounter++;                   // Watchpoint: instance field modification
        }
        System.out.println("Sum 1-5: " + sum);
        
        // Conditional logic
        String result;
        if (sum > 10) {                          // Line 80 - Condition test
            result = "Greater than 10";
        } else {
            result = "10 or less";
        }
        status = result;                         // Watchpoint: field modification
        System.out.println("Result: " + result);
    }
    
    // ========== Phase 2: Data Types ==========
    public void demonstrateDataTypes() {
        System.out.println("\n--- Data Types ---");
        
        // Primitives
        byte b = 127;
        short s = 32000;
        int i = 2147483647;
        long l = 9223372036854775807L;
        float f = 3.14159f;
        double d = 2.718281828;
        char c = 'A';
        boolean bool = true;
        
        // Arrays
        int[] intArray = {1, 2, 3, 4, 5};
        String[] stringArray = {"hello", "world", "debug"};
        int[][] matrix = {{1, 2}, {3, 4}, {5, 6}};
        
        // Objects
        String str = "Hello, Debugger!";
        Integer boxed = Integer.valueOf(42);
        Object nullObj = null;
        
        // Collections
        messages.add("First message");
        messages.add("Second message");
        scores.put("Alice", 100);
        scores.put("Bob", 85);
        
        // Breakpoint here to inspect all variables      // Line 117
        System.out.println("Data types demo at line 118");
    }
    
    // ========== Phase 3: Method Call Chain ==========
    public void methodCallChain() {
        System.out.println("\n--- Method Call Chain ---");
        String result = levelOne("start");               // Line 124 - Step into
        System.out.println("Chain result: " + result);
    }
    
    private String levelOne(String input) {
        String modified = input + "-L1";
        return levelTwo(modified);                       // Line 130 - Step into
    }
    
    private String levelTwo(String input) {
        String modified = input + "-L2";
        return levelThree(modified);                     // Line 135 - Step into/out
    }
    
    private String levelThree(String input) {
        return input + "-L3-END";                        // Line 139 - Deepest point
    }
    
    // ========== Phase 4: Recursion ==========
    public int fibonacci(int n) {
        if (n <= 1) {                                    // Line 144 - Recursive base
            return n;
        }
        return fibonacci(n - 1) + fibonacci(n - 2);      // Line 147 - Recursive call
    }

    // ========== Phase 5: Exception Handling ==========
    public void demonstrateExceptions() {
        System.out.println("\n--- Exception Handling ---");

        // Caught exception
        try {
            riskyOperation(true);                        // Line 156 - Will throw
        } catch (IllegalArgumentException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        // Nested try-catch
        try {
            try {
                riskyOperation(true);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Wrapped", e); // Line 166 - Rethrow
            }
        } catch (RuntimeException e) {
            System.out.println("Caught wrapped: " + e.getMessage());
        }

        // Safe operation
        try {
            riskyOperation(false);                       // Line 173 - No throw
            System.out.println("Safe operation completed");
        } catch (Exception e) {
            System.out.println("Unexpected: " + e);
        }
    }

    private void riskyOperation(boolean shouldFail) {
        if (shouldFail) {
            throw new IllegalArgumentException("Intentional failure!");  // Line 182
        }
        status = "safe";
    }

    // ========== Phase 6: Threads ==========
    public void runThreadDemo() {
        System.out.println("\n--- Thread Demo ---");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(3);

        // Create worker threads
        for (int i = 0; i < 3; i++) {
            final int workerId = i;
            Thread worker = new Thread(() -> {
                try {
                    startLatch.await();                  // Wait for signal
                    doWork(workerId);                    // Line 199 - Thread breakpoint
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }, "Worker-" + i);
            worker.start();
        }

        // Start all workers
        startLatch.countDown();

        // Wait for completion
        try {
            doneLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Synchronization demo
        synchronizedIncrement();

        System.out.println("All workers done. Counter: " + instanceCounter);
    }

    private void doWork(int workerId) {
        for (int i = 0; i < 3; i++) {
            synchronized (this) {                        // Line 226 - Monitor
                instanceCounter++;
                System.out.println("Worker-" + workerId + " iteration " + i);
            }
            try {
                Thread.sleep(10);                        // Line 231 - Thread state: TIMED_WAITING
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private synchronized void synchronizedIncrement() {
        for (int i = 0; i < 5; i++) {
            instanceCounter++;                           // Line 241 - Synchronized access
        }
    }

    // ========== Phase 7: Lambdas and Streams ==========
    public void demonstrateLambdas() {
        System.out.println("\n--- Lambdas and Streams ---");

        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        // Lambda breakpoint
        int sumOfEvens = numbers.stream()
            .filter(n -> n % 2 == 0)                     // Line 253 - Lambda breakpoint
            .mapToInt(n -> n * 2)                        // Line 254 - Another lambda
            .sum();
        System.out.println("Sum of doubled evens: " + sumOfEvens);

        // Runnable lambda
        Runnable task = () -> {
            System.out.println("Lambda runnable executing"); // Line 260
        };
        task.run();

        // Consumer with closure
        String prefix = "Item: ";
        messages.forEach(msg -> {
            System.out.println(prefix + msg);            // Line 266 - Closure
        });
    }

    // ========== Phase 8: Inner Classes ==========
    public void demonstrateInnerClasses() {
        System.out.println("\n--- Inner Classes ---");

        // Regular inner class
        Counter counter = new Counter(10);
        counter.increment();
        counter.increment();
        System.out.println("Counter value: " + counter.getValue());

        // Anonymous inner class
        Runnable anon = new Runnable() {
            private int localCount = 0;

            @Override
            public void run() {
                localCount++;                            // Line 285 - Anonymous class
                System.out.println("Anonymous runnable, count: " + localCount);
            }
        };
        anon.run();

        // Static nested class
        StaticHelper helper = new StaticHelper();
        helper.help();
    }

    // Regular inner class
    public class Counter {
        private int value;

        public Counter(int initial) {
            this.value = initial;                        // Line 299 - Inner class constructor
        }

        public void increment() {
            value++;                                     // Line 303 - Inner class method
            instanceCounter++;                           // Access outer class field
        }

        public int getValue() {
            return value;
        }
    }

    // Static nested class
    public static class StaticHelper {
        public void help() {
            staticCounter++;                             // Line 315 - Access static only
            System.out.println("StaticHelper called, counter: " + staticCounter);
        }
    }
}

