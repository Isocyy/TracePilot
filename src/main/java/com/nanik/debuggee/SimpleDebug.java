package com.nanik.debuggee;

/**
 * Simple program for debugging test - waits for input so it doesn't exit.
 */
public class SimpleDebug {
    private static int counter = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("SimpleDebug started - waiting for debugger...");

        // Wait a bit to allow debugger to attach and set breakpoints
        Thread.sleep(2000);

        // Line 15 - good breakpoint location
        String message = "Hello Debugger!";
        System.out.println(message);

        // Line 19 - loop for stepping
        for (int i = 0; i < 5; i++) {
            counter++;
            process(i);
        }

        System.out.println("Counter: " + counter);
        System.out.println("SimpleDebug finished");
    }

    private static void process(int value) {
        // Line 29 - method for step into
        int result = value * 2;
        System.out.println("Processing: " + value + " -> " + result);
    }
}

