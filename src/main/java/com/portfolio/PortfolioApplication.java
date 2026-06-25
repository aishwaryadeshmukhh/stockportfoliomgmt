package com.portfolio;

import com.portfolio.service.PortfolioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.LocalDate;

@SpringBootApplication
public class PortfolioApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortfolioApplication.class, args);
    }

    @Bean
    CommandLineRunner loadDemoPortfolio(
            PortfolioService portfolioService,
            @Value("${alphavantage.apikey:}") String apiKey) {
        return args -> {
            String cleanApiKey = apiKey.replace("\"", "").trim();
            if (!cleanApiKey.isBlank()) {
                portfolioService.enableApi(cleanApiKey);
                System.out.println("Alpha Vantage API key loaded. Prices will be fetched live.");
            } else {
                System.out.println("No API key set — using demo prices.");
            }

            portfolioService.addStock("AAPL",  "Technology", 150.00, 10, LocalDate.of(2023, 1, 15));
            portfolioService.addStock("GOOGL", "Technology", 140.00,  5, LocalDate.of(2023, 3, 10));
            portfolioService.addStock("JPM",   "Finance",    130.00,  8, LocalDate.of(2023, 2, 20));
            portfolioService.addStock("TSLA",  "Automotive", 200.00,  6, LocalDate.of(2023, 4,  5));
            portfolioService.addStock("MSFT",  "Technology", 300.00,  4, LocalDate.of(2023, 1, 30));

            System.out.println("Fetching live prices on startup — this takes ~65s due to API rate limits...");
            portfolioService.refreshPrices();
            System.out.println("Ready. Open http://localhost:8080 in your browser.");
        };
    }
}
