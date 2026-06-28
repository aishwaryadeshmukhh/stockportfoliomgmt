package com.portfolio.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    private String symbol;
    private String sector;
    private double buyPrice;
    private int quantity;
    private LocalDate buyDate;
    private double currentPrice;

    public Stock() {}

    public Stock(String symbol, String sector, double buyPrice, int quantity, LocalDate buyDate) {
        this.symbol = symbol;
        this.sector = sector;
        this.buyPrice = buyPrice;
        this.quantity = quantity;
        this.buyDate = buyDate;
        this.currentPrice = buyPrice;
    }

    public double getProfitLoss() {
        return (currentPrice - buyPrice) * quantity;
    }

    public double getProfitLossPercent() {
        return ((currentPrice - buyPrice) / buyPrice) * 100;
    }

    public double getCurrentValue() {
        return currentPrice * quantity;
    }

    public double getInvestedValue() {
        return buyPrice * quantity;
    }

    public String getSymbol() { return symbol; }
    public String getSector() { return sector; }
    public double getBuyPrice() { return buyPrice; }
    public int getQuantity() { return quantity; }
    public LocalDate getBuyDate() { return buyDate; }
    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    @Override
    public String toString() {
        return String.format("%-6s | %-15s | Buy: $%8.2f | Current: $%8.2f | Qty: %4d | P&L: $%9.2f (%+.2f%%)",
                symbol, sector, buyPrice, currentPrice, quantity, getProfitLoss(), getProfitLossPercent());
    }
}
