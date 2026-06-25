package com.portfolio.service;

import com.portfolio.api.AlphaVantageClient;
import com.portfolio.api.FallbackDataProvider;
import com.portfolio.model.PriceHistory;
import com.portfolio.model.Stock;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class PortfolioService {

    private final Map<String, Stock> portfolio = new LinkedHashMap<>();
    private final Map<String, List<PriceHistory>> historyCache = new HashMap<>();
    private AlphaVantageClient apiClient;
    private boolean useApi = false;

    public void enableApi(String apiKey) {
        this.apiClient = new AlphaVantageClient(apiKey);
        this.useApi = true;
    }

    public void addStock(String symbol, String sector, double buyPrice, int quantity, LocalDate buyDate) {
        if (portfolio.containsKey(symbol.toUpperCase())) {
            System.out.println("Stock " + symbol + " already exists. Remove it first to re-add.");
            return;
        }
        portfolio.put(symbol.toUpperCase(), new Stock(symbol.toUpperCase(), sector, buyPrice, quantity, buyDate));
        System.out.println("Added " + symbol.toUpperCase() + " to portfolio.");
    }

    public boolean removeStock(String symbol) {
        return portfolio.remove(symbol.toUpperCase()) != null;
    }

    public void refreshPrices() {
        if (useApi) {
            System.out.println("Fetching live prices from Alpha Vantage (13s delay between each)...");
        } else {
            System.out.println("Loading demo prices...");
        }

        List<Stock> stocks = new ArrayList<>(portfolio.values());
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            try {
                if (useApi) {
                    // Delay before every request except the first
                    if (i > 0) {
                        System.out.println("  Waiting 13s before next request...");
                        Thread.sleep(apiClient.getDelayMs());
                    }
                    double price = apiClient.fetchCurrentPrice(stock.getSymbol());
                    stock.setCurrentPrice(price);
                    System.out.printf("  %-5s -> $%.2f%n", stock.getSymbol(), price);
                } else {
                    stock.setCurrentPrice(FallbackDataProvider.getCurrentPrice(stock.getSymbol()));
                }
            } catch (IOException e) {
                System.out.println("  " + stock.getSymbol() + ": fetch failed (" + e.getMessage() + "), using demo price.");
                stock.setCurrentPrice(FallbackDataProvider.getCurrentPrice(stock.getSymbol()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Done.\n");
    }

    public List<PriceHistory> getHistory(String symbol, int days) {
        symbol = symbol.toUpperCase();
        if (historyCache.containsKey(symbol)) {
            return historyCache.get(symbol);
        }
        List<PriceHistory> history;
        if (useApi) {
            try {
                if (!historyCache.isEmpty()) {
                    Thread.sleep(apiClient.getDelayMs());
                }
                history = apiClient.fetchDailyHistory(symbol, days);
            } catch (IOException e) {
                history = FallbackDataProvider.getHistory(symbol, days);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                history = FallbackDataProvider.getHistory(symbol, days);
            }
        } else {
            history = FallbackDataProvider.getHistory(symbol, days);
        }
        historyCache.put(symbol, history);
        return history;
    }

    public Collection<Stock> getPortfolio() {
        return portfolio.values();
    }

    public Stock getStock(String symbol) {
        return portfolio.get(symbol.toUpperCase());
    }

    public boolean isEmpty() {
        return portfolio.isEmpty();
    }

    public double getTotalInvested() {
        return portfolio.values().stream().mapToDouble(Stock::getInvestedValue).sum();
    }

    public double getTotalCurrentValue() {
        return portfolio.values().stream().mapToDouble(Stock::getCurrentValue).sum();
    }

    public double getTotalProfitLoss() {
        return getTotalCurrentValue() - getTotalInvested();
    }

    public double getTotalProfitLossPercent() {
        double invested = getTotalInvested();
        if (invested == 0) return 0;
        return (getTotalProfitLoss() / invested) * 100;
    }

    public List<Stock> getTopPerformers(int n) {
        PriorityQueue<Stock> pq = new PriorityQueue<>(
                (a, b) -> Double.compare(b.getProfitLossPercent(), a.getProfitLossPercent())
        );
        pq.addAll(portfolio.values());
        List<Stock> result = new ArrayList<>();
        for (int i = 0; i < n && !pq.isEmpty(); i++) {
            result.add(pq.poll());
        }
        return result;
    }
}
