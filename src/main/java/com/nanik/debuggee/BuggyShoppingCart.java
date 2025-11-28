package com.nanik.debuggee;

import java.util.*;

/**
 * A shopping cart with a subtle bug - can you find it?
 */
public class BuggyShoppingCart {
    
    private List<Item> items = new ArrayList<>();
    private double discountPercent = 0;
    
    public static void main(String[] args) {
        System.out.println("=== Shopping Cart Test ===\n");
        
        BuggyShoppingCart cart = new BuggyShoppingCart();
        
        // Add items
        cart.addItem("Laptop", 999.99, 1);
        cart.addItem("Mouse", 29.99, 2);
        cart.addItem("Keyboard", 79.99, 1);
        cart.addItem("Monitor", 299.99, 2);
        
        // Apply 10% discount
        cart.applyDiscount(10);
        
        // Calculate and display
        System.out.println("Items in cart:");
        cart.printItems();
        
        double subtotal = cart.calculateSubtotal();
        double discount = cart.calculateDiscount();
        double total = cart.calculateTotal();
        
        System.out.println("\n--- Summary ---");
        System.out.println("Subtotal: $" + String.format("%.2f", subtotal));
        System.out.println("Discount (10%): -$" + String.format("%.2f", discount));
        System.out.println("Total: $" + String.format("%.2f", total));
        
        // Verify the calculation
        double expectedTotal = 1739.93 * 0.90; // $1565.937
        System.out.println("\nExpected total: $" + String.format("%.2f", expectedTotal));
        
        if (Math.abs(total - expectedTotal) > 0.01) {
            System.out.println("ERROR: Total doesn't match expected value!");
            System.out.println("Difference: $" + String.format("%.2f", Math.abs(total - expectedTotal)));
        } else {
            System.out.println("SUCCESS: Calculation is correct!");
        }
    }
    
    public void addItem(String name, double price, int quantity) {
        items.add(new Item(name, price, quantity));
    }
    
    public void applyDiscount(double percent) {
        this.discountPercent = percent;
    }
    
    public double calculateSubtotal() {
        double subtotal = 0;
        for (int i = 0; i <= items.size(); i++) {  // BUG: <= should be <
            Item item = items.get(i);
            subtotal += item.price * item.quantity;
        }
        return subtotal;
    }
    
    public double calculateDiscount() {
        return calculateSubtotal() * (discountPercent / 100);
    }
    
    public double calculateTotal() {
        return calculateSubtotal() - calculateDiscount();
    }
    
    public void printItems() {
        for (Item item : items) {
            double lineTotal = item.price * item.quantity;
            System.out.printf("  %s x%d @ $%.2f = $%.2f%n", 
                item.name, item.quantity, item.price, lineTotal);
        }
    }
    
    // Inner class for items
    static class Item {
        String name;
        double price;
        int quantity;
        
        Item(String name, double price, int quantity) {
            this.name = name;
            this.price = price;
            this.quantity = quantity;
        }
    }
}

