# Problems I hit (and fixes)

Format: **Symptom** -> Cause -> Fix

## Setup / tools
**`stripe` not recognized after `winget install`** (even in a new terminal)
-> winget installed it but didn't add its folder to PATH.
-> Added `...\WinGet\Packages\Stripe.StripeCli_...` to the user PATH. Then **restart VS Code
fully** (new terminal tabs inside VS Code keep the old PATH).

**`winget install dbeaver.dbeaver` failed**
-> Wrong package id.
-> `winget search dbeaver` to find it: `winget install --id DBeaver.DBeaver.Community -e`.

**`stripe listen ...` -> "must specify events to forward using --events..."**
-> Newer Stripe CLI requires choosing events.
-> `stripe listen --events checkout.session.completed,checkout.session.expired --forward-to localhost:8080/api/stripe/webhook`

**Every webhook -> `Rejected Stripe webhook: invalid signature` (400), even on a real payment**
-> `STRIPE_WEBHOOK_SECRET` in `.env` was a *different* secret than the one `stripe listen`
signs with (e.g. from a dashboard endpoint or an older login). Both start with `whsec_`.
-> `stripe listen --print-secret`, put that value in `.env`, **restart the app** (`.env` is
read only at startup). Then pay again or `stripe events resend evt_...`.

**`docker compose ...` -> "failed to connect to the docker API at npipe://..."**
-> Docker Desktop isn't running.
-> Start Docker Desktop, wait for "running".

## Project generation
**Initializr offered only Spring Boot 4.x, no 3.x**
-> 3.x no longer offered for new projects.
-> Used 4.2. Tutorials differ: `starter-web` is now `starter-webmvc`; test starters are split
per starter.

**VS Code Initializr created a `payflow/` subfolder**
-> The extension always creates a folder named after the artifact.
-> Moved everything up one level (except its `.gitignore`; kept mine).

## Config
**App won't start: "Failed to configure a DataSource"**
-> JPA + Postgres driver on the classpath, but no DB URL configured.
-> Postgres in Docker + `spring.datasource.*` in `application.yml`.

**Inline comment in `.env` became part of the value**
-> Spring parses `.env` as `.properties`: `KEY=value # note` = the whole thing is the value.
-> Comments on their own line only.

**Both `application.properties` and `application.yml` existed**
-> Same key in both: `.properties` wins, so yml edits seem ignored.
-> Deleted `application.properties`.

## Running
**`/health` works but `/api/products` -> 404**
-> An **old** app instance (started before the new code) was still on port 8080; the new
run couldn't start ("Port 8080 already in use").
-> Stop the old one (`Ctrl+C` / red stop button), then start again.

**`curl` in PowerShell behaves strangely**
-> In Windows PowerShell 5.1, `curl` is an alias for `Invoke-WebRequest`.
-> Use `curl.exe`. JSON body needs escaped quotes: `-d '{\"productId\":2,\"quantity\":1}'`.

**Stripe redirected to the old URL (404) after changing return URLs**
-> A session keeps the URLs it was created with.
-> Create a **new** checkout after changing them.

**Red failed requests in DevTools on the Stripe Checkout page**
-> uBlock/privacy extension blocking Stripe's analytics/fraud calls (made by my browser).
-> Harmless if payment works. Only worry about errors on `localhost:8080/api/...`.

## Code
**`markCancelled()` didn't change the DB (still PENDING)**
-> Changed only the Java object. Without `@Transactional`, Hibernate doesn't auto-save changes.
-> `orderRepository.save(order)` after `markCancelled()`.

## Git
**M2 files ended up in the M1 commit**
-> `git add .` picked up everything in the folder.
-> Not pushed yet, so: `git rm --cached -r <M2 files>` (keeps files on disk) + stage the
M1-only version of mixed files + `git commit --amend --no-edit`.
Lesson: check `git status` before every commit.

**Mixed file (`application.yml`) still had the M2 block in the M1 commit**
-> The M1-only version wasn't staged before amending.
-> Cut the block, `git add` the file, amend, paste the block back.
