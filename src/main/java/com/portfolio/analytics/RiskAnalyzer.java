package com.portfolio.analytics;

import com.portfolio.model.PriceHistory;
import com.portfolio.model.Stock;
import com.portfolio.service.PortfolioService;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RiskAnalyzer {

    private static final double RISK_FREE_RATE = 0.05; // 5% annual (approximate US T-bill rate)
    private static final int HISTORY_DAYS = 30;

    private final PortfolioService portfolioService;

    public RiskAnalyzer(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    /**
     * Volatility = annualized standard deviation of daily returns.
     * A higher value means more risk.
     */
    public double calculateVolatility(String symbol) {
        List<PriceHistory> history = portfolioService.getHistory(symbol, HISTORY_DAYS);
        if (history.size() < 2) return 0;

        double[] returns = dailyReturns(history);
        double mean = Arrays.stream(returns).average().orElse(0);
        double variance = Arrays.stream(returns)
                .map(r -> Math.pow(r - mean, 2))
                .average().orElse(0);

        double dailyStdDev = Math.sqrt(variance);
        return dailyStdDev * Math.sqrt(252); // annualize (252 trading days)
    }

    /**
     * Sharpe Ratio = (portfolio return - risk-free rate) / portfolio volatility.
     * > 1.0 is considered good; > 2.0 is excellent.
     */
    public double calculateSharpeRatio(String symbol) {
        double annualReturn = annualizedReturn(symbol);
        double volatility = calculateVolatility(symbol);
        if (volatility == 0) return 0;
        return (annualReturn - RISK_FREE_RATE) / volatility;
    }

    /**
     * Moving average for trend detection.
     */
    public double calculateMovingAverage(String symbol, int windowDays) {
        List<PriceHistory> history = portfolioService.getHistory(symbol, windowDays);
        if (history.isEmpty()) return 0;
        return history.stream()
                .mapToDouble(PriceHistory::getClosePrice)
                .average().orElse(0);
    }

    /**
     * Diversification score: ratio of unique sectors to total holdings.
     * 1.0 = fully diversified (each stock in a different sector).
     */
    public double calculateDiversificationScore() {
        Collection<Stock> stocks = portfolioService.getPortfolio();
        if (stocks.isEmpty()) return 0;

        Set<String> uniqueSectors = new HashSet<>();
        for (Stock s : stocks) uniqueSectors.add(s.getSector().toLowerCase());

        return (double) uniqueSectors.size() / stocks.size();
    }

    /**
     * Portfolio-level weighted average Sharpe Ratio.
     */
    public double portfolioSharpeRatio() {
        Collection<Stock> stocks = portfolioService.getPortfolio();
        if (stocks.isEmpty()) return 0;

        double totalValue = portfolioService.getTotalCurrentValue();
        double weightedSharpe = 0;
        for (Stock stock : stocks) {
            double weight = stock.getCurrentValue() / totalValue;
            weightedSharpe += weight * calculateSharpeRatio(stock.getSymbol());
        }
        return weightedSharpe;
    }

    /**
     * True Beta = Covariance(stock returns, market returns) / Variance(market returns).
     * Uses SPY (S&P 500 ETF) as the market proxy, fetched via the same data source.
     * Beta > 1 means the stock moves more than the market; < 1 means less; < 0 means opposite.
     */
    public double calculateBeta(String symbol) {
        List<PriceHistory> stockHistory = portfolioService.getHistory(symbol, HISTORY_DAYS);
        List<PriceHistory> marketHistory = portfolioService.getHistory("SPY", HISTORY_DAYS);

        if (stockHistory.size() < 2 || marketHistory.size() < 2) return 1.0;

        double[] stockReturns = dailyReturns(stockHistory);
        double[] marketReturns = dailyReturns(marketHistory);

        // Align lengths in case histories differ by a day
        int len = Math.min(stockReturns.length, marketReturns.length);
        if (len < 2) return 1.0;

        double meanStock  = mean(stockReturns,  len);
        double meanMarket = mean(marketReturns, len);

        double covariance = 0;
        double marketVariance = 0;
        for (int i = 0; i < len; i++) {
            covariance    += (stockReturns[i]  - meanStock)  * (marketReturns[i] - meanMarket);
            marketVariance += Math.pow(marketReturns[i] - meanMarket, 2);
        }
        covariance    /= len;
        marketVariance /= len;

        if (marketVariance == 0) return 1.0;
        return covariance / marketVariance;
    }

    private double mean(double[] arr, int len) {
        double sum = 0;
        for (int i = 0; i < len; i++) sum += arr[i];
        return sum / len;
    }

    public void printStockRiskReport(String symbol) {
        System.out.println("\n--- Risk Report: " + symbol + " ---");
        System.out.printf("  Annualized Volatility : %.2f%%  (lower = less risky)%n",
                calculateVolatility(symbol) * 100);
        System.out.printf("  Sharpe Ratio          : %.2f   (>1 good, >2 excellent)%n",
                calculateSharpeRatio(symbol));
        System.out.printf("  Beta                  : %.2f   (>1 more volatile than market)%n",
                calculateBeta(symbol));
        System.out.printf("  30d Moving Average    : $%.2f%n",
                calculateMovingAverage(symbol, 30));
    }

    public void printPortfolioRiskSummary() {
        System.out.println("\n====== Portfolio Risk Summary ======");
        System.out.printf("  Diversification Score : %.2f  (1.0 = max diversified)%n",
                calculateDiversificationScore());
        System.out.printf("  Weighted Sharpe Ratio : %.2f%n", portfolioSharpeRatio());
        System.out.println("====================================");
    }

    private double[] dailyReturns(List<PriceHistory> history) {
        double[] returns = new double[history.size() - 1];
        for (int i = 1; i < history.size(); i++) {
            double prev = history.get(i - 1).getClosePrice();
            double curr = history.get(i).getClosePrice();
            returns[i - 1] = (curr - prev) / prev;
        }
        return returns;
    }

    private double annualizedReturn(String symbol) {
        List<PriceHistory> history = portfolioService.getHistory(symbol, HISTORY_DAYS);
        if (history.size() < 2) return 0;
        double start = history.get(0).getClosePrice();
        double end = history.get(history.size() - 1).getClosePrice();
        double periodReturn = (end - start) / start;
        return periodReturn * (252.0 / history.size()); // annualize
    }
}
