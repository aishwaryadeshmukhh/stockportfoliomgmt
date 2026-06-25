package com.portfolio.model;

import java.time.LocalDate;

public class PriceHistory {
    private LocalDate date;
    private double closePrice;

    public PriceHistory(LocalDate date, double closePrice) {
        this.date = date;
        this.closePrice = closePrice;
    }

    public LocalDate getDate() { return date; }
    public double getClosePrice() { return closePrice; }
}
