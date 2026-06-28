package com.portfolio.dto;

public class StockDTO {
    private String symbol;
    private String sector;
    private double buyPrice;
    private double currentPrice;
    private int quantity;
    private double investedValue;
    private double currentValue;
    private double profitLoss;
    private double profitLossPct;

    public StockDTO(String symbol, String sector, double buyPrice, double currentPrice,
                    int quantity, double investedValue, double currentValue,
                    double profitLoss, double profitLossPct) {
        this.symbol = symbol;
        this.sector = sector;
        this.buyPrice = buyPrice;
        this.currentPrice = currentPrice;
        this.quantity = quantity;
        this.investedValue = investedValue;
        this.currentValue = currentValue;
        this.profitLoss = profitLoss;
        this.profitLossPct = profitLossPct;
    }

    public String getSymbol() { return symbol; }
    public String getSector() { return sector; }
    public double getBuyPrice() { return buyPrice; }
    public double getCurrentPrice() { return currentPrice; }
    public int getQuantity() { return quantity; }
    public double getInvestedValue() { return investedValue; }
    public double getCurrentValue() { return currentValue; }
    public double getProfitLoss() { return profitLoss; }
    public double getProfitLossPct() { return profitLossPct; }
}
