package com.portfolio.api;

import com.portfolio.model.PriceHistory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Offline fallback using hardcoded 30-day historical data.
 * Used when Alpha Vantage API is unavailable or API key is not set.
 */
public class FallbackDataProvider {

    // Approximate 30-day close prices for demo stocks
    private static final Map<String, double[]> HISTORICAL_PRICES = new HashMap<>();

    static {
        HISTORICAL_PRICES.put("AAPL", new double[]{
            189.3, 188.6, 191.2, 190.5, 192.3, 193.1, 191.8, 194.5, 195.0, 193.7,
            196.2, 197.5, 198.1, 196.8, 199.0, 200.3, 198.7, 201.2, 202.5, 200.9,
            203.1, 204.4, 202.8, 205.0, 206.3, 204.7, 207.2, 208.5, 206.9, 209.1
        });
        HISTORICAL_PRICES.put("GOOGL", new double[]{
            175.2, 174.8, 176.5, 177.1, 175.9, 178.3, 179.0, 177.6, 180.2, 181.5,
            179.8, 182.4, 183.1, 181.7, 184.3, 185.0, 183.6, 186.2, 187.5, 185.9,
            188.4, 189.1, 187.7, 190.3, 191.0, 189.6, 192.2, 193.5, 191.9, 194.4
        });
        HISTORICAL_PRICES.put("MSFT", new double[]{
            415.2, 413.8, 417.5, 418.1, 416.7, 419.3, 420.0, 418.4, 421.2, 422.5,
            420.8, 423.4, 424.1, 422.7, 425.3, 426.0, 424.4, 427.2, 428.5, 426.9,
            429.4, 430.1, 428.5, 431.3, 432.0, 430.4, 433.2, 434.5, 432.9, 435.4
        });
        HISTORICAL_PRICES.put("JPM", new double[]{
            198.5, 197.2, 199.8, 200.4, 198.9, 201.5, 202.2, 200.6, 203.4, 204.7,
            202.8, 205.6, 206.3, 204.7, 207.5, 208.2, 206.4, 209.4, 210.7, 208.9,
            211.8, 212.5, 210.7, 213.7, 214.4, 212.6, 215.6, 216.9, 215.1, 218.1
        });
        HISTORICAL_PRICES.put("TSLA", new double[]{
            245.3, 242.1, 248.7, 251.2, 247.8, 253.4, 256.1, 252.5, 258.3, 261.0,
            257.4, 263.2, 265.9, 262.1, 267.9, 270.6, 266.8, 272.8, 275.5, 271.5,
            277.5, 280.2, 276.2, 282.4, 285.1, 281.1, 287.3, 290.0, 286.0, 292.2
        });
    }

    public static double getCurrentPrice(String symbol) {
        double[] prices = HISTORICAL_PRICES.getOrDefault(symbol.toUpperCase(),
                new double[]{100.0, 101.0, 99.5, 102.0, 100.5});
        return prices[prices.length - 1];
    }

    public static List<PriceHistory> getHistory(String symbol, int days) {
        double[] prices = HISTORICAL_PRICES.getOrDefault(symbol.toUpperCase(),
                new double[]{100.0, 101.0, 99.5, 102.0, 100.5});
        List<PriceHistory> history = new ArrayList<>();
        int limit = Math.min(days, prices.length);
        LocalDate date = LocalDate.now().minusDays(limit);
        for (int i = 0; i < limit; i++) {
            history.add(new PriceHistory(date.plusDays(i), prices[i]));
        }
        return history;
    }

    public static boolean isSupported(String symbol) {
        return HISTORICAL_PRICES.containsKey(symbol.toUpperCase());
    }
}
