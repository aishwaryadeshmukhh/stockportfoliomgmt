# Stock Portfolio Tracker & Risk Analyzer

A Java console application that lets you manage a stock portfolio and analyze it using real financial risk metrics — Sharpe Ratio, Volatility, Beta, Moving Average, and Diversification Score. Prices are fetched live from the Alpha Vantage API, with offline demo data as a fallback.

---

## Table of Contents

1. [What This Project Does](#what-this-project-does)
2. [How to Run](#how-to-run)
3. [Project Structure](#project-structure)
4. [Java Concepts Applied](#java-concepts-applied)
5. [Financial Concepts Explained](#financial-concepts-explained)
6. [How the Code Maps to Finance](#how-the-code-maps-to-finance)
7. [Demo Portfolio](#demo-portfolio)

---

## What This Project Does

The app simulates what a financial analyst or portfolio manager does on a basic level:

- **Track** a portfolio of stocks (buy price, quantity, sector, date)
- **Fetch live prices** from Alpha Vantage API to update current values
- **Calculate P&L** (Profit and Loss) — how much money you've made or lost
- **Analyze risk** using industry-standard metrics (Sharpe Ratio, Volatility, Beta)
- **Rank stocks** by performance using a priority queue
- **Export** a full portfolio + risk report to CSV

---

## How to Run

**Prerequisites:** Java 17+, Maven installed and on PATH.

```bash
# Build
mvn package -DskipTests

# Run
java -jar target/portfolio-tracker.jar
```

On startup, you will be asked for an Alpha Vantage API key (free at alphavantage.co). Press Enter to skip and use offline demo data instead.

**Note:** The free API tier allows 5 requests/minute. The app automatically waits 13 seconds between each stock fetch to stay within this limit.

---

## Project Structure

```
src/main/java/com/portfolio/
│
├── model/
│   ├── Stock.java              — Represents one stock holding (symbol, buy price, quantity, sector)
│   └── PriceHistory.java       — A single (date, close price) data point for historical analysis
│
├── api/
│   ├── AlphaVantageClient.java — Makes HTTP calls to Alpha Vantage REST API, parses JSON responses
│   └── FallbackDataProvider.java — Hardcoded 30-day price arrays used when API is unavailable
│
├── service/
│   └── PortfolioService.java   — Core business logic: add/remove stocks, refresh prices, P&L totals
│
├── analytics/
│   └── RiskAnalyzer.java       — Computes Volatility, Sharpe Ratio, Beta, Moving Average, Diversification
│
├── report/
│   └── ReportExporter.java     — Exports portfolio + risk metrics to a timestamped CSV file
│
└── ui/
    └── MainMenu.java           — Console menu, user input handling, entry point (main method)
```

---

## Java Concepts Applied

### 1. Object-Oriented Programming (OOP)

- **Encapsulation:** `Stock.java` keeps all fields `private` and exposes them through getters/setters. The current price can only be updated via `setCurrentPrice()`, not directly.
- **Abstraction:** `PortfolioService` hides whether data comes from the live API or fallback. The rest of the app doesn't know or care — it just calls `refreshPrices()`.
- **Separation of Concerns:** Each class has one job. `RiskAnalyzer` only does math. `ReportExporter` only writes files. `MainMenu` only handles user input.

### 2. Collections and Data Structures

- **`HashMap<String, Stock>`** in `PortfolioService` — O(1) lookup of a stock by its symbol (e.g., "AAPL"). Used because we frequently need to find, add, or remove a specific stock by name.
- **`LinkedHashMap`** — preserves insertion order so the portfolio displays in the order stocks were added.
- **`PriorityQueue<Stock>`** in `getTopPerformers()` — a max-heap that automatically sorts stocks by P&L percentage. Efficiently extracts the top N performers without sorting the entire list.
- **`HashSet<String>`** in `calculateDiversificationScore()` — used to count unique sectors. Adding duplicates to a Set is a no-op, so the size of the Set equals the number of distinct sectors.
- **`List<PriceHistory>`** — ordered list of historical price points, used as input for all statistical calculations.

### 3. Java Streams and Lambdas

Used in `PortfolioService` and `RiskAnalyzer` for clean, functional-style data processing:

```java
// Sum all invested values across portfolio
portfolio.values().stream().mapToDouble(Stock::getInvestedValue).sum();

// Compute mean of daily returns array
Arrays.stream(returns).average().orElse(0);

// Compute variance
Arrays.stream(returns).map(r -> Math.pow(r - mean, 2)).average().orElse(0);
```

Method references (`Stock::getInvestedValue`) and lambda expressions (`r -> Math.pow(r - mean, 2)`) replace verbose for-loops.

### 4. Exception Handling

- All API calls are wrapped in `try-catch (IOException e)` — network calls can always fail, and the app gracefully falls back to demo data rather than crashing.
- `Thread.sleep()` throws `InterruptedException`, which is caught and handled by re-interrupting the thread (the correct Java idiom).
- Input parsing (`Double.parseDouble`, `LocalDate.parse`) is wrapped to handle invalid user input without crashing.

### 5. HTTP Networking

`AlphaVantageClient` uses Java's built-in `HttpURLConnection` to make GET requests to a REST API and read the response as a string. No third-party HTTP library needed.

### 6. JSON Parsing

The API returns JSON. `AlphaVantageClient` uses the Gson library (`JsonParser`, `JsonObject`) to navigate the JSON tree and extract specific values like `"05. price"` or `"4. close"`.

### 7. Date and Time API

Uses `java.time.LocalDate` (introduced in Java 8) for storing buy dates and parsing historical price dates. This is preferred over the old `java.util.Date` because it is immutable and easier to work with.

### 8. File I/O and CSV Export

`ReportExporter` uses the OpenCSV library to write structured data to a `.csv` file. The filename includes a timestamp (`yyyyMMdd_HHmmss`) generated via `LocalDateTime` and `DateTimeFormatter`.

### 9. Rate Limiting with Thread.sleep

The free Alpha Vantage API allows 5 requests per minute. `PortfolioService.refreshPrices()` calls `Thread.sleep(13_000)` between each stock fetch — a deliberate pause of 13 seconds — to avoid hitting the rate limit. This is a real-world API throttling pattern.

### 10. Dependency Injection (manual)

`RiskAnalyzer` receives a `PortfolioService` instance through its constructor rather than creating one itself. This means `RiskAnalyzer` can work with any `PortfolioService` — a basic form of dependency injection that makes components loosely coupled and easier to test.

---

## Financial Concepts Explained

### Profit & Loss (P&L)

The most basic metric. For each stock:

```
P&L ($)  = (Current Price - Buy Price) × Quantity
P&L (%)  = ((Current Price - Buy Price) / Buy Price) × 100
```

If you bought 10 shares of AAPL at $150 and it's now $209, your P&L is +$590 (+39.3%).

### Daily Return

The percentage change in a stock's price from one day to the next:

```
Daily Return = (Today's Price - Yesterday's Price) / Yesterday's Price
```

This is the building block for all risk metrics. A stock that moves +2%, -3%, +1%, -2% has a series of daily returns that we can analyze statistically.

### Volatility (Annualized Standard Deviation)

Volatility measures how much a stock's price fluctuates. It is the standard deviation of daily returns, scaled up to a yearly figure:

```
Daily Std Dev = sqrt(average of (each daily return - mean return)²)
Annual Volatility = Daily Std Dev × sqrt(252)    [252 = trading days in a year]
```

- A volatility of 20% means the stock typically moves ±20% per year.
- Lower volatility = more stable, less risky.
- A stock like a utility company might have 15% volatility; a tech stock might have 40%.

### Sharpe Ratio

The Sharpe Ratio answers: "How much return am I getting per unit of risk?" It adjusts returns for the risk taken.

```
Sharpe Ratio = (Annual Return - Risk-Free Rate) / Annual Volatility
```

The **risk-free rate** (5% in this app) is what you'd earn with zero risk — e.g., a US Treasury bond. If your stock returns 12% with 20% volatility, your Sharpe is (0.12 - 0.05) / 0.20 = 0.35. That's not great — you're not being well compensated for the risk.

**Interpreting Sharpe Ratio:**
- Below 1.0 — poor risk-adjusted return
- 1.0 to 2.0 — good
- Above 2.0 — excellent
- Negative — you'd have been better off in a risk-free asset

### Beta

Beta measures how much a stock moves relative to the overall market, and crucially, whether it moves *together* with the market.

```
Beta = Covariance(Stock Returns, Market Returns) / Variance(Market Returns)
```

**Covariance** captures whether the stock and market move in the same direction at the same time. A stock can be highly volatile but still have a low Beta if its price movements are unrelated to the market.

This app uses **SPY** (the S&P 500 ETF) as the market proxy. SPY's daily historical returns are fetched from the same API, and the true covariance formula is applied — not a simplified variance ratio.

- **Beta = 1.0** — stock moves in line with the market
- **Beta > 1.0** — stock is more volatile than the market (e.g., Tesla)
- **Beta < 1.0** — stock is less volatile (e.g., a consumer staples company)
- **Beta < 0** — stock moves opposite to the market (rare, e.g., gold)

### Moving Average

The average closing price over the last N days:

```
30-day Moving Average = Sum of last 30 closing prices / 30
```

Used as a trend indicator. If the current price is above the moving average, the stock is trending upward. If below, it may be declining. Traders use the 50-day and 200-day moving averages as key signals.

### Diversification Score

A simple measure of how spread out the portfolio is across industries:

```
Diversification Score = Unique Sectors / Total Holdings
```

- Score of 1.0 means every stock is in a different sector — maximum diversification.
- Score of 0.33 with 3 stocks all in Technology means the portfolio is concentrated and exposed to sector-specific risk.

The financial principle: if all your stocks are in one sector (e.g., all tech), a tech crash hits your whole portfolio. Spreading across Finance, Healthcare, Energy etc. reduces this risk.

### Portfolio Weighted Sharpe Ratio

Instead of a simple average of individual Sharpe Ratios, each stock's Sharpe is weighted by its proportion of the total portfolio value:

```
Weighted Sharpe = Σ (Stock Value / Total Portfolio Value) × Stock Sharpe Ratio
```

This reflects that a stock worth 60% of your portfolio has more impact on overall risk-adjusted performance than a stock worth 5%.

---

## How the Code Maps to Finance

| Financial Concept         | Where in Code                                      |
|---------------------------|----------------------------------------------------|
| P&L calculation           | `Stock.getProfitLoss()`, `getProfitLossPercent()`  |
| Daily returns             | `RiskAnalyzer.dailyReturns()`                      |
| Volatility                | `RiskAnalyzer.calculateVolatility()`               |
| Sharpe Ratio              | `RiskAnalyzer.calculateSharpeRatio()`              |
| Beta                      | `RiskAnalyzer.calculateBeta()`                     |
| Moving Average            | `RiskAnalyzer.calculateMovingAverage()`            |
| Diversification Score     | `RiskAnalyzer.calculateDiversificationScore()`     |
| Portfolio Sharpe Ratio    | `RiskAnalyzer.portfolioSharpeRatio()`              |
| Live market data          | `AlphaVantageClient.fetchCurrentPrice()`           |
| Historical price data     | `AlphaVantageClient.fetchDailyHistory()`           |
| Top performers ranking    | `PortfolioService.getTopPerformers()` (PriorityQueue) |

---

## Demo Portfolio

When you start the app, five stocks are pre-loaded to demonstrate all features:

| Symbol | Sector      | Buy Price | Qty |
|--------|-------------|-----------|-----|
| AAPL   | Technology  | $150.00   | 10  |
| GOOGL  | Technology  | $140.00   | 5   |
| JPM    | Finance     | $130.00   | 8   |
| TSLA   | Automotive  | $200.00   | 6   |
| MSFT   | Technology  | $300.00   | 4   |

The portfolio has a Diversification Score of 0.6 (3 unique sectors out of 5 holdings), which is a realistic and discussable scenario — it is moderately diversified but overweight in Technology.
