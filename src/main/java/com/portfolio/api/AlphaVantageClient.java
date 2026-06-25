package com.portfolio.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portfolio.model.PriceHistory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AlphaVantageClient {

    private static final String BASE_URL = "https://www.alphavantage.co/query";
    // Free tier allows 5 requests/minute, so wait 13s between calls to be safe
    private static final long DELAY_MS = 13_000;

    private final String apiKey;

    public AlphaVantageClient(String apiKey) {
        this.apiKey = apiKey;
    }

    public double fetchCurrentPrice(String symbol) throws IOException {
        String url = BASE_URL + "?function=GLOBAL_QUOTE&symbol=" + symbol + "&apikey=" + apiKey;
        String json = get(url);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject quote = root.getAsJsonObject("Global Quote");

        if (quote == null || !quote.has("05. price") || quote.get("05. price").getAsString().isEmpty()) {
            throw new IOException("No price data for " + symbol);
        }
        return Double.parseDouble(quote.get("05. price").getAsString());
    }

    public List<PriceHistory> fetchDailyHistory(String symbol, int days) throws IOException {
        String url = BASE_URL + "?function=TIME_SERIES_DAILY&symbol=" + symbol
                + "&outputsize=compact&apikey=" + apiKey;
        String json = get(url);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject timeSeries = root.getAsJsonObject("Time Series (Daily)");

        if (timeSeries == null) {
            throw new IOException("No history data for " + symbol);
        }

        List<PriceHistory> history = new ArrayList<>();
        int count = 0;
        for (Map.Entry<String, JsonElement> entry : timeSeries.entrySet()) {
            if (count >= days) break;
            LocalDate date = LocalDate.parse(entry.getKey());
            double close = Double.parseDouble(
                    entry.getValue().getAsJsonObject().get("4. close").getAsString());
            history.add(new PriceHistory(date, close));
            count++;
        }
        return history;
    }

    public long getDelayMs() {
        return DELAY_MS;
    }

    private String get(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);

        int status = conn.getResponseCode();
        if (status != 200) throw new IOException("HTTP " + status);

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
