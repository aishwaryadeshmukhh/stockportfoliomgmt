# Stock Portfolio Tracker & Risk Analyzer — Detailed Reference

A full-stack Spring Boot application that tracks a stock portfolio with live market data, computes industry-standard risk metrics, and generates AI-powered natural language insights via Groq (Llama 3). Built with Java 17, Spring Boot 3, JPA, H2, REST API, and a vanilla JS single-page dashboard.

Use this document as a prompt for an AI tool to teach you any section in depth.

---

## Table of Contents

1. [What This Project Does](#what-this-project-does)
2. [Architecture Overview](#architecture-overview)
3. [Project Structure](#project-structure)
4. [Spring Boot Concepts — Full Reference](#spring-boot-concepts--full-reference)
5. [Java Concepts Applied](#java-concepts-applied)
6. [Financial Concepts Explained](#financial-concepts-explained)
7. [How the Code Maps to Finance](#how-the-code-maps-to-finance)
8. [API Endpoints](#api-endpoints)
9. [Demo Portfolio](#demo-portfolio)

---

## What This Project Does

The app simulates what a financial analyst or portfolio manager does on a basic level:

- **Track** a portfolio of stocks (buy price, quantity, sector, date) persisted via JPA + H2
- **Fetch live prices** from Alpha Vantage API with rate-limit-aware sequential scheduling
- **Calculate P&L** — how much money you've made or lost per holding and in total
- **Analyze risk** using industry-standard metrics: Sharpe Ratio, Volatility, true Beta (SPY covariance), Moving Average, Diversification Score
- **Auto-refresh prices** on a configurable schedule via `@Scheduled`
- **Serve a live dashboard** — single-page JavaScript frontend consuming the REST API
- **Generate AI insights** — Groq (Llama 3.3 70B) interprets the portfolio's risk and performance in natural language
- **Export** a full portfolio + risk report to CSV

---

## Architecture Overview

```
Browser (index.html)
      ↕ fetch() / JSON
Spring Boot REST API (:8080)
  ├── PortfolioController     → HTTP layer, DTOs, request/response mapping
  ├── PortfolioService        → Business logic, @Scheduled price refresh
  ├── RiskAnalyzer            → Sharpe, Beta, Volatility, Moving Average
  ├── StockRepository         → JPA repository, H2 persistence
  ├── AlphaVantageClient      → Live prices + 30-day history
  ├── GroqClient              → Llama 3.3 70B natural language insights
  └── GlobalExceptionHandler  → Centralized error handling (@ControllerAdvice)

Data Flow:
  Startup → CommandLineRunner → addStock() → StockRepository.save() → H2
  @Scheduled → refreshPrices() → AlphaVantageClient → StockRepository.save()
  GET /api/portfolio → PortfolioService → StockRepository.findAll() → StockDTO → JSON
```

---

## Project Structure

```
src/main/java/com/portfolio/
│
├── PortfolioApplication.java       — @SpringBootApplication entry point, @EnableScheduling, CommandLineRunner
│
├── model/
│   ├── Stock.java                  — @Entity mapped to H2 "stocks" table, @Id on symbol
│   └── PriceHistory.java           — Value object for (date, closePrice) historical data points
│
├── repository/
│   └── StockRepository.java        — JpaRepository<Stock, String>: save, findAll, deleteById, existsById
│
├── dto/
│   ├── StockDTO.java               — API response shape for portfolio holdings (decoupled from @Entity)
│   └── StockRequest.java           — API request shape for adding a stock (decoupled from @Entity)
│
├── service/
│   └── PortfolioService.java       — @Service, @Scheduled, constructor-injected StockRepository
│
├── analytics/
│   └── RiskAnalyzer.java           — @Service, computes all risk metrics
│
├── controller/
│   └── PortfolioController.java    — @RestController, returns ResponseEntity<T> with DTOs
│
├── exception/
│   └── GlobalExceptionHandler.java — @ControllerAdvice, @ExceptionHandler for centralized error responses
│
├── api/
│   ├── AlphaVantageClient.java     — HttpURLConnection GET calls, Gson JSON parsing
│   ├── FallbackDataProvider.java   — Static demo price arrays when API unavailable
│   └── GroqClient.java             — POST to Groq API, Llama 3.3 70B, returns natural language string
│
└── report/
    └── ReportExporter.java         — OpenCSV export to timestamped .csv file
```

---

## Spring Boot Concepts — Full Reference

This section covers every Spring Boot concept used in this project. Each concept is explained from first principles — what it is, why it exists, how it's used here, and what to say in an interview.

---

### 1. `@SpringBootApplication`

**What it is:** A convenience annotation that combines three annotations:
- `@Configuration` — marks the class as a source of bean definitions
- `@EnableAutoConfiguration` — tells Spring Boot to automatically configure beans based on what's on the classpath (e.g., if H2 is present, configure an in-memory datasource)
- `@ComponentScan` — tells Spring to scan the current package and sub-packages for `@Component`, `@Service`, `@Repository`, `@Controller` etc.

**In this project:** `PortfolioApplication.java` is annotated with `@SpringBootApplication`. When you run the `main()` method, Spring Boot bootstraps the entire application context.

**Interview answer:** "@SpringBootApplication is a meta-annotation that enables auto-configuration, component scanning, and bean registration in one step. It eliminates the need for explicit XML configuration."

---

### 2. Dependency Injection and the Spring Container

**What it is:** Instead of classes creating their own dependencies (`new SomeService()`), Spring creates and manages all objects (called *beans*) and *injects* them wherever they're needed.

**Why it exists:** Loose coupling. If `PortfolioController` creates its own `PortfolioService`, you can never swap in a mock for testing. With injection, you just provide a different bean.

**How Spring knows what to inject:** Annotations like `@Service`, `@Repository`, `@Component`, `@RestController` register a class as a bean. Spring then matches constructor parameters by type and injects the right bean.

**In this project:**
```java
// Spring sees @RestController, scans constructor, injects all three beans automatically
public PortfolioController(PortfolioService portfolioService,
                           RiskAnalyzer riskAnalyzer,
                           GroqClient groqClient) { ... }
```

**Constructor injection vs field injection:** Always prefer constructor injection (what we use). It makes dependencies explicit and the class testable — you can instantiate it in a test by just passing mocks to the constructor.

**Interview answer:** "Spring's IoC container manages the lifecycle of beans and injects dependencies at runtime. Constructor injection makes dependencies explicit and supports immutability."

---

### 3. `@Service`, `@Repository`, `@Component`, `@RestController`

These are all specializations of `@Component` — they all register the class as a Spring bean. The distinction is semantic and behavioral:

| Annotation | Used on | Extra behavior |
|---|---|---|
| `@Component` | Generic utility class | None beyond bean registration |
| `@Service` | Business logic layer | None, but signals intent |
| `@Repository` | Data access layer | Translates DB exceptions to Spring's `DataAccessException` |
| `@RestController` | HTTP controller | Combines `@Controller` + `@ResponseBody` — all methods return JSON by default |

**In this project:**
- `PortfolioService` → `@Service`
- `StockRepository` → `@Repository` (implicit via `JpaRepository`)
- `GroqClient`, `ReportExporter` → `@Component`
- `PortfolioController` → `@RestController`
- `RiskAnalyzer` → `@Service`

---

### 4. Spring Data JPA and `JpaRepository`

**What JPA is:** Java Persistence API — a standard for mapping Java objects to database tables. You annotate a class with `@Entity` and Spring + Hibernate handle all the SQL.

**What `JpaRepository` gives you for free:** By extending `JpaRepository<Stock, String>`, `StockRepository` automatically gets:
- `save(entity)` — INSERT or UPDATE
- `findAll()` — SELECT *
- `findById(id)` — SELECT WHERE id = ?
- `existsById(id)` — SELECT COUNT WHERE id = ?
- `deleteById(id)` — DELETE WHERE id = ?
- `count()` — SELECT COUNT(*)

No SQL written. No implementation class needed.

**`@Entity` and `@Id`:**
```java
@Entity
@Table(name = "stocks")
public class Stock {
    @Id
    private String symbol;  // Primary key — symbol is unique per stock
    ...
}
```

`@Entity` tells JPA "map this class to a table". `@Id` marks the primary key. The no-arg constructor is required by JPA (Hibernate uses reflection to instantiate objects).

**H2 in-memory database:** We use H2 for simplicity — it's a Java database that runs inside the JVM, no external DB server needed. `spring.jpa.hibernate.ddl-auto=create-drop` means the schema is created on startup and dropped on shutdown. For production you'd use PostgreSQL with `ddl-auto=validate`.

**Interview answer:** "I used Spring Data JPA with an H2 in-memory database. The `Stock` entity is mapped to a table via `@Entity`, and `StockRepository` extends `JpaRepository` to get full CRUD operations without writing any SQL. For production I'd swap H2 for PostgreSQL — only the datasource config changes."

---

### 5. `@RestController` and `@RequestMapping`

**What it does:** `@RestController` marks a class as an HTTP controller where every method returns data (serialized to JSON) rather than a view name.

**`@RequestMapping("/api")`** sets a base path for all endpoints in the class.

**Method-level mappings:**
- `@GetMapping("/portfolio")` → handles `GET /api/portfolio`
- `@PostMapping("/portfolio/add")` → handles `POST /api/portfolio/add`
- `@DeleteMapping("/portfolio/{symbol}")` → handles `DELETE /api/portfolio/AAPL`
- `@PathVariable` extracts `{symbol}` from the URL
- `@RequestBody` deserializes the JSON request body into a Java object (e.g., `StockRequest`)

**In this project:** The controller was refactored to return `ResponseEntity<T>` instead of raw objects, giving explicit control over HTTP status codes.

---

### 6. DTO Pattern (Data Transfer Object)

**What it is:** A DTO is a plain object whose only job is to carry data between layers. It is separate from the `@Entity` (database model).

**Why it matters:**
- Your `@Entity` has JPA annotations, lazy loading, and database concerns. You don't want to expose that directly over HTTP.
- DTOs let you shape the API response independently of the database schema — you can add computed fields, rename properties, or hide internal fields.
- Decouples the API contract from the database model — you can change the DB schema without breaking API consumers.

**In this project:**
- `StockDTO` — the response shape for `GET /api/portfolio`. Contains computed fields like `profitLoss` and `profitLossPct`.
- `StockRequest` — the request shape for `POST /api/portfolio/add`. A clean input object instead of raw `Map<String, String>`.

**Before (bad):**
```java
// Controller was building raw Maps — no type safety, hard to read
Map<String, Object> row = new LinkedHashMap<>();
row.put("symbol", s.getSymbol());
```

**After (good):**
```java
// Controller builds a typed DTO — clean, documented, testable
return ResponseEntity.ok(new StockDTO(s.getSymbol(), s.getSector(), ...));
```

**Interview answer:** "DTOs decouple the API contract from the persistence model. The `StockDTO` lets me expose computed fields like P&L percentage without putting business logic in the entity, and `StockRequest` gives me a typed, validated input object instead of parsing raw maps."

---

### 7. `@ControllerAdvice` and `@ExceptionHandler`

**What it is:** A centralized place to handle exceptions thrown from any controller. Without this, every controller method would need its own try-catch blocks.

**How it works:** `@ControllerAdvice` makes a class globally apply to all controllers. `@ExceptionHandler(SomeException.class)` catches that exception type and returns a structured response.

**In this project:**
```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
               .body(Map.of("status", "error", "message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        return ResponseEntity.status(500)
               .body(Map.of("status", "error", "message", "Unexpected error: " + ex.getMessage()));
    }
}
```

Now in the controller, instead of returning error maps manually:
```java
// Before: manual error handling in every method
try { ... } catch (Exception e) { return Map.of("status", "error", ...); }

// After: just throw — GlobalExceptionHandler catches it
if (request.getSymbol() == null) throw new IllegalArgumentException("Symbol is required.");
```

**Interview answer:** "`@ControllerAdvice` gives you a single place to handle all exceptions across controllers. It enforces a consistent error response format and removes try-catch boilerplate from business logic."

---

### 8. `@Scheduled` and `@EnableScheduling`

**What it is:** Spring's built-in task scheduling. `@Scheduled` on a method makes Spring call it automatically on a defined interval or cron expression. `@EnableScheduling` on the main class activates the scheduler.

**Options:**
- `fixedRate = 60000` — run every 60 seconds, regardless of how long the task takes
- `fixedDelay = 60000` — wait 60 seconds *after* the task finishes before running again
- `cron = "0 0 9 * * MON-FRI"` — run at 9am every weekday (cron expression)
- `fixedRateString = "${price.refresh.interval:86400000}"` — read interval from config, default 24h

**In this project:**
```java
@Scheduled(fixedRateString = "${price.refresh.interval:86400000}")
public void refreshPrices() {
    // Auto-refreshes prices every 24 hours by default
    // Configurable via price.refresh.interval property
}
```

The interval is kept at 24 hours because the free Alpha Vantage API allows only 25 requests/day. In a production environment with a paid API tier, this would be set to every 15 minutes.

**Why `fixedRateString` instead of `fixedRate`:** Using a string that reads from a property (`${...}`) makes the interval configurable per environment — dev might refresh hourly, prod might refresh every 15 minutes — without changing code.

**Interview answer:** "`@Scheduled` with `fixedRateString` lets me drive the refresh interval from config, not hardcoded values. In production you'd bind it to a paid API tier's rate limit. `@EnableScheduling` activates Spring's task executor which runs scheduled methods in a background thread."

---

### 9. `@Value` and Externalized Configuration

**What it is:** `@Value("${property.name:defaultValue}")` injects a value from `application.properties` or environment variables into a Spring bean field.

**The `:` syntax:** `${groq.apikey:}` — the part after `:` is the default if the property isn't set. An empty default `""` means the field gets an empty string rather than Spring throwing an error at startup.

**Property resolution order (highest to lowest priority):**
1. Environment variables (e.g., Railway Variables tab: `GROQ_APIKEY=...`)
2. `application.properties` (e.g., `groq.apikey=${GROQ_APIKEY:}`)
3. Default value in `@Value`

**In this project:**
```java
// In GroqClient.java
@Value("${groq.apikey:}")
private String apiKey;

// In application.properties
groq.apikey=${GROQ_APIKEY:}
alphavantage.apikey=${ALPHAVANTAGE_APIKEY:}
```

This means: Railway injects `GROQ_APIKEY` as an env var → `application.properties` maps it to `groq.apikey` → `@Value` injects it into `GroqClient`. Secrets never appear in code or git history.

---

### 10. `CommandLineRunner`

**What it is:** A functional interface with a single `run()` method. A bean that implements it (or a `@Bean` that returns it) is called by Spring Boot *after the application context is fully loaded* — i.e., after all beans are wired, all JPA tables are created, and the HTTP server is started.

**In this project:**
```java
@Bean
CommandLineRunner loadDemoPortfolio(PortfolioService portfolioService,
                                    @Value("${alphavantage.apikey:}") String apiKey) {
    return args -> {
        // Runs once on startup — seeds the portfolio and fetches live prices
        portfolioService.addStock("AAPL", "Technology", 150.00, 10, LocalDate.of(2023, 1, 15));
        portfolioService.refreshPrices();
    };
}
```

**Why not put this in `main()`?** The Spring context isn't ready in `main()`. Beans don't exist yet. `CommandLineRunner` guarantees the context is fully initialized.

---

### 11. `ResponseEntity<T>`

**What it is:** A wrapper for HTTP responses that gives you explicit control over the status code, headers, and body.

**Before:**
```java
// Spring picks the status code (always 200)
public List<StockDTO> getPortfolio() { return list; }
```

**After:**
```java
// You control the status code explicitly
public ResponseEntity<List<StockDTO>> getPortfolio() {
    return ResponseEntity.ok(list);            // 200
    return ResponseEntity.badRequest().body(e); // 400
    return ResponseEntity.status(500).body(e);  // 500
}
```

**In this project:** All controller methods return `ResponseEntity<T>`. Errors are thrown and caught by `GlobalExceptionHandler` which returns `ResponseEntity` with the appropriate 4xx/5xx code.

---

### 12. Spring Data JPA vs Plain JDBC vs Hibernate

Understanding the stack matters for interviews:

- **JDBC** — raw SQL with `Connection`, `PreparedStatement`, `ResultSet`. Full control, lots of boilerplate.
- **Hibernate** — ORM framework. Maps Java objects to tables. Generates SQL. Less boilerplate.
- **JPA** — a *standard* (interface) that Hibernate implements. You code to JPA, Hibernate is the provider.
- **Spring Data JPA** — sits on top of JPA. Generates repository implementations automatically. `JpaRepository` methods like `findAll()` are implemented by Spring at runtime — you never write the class body.

In this project: Spring Data JPA → JPA (standard) → Hibernate (implementation) → H2 (database).

---

### 13. MockMvc Integration Testing

**What it is:** MockMvc lets you test Spring MVC controllers without starting a real HTTP server. It simulates HTTP requests through the Spring DispatcherServlet in memory.

**`@SpringBootTest`** — loads the full application context (all beans, JPA, scheduler, etc.).
**`@AutoConfigureMockMvc`** — configures MockMvc and injects it as a bean.

**In this project:**
```java
@SpringBootTest
@AutoConfigureMockMvc
public class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getPortfolio_returnsOkWithList() throws Exception {
        mockMvc.perform(get("/api/portfolio"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray());
    }

    @Test
    void addStock_withMissingFields_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/portfolio/add")
                       .contentType("application/json")
                       .content("{\"symbol\":\"NVDA\"}"))
               .andExpect(status().isBadRequest());
    }
}
```

**What each test verifies:**
- `getPortfolio` — HTTP 200 and response is a JSON array
- `getSummary` — HTTP 200 and key fields (`totalInvested`, `holdingsCount`) exist
- `addStock` with missing fields — HTTP 400 (caught by `GlobalExceptionHandler`)
- `removeStock` with unknown symbol — HTTP 400

**Interview answer:** "MockMvc tests the full Spring MVC stack in memory — routing, deserialization, exception handling — without a real HTTP server. `@SpringBootTest` loads the real application context so I'm testing actual behavior, not mocks of the service layer."

---

## Java Concepts Applied

### Collections and Data Structures

- **`LinkedHashMap<String, Stock>`** in `PortfolioService` — preserves insertion order so the portfolio displays in the order stocks were added.
- **`PriorityQueue<Stock>`** in `getTopPerformers()` — a max-heap that efficiently extracts the top N performers by P&L percentage without sorting the entire list.
- **`HashSet<String>`** in `calculateDiversificationScore()` — counts unique sectors. Adding duplicates is a no-op, so `Set.size()` equals distinct sector count.
- **`HashMap<String, List<PriceHistory>>`** — in-memory history cache. O(1) lookup prevents redundant API calls when multiple risk metrics need the same stock's history.

### Streams and Lambdas

```java
// Sum all invested values
stockRepository.findAll().stream().mapToDouble(Stock::getInvestedValue).sum();

// Compute mean of daily returns
Arrays.stream(returns).average().orElse(0);

// Compute variance
Arrays.stream(returns).map(r -> Math.pow(r - mean, 2)).average().orElse(0);
```

### Exception Handling

- All API calls wrapped in `try-catch (IOException e)` with graceful fallback to demo data.
- `Thread.sleep()` `InterruptedException` caught and thread re-interrupted (correct Java idiom).
- Business validation throws `IllegalArgumentException` — caught by `GlobalExceptionHandler`.

### HTTP Networking

`AlphaVantageClient` and `GroqClient` use `HttpURLConnection` — Java's built-in HTTP client. No third-party library. For POST requests (Groq), `setDoOutput(true)` and `getOutputStream()` write the request body.

### JSON Parsing (Gson)

Alpha Vantage and Groq both return JSON. `JsonParser.parseString()` → `JsonObject` → `.get("key")` navigates the response tree to extract values.

### Rate Limiting

The free Alpha Vantage API allows 5 requests/minute, 25/day. `Thread.sleep(13_000)` between each stock fetch stays within the limit. History results are cached in `HashMap` so risk calculations for 5 stocks make 5 API calls total, not 35.

---

## Financial Concepts Explained

### Profit & Loss (P&L)
```
P&L ($) = (Current Price - Buy Price) × Quantity
P&L (%) = ((Current Price - Buy Price) / Buy Price) × 100
```

### Daily Return
```
Daily Return = (Today's Price - Yesterday's Price) / Yesterday's Price
```
Building block for all risk metrics.

### Volatility (Annualized Standard Deviation)
```
Annual Volatility = StdDev(daily returns) × sqrt(252)
```
252 = trading days per year. Measures how much the stock price fluctuates annually.

### Sharpe Ratio
```
Sharpe = (Annual Return - Risk-Free Rate) / Annual Volatility
```
Risk-free rate = 5% (US Treasury proxy). Measures return per unit of risk. Above 1.0 is good, above 2.0 is excellent.

### Beta (True Covariance Formula)
```
Beta = Covariance(Stock Returns, SPY Returns) / Variance(SPY Returns)
```
Uses SPY (S&P 500 ETF) as the market proxy. Covariance captures whether the stock moves *with* the market, not just how volatile it is. Beta > 1 = more volatile than market; Beta < 1 = defensive.

### Moving Average
```
30-day MA = Sum of last 30 closing prices / 30
```
Trend indicator. Price above MA = uptrend. Price below MA = possible decline.

### Diversification Score
```
Score = Unique Sectors / Total Holdings
```
Score of 1.0 = every stock in a different sector. Score < 0.5 = concentrated risk.

### Portfolio Weighted Sharpe Ratio
```
Weighted Sharpe = Σ (Stock Value / Total Portfolio Value) × Stock Sharpe Ratio
```
Weights each stock's contribution by its share of total portfolio value.

---

## How the Code Maps to Finance

| Financial Concept | Where in Code |
|---|---|
| P&L calculation | `Stock.getProfitLoss()`, `getProfitLossPercent()` |
| Daily returns | `RiskAnalyzer` internal `dailyReturns()` |
| Volatility | `RiskAnalyzer.calculateVolatility()` |
| Sharpe Ratio | `RiskAnalyzer.calculateSharpeRatio()` |
| True Beta | `RiskAnalyzer.calculateBeta()` — SPY covariance formula |
| Moving Average | `RiskAnalyzer.calculateMovingAverage()` |
| Diversification Score | `RiskAnalyzer.calculateDiversificationScore()` |
| Portfolio Sharpe | `RiskAnalyzer.portfolioSharpeRatio()` |
| Live prices | `AlphaVantageClient.fetchCurrentPrice()` |
| Historical data | `AlphaVantageClient.fetchDailyHistory()` |
| Top performers | `PortfolioService.getTopPerformers()` — PriorityQueue max-heap |
| AI interpretation | `GroqClient.getInsights()` — Llama 3.3 70B |
| Persistence | `StockRepository.save()`, `findAll()` — JPA + H2 |

---

## API Endpoints

| Method | Endpoint | Description | Returns |
|---|---|---|---|
| GET | `/api/portfolio` | All holdings with P&L | `List<StockDTO>` |
| GET | `/api/summary` | Portfolio totals | `Map<String, Object>` |
| GET | `/api/risk` | Risk metrics per stock | `Map<String, Object>` |
| GET | `/api/insights` | AI-generated analysis | `Map<String, String>` |
| POST | `/api/refresh` | Refresh live prices | `Map<String, String>` |
| POST | `/api/portfolio/add` | Add a stock | `Map<String, String>` |
| DELETE | `/api/portfolio/{symbol}` | Remove a stock | `Map<String, String>` |

---

## Demo Portfolio

| Symbol | Sector | Buy Price | Qty |
|---|---|---|---|
| AAPL | Technology | $150.00 | 10 |
| GOOGL | Technology | $140.00 | 5 |
| JPM | Finance | $130.00 | 8 |
| TSLA | Automotive | $200.00 | 6 |
| MSFT | Technology | $300.00 | 4 |

Diversification Score = 0.6 (3 unique sectors / 5 holdings) — moderately diversified but overweight in Technology. A realistic and discussable scenario for interviews.
