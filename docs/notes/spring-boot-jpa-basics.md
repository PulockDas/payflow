# Spring, Spring Boot + JPA basics

## Spring vs Spring Boot
Both are frameworks; **Spring Boot is built on top of Spring**.

- **Spring (Spring Framework)**: the core. Dependency injection (beans), Spring MVC
  (web), transactions, data access. Powerful, but you wire everything yourself: web
  server, DB connection, Hibernate setup.
- **Spring Boot**: Spring + automatic setup.
  - **Starters**: one dependency (`starter-data-jpa`) brings a compatible set of libraries.
  - **Auto-configuration**: sees what's on the classpath and configures it (DataSource,
    Hibernate, Tomcat).
  - **Embedded server**: Tomcat runs inside the app; `java -jar` / `spring-boot:run` starts it.
  - **Sensible defaults**, overridden in `application.yml`.

In PayFlow: `@RestController`, `@Component`, beans = **Spring**. That it all works with
zero setup code = **Spring Boot**.

## Annotations

### Spring: "make this a bean"
A **bean** is an object Spring creates once and injects wherever it's needed
(constructor injection). Spring finds them by scanning `com.pulock.payflow` and below.

| Annotation | Meaning | In PayFlow |
|---|---|---|
| `@SpringBootApplication` | Entry point. = `@Configuration` + `@ComponentScan` (find beans in this package and below) + `@EnableAutoConfiguration` (set things up based on the classpath) | `PayflowApplication` |
| `@Component` | Generic bean | `ProductSeeder` |
| `@Configuration` | Bean that holds setup / config code | `StripeConfig` |
| `@Service` | Bean for business logic (same as `@Component`, just a label) | not used yet |
| `@Repository` | Bean for DB access. Not needed on Spring Data interfaces (`extends JpaRepository`) | not needed |
| `@Controller` | Web controller whose methods return **views** (HTML templates) | not used |
| `@RestController` | `@Controller` + `@ResponseBody`: return values are written straight to the response (JSON / text) | `HealthController`, `ProductController` |

### Spring: web + config
| Annotation | Meaning |
|---|---|
| `@RequestMapping("/api/products")` | URL prefix for every method in the class |
| `@GetMapping` / `@PostMapping` | Handle GET / POST on a path |
| `@Value("${stripe.secret-key}")` | Inject a config property |
| `@PostConstruct` | Run this method once, right after the bean is created and injected |

### JPA: map classes to tables
| Annotation | Meaning |
|---|---|
| `@Entity` | This class is stored in a table |
| `@Table(name = "orders")` | Table name (default = class name) |
| `@Id` | Primary key |
| `@GeneratedValue(strategy = IDENTITY)` | DB generates the id (Postgres identity column) |
| `@Column(nullable = false, length = 3)` | Column rules (`NOT NULL`, size) |
| `@ManyToOne` + `@JoinColumn(name = "product_id")` | Foreign key: many orders -> one product |
| `@Enumerated(EnumType.STRING)` | Store enum as text (`"PENDING"`) instead of a number |

## JPA vs Hibernate vs Spring Data JPA
- **JPA** (Jakarta Persistence) = a **specification**: just interfaces and annotations
  (`jakarta.persistence.*`). It says *what*, has no code that talks to a DB.
- **Hibernate** = a **library** that **implements** JPA (an ORM: Object-Relational
  Mapper). It turns entities into SQL and rows back into objects. Other implementations
  exist (EclipseLink), Hibernate is the default.
- **Spring Data JPA** = a layer on top: generates repository implementations
  (`findAll`, `save`, ...) from interfaces.

`spring-boot-starter-data-jpa` pulls in all three. You only write JPA annotations;
Hibernate does the work.

## How it all starts with the app
`main()` -> `SpringApplication.run(...)` -> **auto-configuration**: Spring Boot looks at
the classpath and config and sets things up itself:
1. Postgres driver + `spring.datasource.*` -> creates a **DataSource** (HikariCP
   connection pool) and connects to the DB.
2. Hibernate on the classpath -> creates the **EntityManagerFactory**, scans for
   `@Entity` classes.
3. Applies `ddl-auto` (creates/updates tables).
4. Creates implementations for every `JpaRepository` interface.
5. Starts Tomcat on 8080, then runs `CommandLineRunner`s (`ProductSeeder`).

No setup code needed: having the dependency in `pom.xml` is the trigger.

## `spring.jpa.hibernate.ddl-auto`
What Hibernate does to the **schema** (tables) at startup:

| Value | What happens | Use for |
|---|---|---|
| `none` | Nothing | Production (with migrations) |
| `validate` | Checks tables match entities, fails startup if not; changes nothing | Production safety check |
| `update` | Creates missing tables/columns. **Never** drops or renames anything | Dev / learning (PayFlow now) |
| `create` | Drops all tables and recreates them on every start (**data lost**) | Throwaway experiments |
| `create-drop` | Like `create`, and also drops everything on shutdown | Tests |

Default: `none` for a real DB like Postgres (`create-drop` for in-memory DBs like H2).

## Why production uses migrations (Flyway / Liquibase)
Problems with `update`:
- Rename a field -> it adds a **new** empty column; the old column + data stay behind.
- Never drops columns, never changes types safely, can't move/transform data.
- No history: nobody can see or review what changed, or when.
- Output depends on the current DB state, so dev and prod can drift apart.
- One bad entity change can alter the production schema silently at startup.

**Migrations** = SQL change scripts you write yourself, numbered, committed to git,
reviewed like code, applied in order, each exactly once.

- **Flyway**: plain SQL files.
  `src/main/resources/db/migration/V1__create_products.sql`, `V2__add_orders.sql`, ...
- **Liquibase**: changelog files (YAML / XML / SQL), with rollback support.
  `src/main/resources/db/changelog/db.changelog-master.yaml`

## Who tracks migrations?
- **Git** tracks the migration **files** (they're normal source code).
- **The tool** tracks which ones have **run**, in a table inside the database:
  `flyway_schema_history` (Flyway) or `DATABASECHANGELOG` (Liquibase). It stores each
  version + a checksum; at startup it runs only the new ones, and fails if an
  already-applied file was edited.
- **Spring Boot** doesn't track anything itself: it just auto-runs Flyway / Liquibase
  at startup when the library is on the classpath (same auto-configuration idea).

PayFlow has no migration files: it uses `ddl-auto: update` and no migration tool.
