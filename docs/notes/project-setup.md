# Project setup reference

## Dependencies (pom.xml)

No dependency has a `<version>`: the parent `spring-boot-starter-parent` (4.2.0)
picks tested, compatible versions for all of them.

A **starter** = a bundle of libraries + auto-configuration (Spring sees them on the
classpath and sets up sensible defaults).

| Dependency | What it gives you | Where PayFlow uses it |
|---|---|---|
| `spring-boot-starter-webmvc` | Embedded Tomcat server, `@RestController`, `@GetMapping`, Jackson (Java objects <-> JSON). Called `starter-web` in Spring Boot 3. | `/health`, `GET /api/products`, `POST /api/checkout`, webhook endpoint |
| `spring-boot-starter-data-jpa` | JPA + Hibernate: map classes to tables (`@Entity`), ready-made repositories (`findById`, `save`) without writing SQL. | `Product`, `Order` + repositories (M2) |
| `spring-boot-starter-validation` | Bean Validation: `@NotNull`, `@Min(1)` etc. on request objects, checked with `@Valid`. | Rejecting bad checkout requests (M3) |
| `postgresql` (scope `runtime`) | JDBC driver that talks to Postgres. `runtime` = my code never imports it; Hibernate loads it at runtime. | Connecting to the Docker Postgres (M1) |
| `*-test` starters (scope `test`) | JUnit 5, AssertJ, Mockito + test helpers per area (`@WebMvcTest`, `@DataJpaTest`). Boot 4 splits them per starter (Boot 3 had one `starter-test`). `test` scope = not in the final jar. | Tests (M7) |

Not from Initializr: the Stripe SDK (`com.stripe:stripe-java`) gets added by hand in M3.

## .gitattributes

```
/mvnw text eol=lf
*.cmd text eol=crlf
```

Windows ends lines with CRLF (`\r\n`), Linux/Mac with LF (`\n`). Git can convert
them on checkout, which can break scripts.

- `/mvnw text eol=lf`: `mvnw` is a shell script for Linux/Mac (and the Docker
  build in M7). With CRLF endings it fails with errors like
  `/bin/sh^M: bad interpreter`. This forces LF, even when committed from Windows.
- `*.cmd text eol=crlf`: `mvnw.cmd` is a Windows batch file; it's safest with CRLF.

`text` marks the files as text so Git is allowed to normalise them.

## Maven wrapper commands

Run from the project root (PowerShell uses `.\mvnw.cmd`; Linux/Mac/Git Bash use `./mvnw`).

| Command | What it does |
|---|---|
| `.\mvnw.cmd clean compile` | Deletes `target/`, downloads missing dependencies, compiles the code |
| `.\mvnw.cmd dependency:resolve` | Only downloads dependencies, no compiling |
| `.\mvnw.cmd dependency:tree` | Prints all dependencies as a tree: direct ones at the top, the libraries they pull in (transitive) indented below, with versions and scopes |
| `.\mvnw.cmd dependency:tree "-Dincludes=com.stripe"` | Same tree, filtered to `com.stripe` libraries only |
| `.\mvnw.cmd spring-boot:run` | Compiles and starts the app (Tomcat on port 8080). Needs Postgres running first: `docker compose up -d`. Stop with `Ctrl+C` |

### Phases vs goals
- **Lifecycle phase** (no colon): `clean`, `compile`, `test`, `package`.
  Running a phase runs it *and every phase before it* (`package` also compiles and tests).
- **Plugin goal** (`plugin:goal`): `dependency:tree` = the `tree` goal of the
  `dependency` plugin (`maven-dependency-plugin`). Runs just that one task.
- **`-Dname=value`** sets a property, e.g. `-Dincludes=com.stripe` filters the tree.
  In PowerShell, wrap it in quotes so the `.` / `=` aren't misread.

## The Twelve-Factor App

A set of 12 rules (12factor.net) for apps that are easy to deploy and scale.

| # | Factor | Rule | In PayFlow |
|---|---|---|---|
| I | Codebase | One codebase in version control, many deploys (dev, staging, prod) | One git repo |
| II | Dependencies | Declare all dependencies explicitly; never rely on something "already installed" | `pom.xml` + Maven wrapper (no global Maven needed) |
| III | Config | Store config (URLs, passwords, keys) in the environment, not in code. Test: could the repo go public now without leaking a credential? | `.env` / env vars, read by `application.yml` |
| IV | Backing services | Treat databases, queues, external APIs as attached resources, swappable by changing config only | Postgres and Stripe, both reached via URLs/keys from config |
| V | Build, release, run | Keep stages separate: build = compile to an artifact, release = artifact + config, run = start it | `mvnw package` -> jar; jar + env vars -> running app |
| VI | Processes | Run as stateless processes; anything that must persist goes in a backing service | Order state lives in Postgres, not in memory |
| VII | Port binding | The app is self-contained and exports its service by binding to a port | Embedded Tomcat on 8080, no external server |
| VIII | Concurrency | Scale out by running more processes, not one bigger process | Could run several PayFlow instances behind a load balancer |
| IX | Disposability | Start fast, shut down gracefully, survive being killed anytime | Idempotent webhook handling (M5) makes retries safe |
| X | Dev/prod parity | Keep dev, staging and prod as similar as possible | Same Postgres 16 via Docker locally as in prod |
| XI | Logs | Treat logs as an event stream: write to stdout, let the environment collect them | Spring Boot logs to the console by default |
| XII | Admin processes | Run one-off tasks (migrations, scripts) as separate processes from the same codebase and config | e.g. a DB migration or data-fix command |

## How application.yml reads .env

1. **Import the file.** In `application.yml`:
   ```yaml
   spring:
     config:
       import: optional:file:.env[.properties]
   ```
   - `file:` = path relative to the working directory (project root, where `mvnw` runs)
   - `optional:` = don't fail if `.env` is missing (Docker / CI / prod use real env vars)
   - `[.properties]` = extension hint: parse `.env` as a `.properties` file
2. **Each `KEY=value` line becomes a Spring property** (`DB_PASSWORD=secret` -> property `DB_PASSWORD`).
3. **Placeholders in the yml read them:**
   ```yaml
   password: ${DB_PASSWORD}                  # required: startup fails if missing
   url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME}
   ```
   `${NAME:default}` uses `default` when `NAME` isn't set. Secrets get no default, so a
   missing one fails loudly.
4. **Code reads our own names, not env var names:** `stripe.secret-key: ${STRIPE_SECRET_KEY}`
   -> code uses `stripe.secret-key` (via `@Value` or `@ConfigurationProperties`).

**Who wins** (strongest first): OS environment variables > `.env` > `application.yml`.
If `application.properties` and `application.yml` both exist in the same folder,
`.properties` wins for the same key, so keep only one.

**.env catches** (it's parsed as `.properties`, not by a real dotenv parser):
- No inline comments: `KEY=value # note` makes the whole thing the value. Comments go on their own line.
- Quotes are kept literally: `KEY="abc"` gives `"abc"`.
- No `export KEY=value` lines.
