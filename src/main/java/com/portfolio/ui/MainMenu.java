package com.portfolio.ui;

import com.portfolio.analytics.RiskAnalyzer;
import com.portfolio.model.Stock;
import com.portfolio.report.ReportExporter;
import com.portfolio.service.PortfolioService;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

public class MainMenu {

    private static final PortfolioService portfolioService = new PortfolioService();
    private static final RiskAnalyzer riskAnalyzer = new RiskAnalyzer(portfolioService);
    private static final ReportExporter reportExporter = new ReportExporter(portfolioService, riskAnalyzer);
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        printBanner();
        setupApiKey();
        loadDemoPortfolio();

        boolean running = true;
        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1": viewPortfolio(); break;
                case "2": addStock(); break;
                case "3": removeStock(); break;
                case "4": refreshPrices(); break;
                case "5": viewRiskAnalysis(); break;
                case "6": viewTopPerformers(); break;
                case "7": exportReport(); break;
                case "0": running = false; break;
                default: System.out.println("Invalid option. Please try again.");
            }
        }
        System.out.println("\nGoodbye!");
        scanner.close();
    }

    private static void printBanner() {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║    Stock Portfolio Tracker & Risk Analyzer   ║");
        System.out.println("╚══════════════════════════════════════════════╝");
    }

    private static void setupApiKey() {
        System.out.print("\nEnter Alpha Vantage API key (or press Enter to use demo data): ");
        String key = scanner.nextLine().trim();
        if (!key.isEmpty()) {
            portfolioService.enableApi(key);
            System.out.println("Live mode enabled. Prices will be fetched with a 13s delay between requests.");
        } else {
            System.out.println("Using offline demo data.");
        }
    }

    private static void loadDemoPortfolio() {
        System.out.println("\nLoading demo portfolio...");
        portfolioService.addStock("AAPL",  "Technology",  150.00, 10, LocalDate.of(2023, 1, 15));
        portfolioService.addStock("GOOGL", "Technology",  140.00,  5, LocalDate.of(2023, 3, 10));
        portfolioService.addStock("JPM",   "Finance",     130.00,  8, LocalDate.of(2023, 2, 20));
        portfolioService.addStock("TSLA",  "Automotive",  200.00,  6, LocalDate.of(2023, 4, 5));
        portfolioService.addStock("MSFT",  "Technology",  300.00,  4, LocalDate.of(2023, 1, 30));
        portfolioService.refreshPrices();
    }

    private static void printMenu() {
        System.out.println("\n--- MENU ---");
        System.out.println("1. View Portfolio");
        System.out.println("2. Add Stock");
        System.out.println("3. Remove Stock");
        System.out.println("4. Refresh Prices");
        System.out.println("5. Risk Analysis");
        System.out.println("6. Top Performers");
        System.out.println("7. Export Report (CSV)");
        System.out.println("0. Exit");
        System.out.print("Choice: ");
    }

    private static void viewPortfolio() {
        System.out.println("\n======= PORTFOLIO =======");
        if (portfolioService.isEmpty()) {
            System.out.println("Portfolio is empty.");
            return;
        }
        System.out.printf("%-6s | %-15s | %-12s | %-14s | %-6s | %-12s | %-8s%n",
                "Symbol", "Sector", "Buy Price", "Current Price", "Qty", "P&L ($)", "P&L (%)");
        System.out.println("-".repeat(85));
        for (Stock s : portfolioService.getPortfolio()) {
            System.out.println(s);
        }
        System.out.println("-".repeat(85));
        System.out.printf("Total Invested  : $%.2f%n", portfolioService.getTotalInvested());
        System.out.printf("Current Value   : $%.2f%n", portfolioService.getTotalCurrentValue());
        System.out.printf("Total P&L       : $%.2f (%+.2f%%)%n",
                portfolioService.getTotalProfitLoss(),
                portfolioService.getTotalProfitLossPercent());
    }

    private static void addStock() {
        System.out.println("\n--- Add Stock ---");
        try {
            System.out.print("Symbol (e.g. AAPL): ");
            String symbol = scanner.nextLine().trim().toUpperCase();

            System.out.print("Sector (e.g. Technology, Finance, Healthcare): ");
            String sector = scanner.nextLine().trim();

            System.out.print("Buy price: $");
            double buyPrice = Double.parseDouble(scanner.nextLine().trim());

            System.out.print("Quantity: ");
            int qty = Integer.parseInt(scanner.nextLine().trim());

            System.out.print("Buy date (YYYY-MM-DD): ");
            LocalDate date = LocalDate.parse(scanner.nextLine().trim());

            portfolioService.addStock(symbol, sector, buyPrice, qty, date);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number entered.");
        } catch (DateTimeParseException e) {
            System.out.println("Invalid date format. Use YYYY-MM-DD.");
        }
    }

    private static void removeStock() {
        System.out.print("\nEnter symbol to remove: ");
        String symbol = scanner.nextLine().trim().toUpperCase();
        if (portfolioService.removeStock(symbol)) {
            System.out.println(symbol + " removed from portfolio.");
        } else {
            System.out.println(symbol + " not found in portfolio.");
        }
    }

    private static void refreshPrices() {
        portfolioService.refreshPrices();
        viewPortfolio();
    }

    private static void viewRiskAnalysis() {
        if (portfolioService.isEmpty()) {
            System.out.println("Portfolio is empty.");
            return;
        }
        riskAnalyzer.printPortfolioRiskSummary();
        System.out.println("\nView individual stock risk? Enter symbol (or press Enter to skip): ");
        String symbol = scanner.nextLine().trim().toUpperCase();
        if (!symbol.isEmpty() && portfolioService.getStock(symbol) != null) {
            riskAnalyzer.printStockRiskReport(symbol);
        } else if (!symbol.isEmpty()) {
            System.out.println("Stock not found in portfolio.");
        }
    }

    private static void viewTopPerformers() {
        System.out.print("How many top performers to show? ");
        try {
            int n = Integer.parseInt(scanner.nextLine().trim());
            List<Stock> top = portfolioService.getTopPerformers(n);
            System.out.println("\n--- Top " + n + " Performers ---");
            for (int i = 0; i < top.size(); i++) {
                System.out.printf("%d. %s%n", i + 1, top.get(i));
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid number.");
        }
    }

    private static void exportReport() {
        try {
            String filename = reportExporter.exportToCsv();
            System.out.println("Report exported to: " + filename);
        } catch (IOException e) {
            System.out.println("Export failed: " + e.getMessage());
        }
    }
}
