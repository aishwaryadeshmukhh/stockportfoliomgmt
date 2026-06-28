package com.portfolio.controller;

import com.portfolio.analytics.RiskAnalyzer;
import com.portfolio.api.GroqClient;
import com.portfolio.dto.StockDTO;
import com.portfolio.dto.StockRequest;
import com.portfolio.model.Stock;
import com.portfolio.service.PortfolioService;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/portfolio")
    public ResponseEntity<List<StockDTO>> getPortfolio() {
        List<StockDTO> result = new ArrayList<>();
        for (Stock s : portfolioService.getPortfolio()) {
            result.add(new StockDTO(
                    s.getSymbol(), s.getSector(), s.getBuyPrice(), s.getCurrentPrice(),
                    s.getQuantity(), s.getInvestedValue(), s.getCurrentValue(),
                    s.getProfitLoss(), s.getProfitLossPercent()
            ));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalInvested",      portfolioService.getTotalInvested());
        summary.put("totalCurrentValue",  portfolioService.getTotalCurrentValue());
        summary.put("totalProfitLoss",    portfolioService.getTotalProfitLoss());
        summary.put("totalProfitLossPct", portfolioService.getTotalProfitLossPercent());
        summary.put("holdingsCount",      portfolioService.getPortfolio().size());
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/risk")
    public ResponseEntity<Map<String, Object>> getRisk() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("portfolioSharpeRatio", riskAnalyzer.portfolioSharpeRatio());
        result.put("diversificationScore", riskAnalyzer.calculateDiversificationScore());

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
        return ResponseEntity.ok(result);
    }

    @PostMapping("/portfolio/add")
    public ResponseEntity<Map<String, String>> addStock(@RequestBody StockRequest request) {
        if (request.getSymbol() == null || request.getSector() == null) {
            throw new IllegalArgumentException("Symbol and sector are required.");
        }
        portfolioService.addStock(
                request.getSymbol(),
                request.getSector(),
                request.getBuyPrice(),
                request.getQuantity(),
                LocalDate.parse(request.getBuyDate())
        );
        return ResponseEntity.ok(Map.of("status", "ok", "message", request.getSymbol().toUpperCase() + " added."));
    }

    @DeleteMapping("/portfolio/{symbol}")
    public ResponseEntity<Map<String, String>> removeStock(@PathVariable String symbol) {
        boolean removed = portfolioService.removeStock(symbol);
        if (!removed) throw new IllegalArgumentException(symbol + " not found in portfolio.");
        return ResponseEntity.ok(Map.of("status", "ok", "message", symbol + " removed."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh() {
        portfolioService.refreshPrices();
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Prices refreshed."));
    }

    @GetMapping("/insights")
    public ResponseEntity<Map<String, String>> getInsights() throws IOException {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a financial analyst. Analyze this stock portfolio and give a concise 3-4 sentence interpretation. ");
        prompt.append("Focus on overall performance, risk-adjusted returns, and one actionable observation. Be direct, no fluff.\n\n");
        prompt.append(String.format("Portfolio: total invested $%.2f, current value $%.2f, return %.2f%%.\n",
                portfolioService.getTotalInvested(),
                portfolioService.getTotalCurrentValue(),
                portfolioService.getTotalProfitLossPercent()));
        prompt.append(String.format("Portfolio Sharpe Ratio: %.2f. Diversification score: %.2f.\n",
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
        String insight = groqClient.getInsights(prompt.toString());
        return ResponseEntity.ok(Map.of("status", "ok", "insight", insight));
    }
}
