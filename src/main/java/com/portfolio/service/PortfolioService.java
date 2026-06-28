package com.portfolio.service;

import com.portfolio.api.AlphaVantageClient;
import com.portfolio.api.FallbackDataProvider;
import com.portfolio.model.PriceHistory;
import com.portfolio.model.Stock;
import com.portfolio.repository.StockRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@Service
public class PortfolioService {

    private final StockRepository stockRepository;
    private final Map<String, List<PriceHistory>> historyCache = new HashMap<>();
    private AlphaVantageClient apiClient;
    private boolean useApi = false;

    public PortfolioService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public void enableApi(String apiKey) {
        this.apiClient = new AlphaVantageClient(apiKey);
        this.useApi = true;
    }

    public void addStock(String symbol, String sector, double buyPrice, int quantity, LocalDate buyDate) {
        symbol = symbol.toUpperCase();
        if (stockRepository.existsById(symbol)) {
            System.out.println("Stock " + symbol + " already exists. Remove it first to re-add.");
            return;
        }
        stockRepository.save(new Stock(symbol, sector, buyPrice, quantity, buyDate));
        System.out.println("Added " + symbol + " to portfolio.");
    }

    public boolean removeStock(String symbol) {
        symbol = symbol.toUpperCase();
        if (!stockRepository.existsById(symbol)) return false;
        stockRepository.deleteById(symbol);
        historyCache.remove(symbol);
        return true;
    }

    @Scheduled(fixedRateString = "${price.refresh.interval:86400000}")
    public void refreshPrices() {
        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) return;

        if (useApi) {
            System.out.println("Fetching live prices from Alpha Vantage (13s delay between each)...");
        } else {
            System.out.println("Loading demo prices...");
        }

        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            try {
                if (useApi) {
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
                stockRepository.save(stock);
            } catch (IOException e) {
                System.out.println("  " + stock.getSymbol() + ": fetch failed (" + e.getMessage() + "), using demo price.");
                stock.setCurrentPrice(FallbackDataProvider.getCurrentPrice(stock.getSymbol()));
                stockRepository.save(stock);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Done.\n");
    }

    public List<PriceHistory> getHistory(String symbol, int days) {
        symbol = symbol.toUpperCase();
        if (historyCache.containsKey(symbol)) return historyCache.get(symbol);

        List<PriceHistory> history;
        if (useApi) {
            try {
                if (!historyCache.isEmpty()) Thread.sleep(apiClient.getDelayMs());
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

    public List<Stock> getPortfolio() {
        return stockRepository.findAll();
    }

    public Stock getStock(String symbol) {
        return stockRepository.findById(symbol.toUpperCase()).orElse(null);
    }

    public boolean isEmpty() {
        return stockRepository.count() == 0;
    }

    public double getTotalInvested() {
        return stockRepository.findAll().stream().mapToDouble(Stock::getInvestedValue).sum();
    }

    public double getTotalCurrentValue() {
        return stockRepository.findAll().stream().mapToDouble(Stock::getCurrentValue).sum();
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
        pq.addAll(stockRepository.findAll());
        List<Stock> result = new ArrayList<>();
        for (int i = 0; i < n && !pq.isEmpty(); i++) result.add(pq.poll());
        return result;
    }
}
