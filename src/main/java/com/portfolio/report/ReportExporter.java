package com.portfolio.report;

import com.opencsv.CSVWriter;
import com.portfolio.analytics.RiskAnalyzer;
import com.portfolio.model.Stock;
import com.portfolio.service.PortfolioService;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

public class ReportExporter {

    private final PortfolioService portfolioService;
    private final RiskAnalyzer riskAnalyzer;

    public ReportExporter(PortfolioService portfolioService, RiskAnalyzer riskAnalyzer) {
        this.portfolioService = portfolioService;
        this.riskAnalyzer = riskAnalyzer;
    }

    public String exportToCsv() throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "portfolio_report_" + timestamp + ".csv";

        try (CSVWriter writer = new CSVWriter(new FileWriter(filename))) {
            // Header
            writer.writeNext(new String[]{
                "Symbol", "Sector", "Buy Price", "Current Price", "Quantity",
                "Invested ($)", "Current Value ($)", "P&L ($)", "P&L (%)",
                "Volatility (%)", "Sharpe Ratio", "Beta"
            });

            // Rows
            Collection<Stock> stocks = portfolioService.getPortfolio();
            for (Stock s : stocks) {
                writer.writeNext(new String[]{
                    s.getSymbol(),
                    s.getSector(),
                    String.format("%.2f", s.getBuyPrice()),
                    String.format("%.2f", s.getCurrentPrice()),
                    String.valueOf(s.getQuantity()),
                    String.format("%.2f", s.getInvestedValue()),
                    String.format("%.2f", s.getCurrentValue()),
                    String.format("%.2f", s.getProfitLoss()),
                    String.format("%.2f", s.getProfitLossPercent()),
                    String.format("%.2f", riskAnalyzer.calculateVolatility(s.getSymbol()) * 100),
                    String.format("%.2f", riskAnalyzer.calculateSharpeRatio(s.getSymbol())),
                    String.format("%.2f", riskAnalyzer.calculateBeta(s.getSymbol()))
                });
            }

            // Summary row
            writer.writeNext(new String[]{});
            writer.writeNext(new String[]{
                "TOTAL", "", "", "", "",
                String.format("%.2f", portfolioService.getTotalInvested()),
                String.format("%.2f", portfolioService.getTotalCurrentValue()),
                String.format("%.2f", portfolioService.getTotalProfitLoss()),
                String.format("%.2f", portfolioService.getTotalProfitLossPercent()),
                "", String.format("%.2f", riskAnalyzer.portfolioSharpeRatio()),
                String.format("Div Score: %.2f", riskAnalyzer.calculateDiversificationScore())
            });
        }

        return filename;
    }
}
