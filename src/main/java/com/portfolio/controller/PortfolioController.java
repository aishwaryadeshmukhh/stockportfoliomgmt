package com.portfolio.controller;

import com.portfolio.analytics.RiskAnalyzer;
import com.portfolio.api.GroqClient;
import com.portfolio.model.Stock;
import com.portfolio.service.PortfolioService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final RiskAnalyzer riskAnalyzer;
    private final GroqClient groqClient;

    public PortfolioController(PortfolioService portfolioService, RiskAnalyzer riskAnalyzer, GroqClient groqClient) {
        this.portfolioService = portfolioService;
        this.riskAnalyzer = riskAnalyzer;
        this.groqClient = groqClient;
    }

    // GET /api/portfolio — all holdings with P&L
    @GetMapping("/portfolio")
    public List<Map<String, Object>> getPortfolio() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Stock s : portfolioService.getPortfolio()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("symbol",          s.getSymbol());
            row.put("sector",          s.getSector());
            row.put("buyPrice",        s.getBuyPrice());
            row.put("currentPrice",    s.getCurrentPrice());
            row.put("quantity",        s.getQuantity());
            row.put("investedValue",   s.getInvestedValue());
            row.put("currentValue",    s.getCurrentValue());
            row.put("profitLoss",      s.getProfitLoss());
            row.put("profitLossPct",   s.getProfitLossPercent());
            result.add(row);
        }
        return result;
    }

    // GET /api/summary — portfolio-level totals
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalInvested",      portfolioService.getTotalInvested());
        summary.put("totalCurrentValue",  portfolioService.getTotalCurrentValue());
        summary.put("totalProfitLoss",    portfolioService.getTotalProfitLoss());
        summary.put("totalProfitLossPct", portfolioService.getTotalProfitLossPercent());
        summary.put("holdingsCount",      portfolioService.getPortfolio().size());
        return summary;
    }

    // GET /api/risk — risk metrics for all stocks + portfolio level
    @GetMapping("/risk")
    public Map<String, Object> getRisk() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("portfolioSharpeRatio",   riskAnalyzer.portfolioSharpeRatio());
        result.put("diversificationScore",   riskAnalyzer.calculateDiversificationScore());

        List<Map<String, Object>> stocks = new ArrayList<>();
        for (Stock s : portfolioService.getPortfolio()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("symbol",     s.getSymbol());
            row.put("volatility", riskAnalyzer.calculateVolatility(s.getSymbol()) * 100);
            row.put("sharpe",     riskAnalyzer.calculateSharpeRatio(s.getSymbol()));
            row.put("beta",       riskAnalyzer.calculateBeta(s.getSymbol()));
            row.put("movingAvg",  riskAnalyzer.calculateMovingAverage(s.getSymbol(), 30));
            stocks.add(row);
        }
        result.put("stocks", stocks);
        return result;
    }

    // POST /api/portfolio/add — add a stock
    @PostMapping("/portfolio/add")
    public Map<String, String> addStock(@RequestBody Map<String, String> body) {
        try {
            portfolioService.addStock(
                body.get("symbol"),
                body.get("sector"),
                Double.parseDouble(body.get("buyPrice")),
                Integer.parseInt(body.get("quantity")),
                LocalDate.parse(body.get("buyDate"))
            );
            return Map.of("status", "ok", "message", body.get("symbol") + " added.");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    // DELETE /api/portfolio/{symbol} — remove a stock
    @DeleteMapping("/portfolio/{symbol}")
    public Map<String, String> removeStock(@PathVariable String symbol) {
        boolean removed = portfolioService.removeStock(symbol);
        return Map.of("status", removed ? "ok" : "error",
                      "message", removed ? symbol + " removed." : symbol + " not found.");
    }

    // POST /api/refresh — refresh all prices
    @PostMapping("/refresh")
    public Map<String, String> refresh() {
        portfolioService.refreshPrices();
        return Map.of("status", "ok", "message", "Prices refreshed.");
    }

    // GET /api/insights — LLM-generated natural language analysis of portfolio
    @GetMapping("/insights")
    public Map<String, String> getInsights() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a financial analyst. Analyze this stock portfolio and give a concise 3-4 sentence interpretation. ");
        prompt.append("Focus on overall performance, risk-adjusted returns, and one actionable observation. Be direct, no fluff.\n\n");

        prompt.append(String.format("Portfolio: total invested $%.2f, current value $%.2f, return %.2f%%.\n",
                portfolioService.getTotalInvested(),
                portfolioService.getTotalCurrentValue(),
                portfolioService.getTotalProfitLossPercent()));

        prompt.append(String.format("Portfolio Sharpe Ratio: %.2f. Diversification score: %.2f (3 sectors).\n",
                riskAnalyzer.portfolioSharpeRatio(),
                riskAnalyzer.calculateDiversificationScore()));

        prompt.append("Individual stocks:\n");
        for (Stock s : portfolioService.getPortfolio()) {
            prompt.append(String.format("- %s (%s): buy $%.2f, current $%.2f, P&L %.2f%%, volatility %.1f%%, beta %.2f, sharpe %.2f\n",
                    s.getSymbol(), s.getSector(),
                    s.getBuyPrice(), s.getCurrentPrice(), s.getProfitLossPercent(),
                    riskAnalyzer.calculateVolatility(s.getSymbol()) * 100,
                    riskAnalyzer.calculateBeta(s.getSymbol()),
                    riskAnalyzer.calculateSharpeRatio(s.getSymbol())));
        }

        try {
            String insight = groqClient.getInsights(prompt.toString());
            return Map.of("status", "ok", "insight", insight);
        } catch (IOException e) {
            return Map.of("status", "error", "insight", "Could not generate insights: " + e.getMessage());
        }
    }
}
