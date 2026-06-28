package com.portfolio.dto;

public class StockRequest {
    private String symbol;
    private String sector;
    private double buyPrice;
    private int quantity;
    private String buyDate;

    public String getSymbol() { return symbol; }
    public String getSector() { return sector; }
    public double getBuyPrice() { return buyPrice; }
    public int getQuantity() { return quantity; }
    public String getBuyDate() { return buyDate; }
}
