# M1 — Project skeleton

## Overview
A Spring Boot app that starts, connects to Postgres in Docker, reads all config
(DB credentials, Stripe keys) from `.env` / environment variables, and answers
`GET /health` with `ok`.

What it gives the project:
- A base every later milestone builds on (entities, checkout, webhooks).
- Secrets kept out of the code and out of git from day one.
- Fail-fast startup: a missing Stripe key stops the app immediately, not on the first payment.
- A health endpoint to check "is it up?" (Docker/monitoring use it later).

## Steps, in the order I did them
1. **Tools:** Java 21 (Temurin), Docker Desktop, Git, Stripe CLI (`stripe login`, sandbox).
2. **`.gitignore`:** ignore `target/`, IDE files, and `.env` (keep `.env.example`).
3. **Generate the project:** VS Code Spring Initializr -> Maven, Java 21, Boot 4.2,
   group `com.pulock`, artifact `payflow`, deps: Web, Data JPA, Validation, PostgreSQL.
4. **Add `stripe-java`** to `pom.xml` (explicit version; the Boot parent doesn't manage it).
5. **`docker-compose.yml`:** Postgres 16; DB name/user/password from `.env`; named volume.
6. **`.env.example`** (placeholders, committed) -> copy to **`.env`** (real values, ignored).
7. **`application.yml`:** `spring.config.import: optional:file:.env[.properties]`,
   datasource built from `${DB_*}`, `stripe.*` from `${STRIPE_*}`.
   Deleted `application.properties`.
8. **`StripeConfig`:** sets `Stripe.apiKey` at startup, throws if blank, logs last 4 chars.
9. **`HealthController`:** `GET /health` -> `ok`.

## Run it
```powershell
docker compose up -d              # start Postgres (wait for "healthy": docker compose ps)
.\mvnw.cmd spring-boot:run        # start the app
curl.exe http://localhost:8080/health   # -> ok
```
Stop: `Ctrl+C` (app), `docker compose down` (DB, data kept).
