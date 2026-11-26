# CLAUDE.md — PayFlow (Stripe payment integration)

## What this project is
PayFlow is a small order-and-checkout backend that takes card payments through
Stripe Checkout in TEST MODE. It is a learning + portfolio project.

Stack: Java 21, Spring Boot 4.2 (Web, Data JPA, Validation), PostgreSQL (Docker
Compose), Stripe Java SDK (`com.stripe:stripe-java`), Stripe CLI for webhooks,
one plain HTML page as the shop. Build tool: Maven.

## Milestones (work on ONE at a time, in this order)
- M0 Stripe account, keys, CLI (done)
- M1 Project skeleton: Spring Boot + Postgres + config from env vars + /health (done)
- M2 Product and Order entities, OrderStatus lifecycle, GET /api/products (done)
- M3 POST /api/checkout creates an Order (PENDING) + Stripe Checkout Session (done)
- M4 Success / cancel pages that READ order status from the DB (done)
- M5 Webhook endpoint: signature verification, idempotent event handling, Order -> PAID (done)
- M6 Refunds with Stripe idempotency keys, Order -> REFUNDED (done)
- M7 Tests, Dockerfile, README
- M8 GitHub polish + resume

## Current status
- M0-M6 done and tested, pushed to GitHub (private: PulockDas/payflow). Next: M7.
- Refunds (M6): `POST /api/orders/{id}/refund`
  (`refund/RefundController` checks `X-Admin-Token` vs `ADMIN_API_TOKEN`, constant-time;
  `refund/RefundService` -> `Refund.create` with idempotency key `refund-order-{id}`,
  returns 202, does NOT change status). `charge.refunded` webhook (Charge object, fully
  refunded only) -> `markRefunded()`. Order stores `stripePaymentIntentId` (set when PAID).
  `stripe listen --events` includes `charge.refunded`.
- Webhook (M5): `POST /api/stripe/webhook`. `webhook/StripeWebhookController` verifies
  the signature on the raw body (400 if invalid). `webhook/StripeWebhookService` (I wrote
  it) is `@Transactional`: skip if event id in `processed_stripe_events`, else
  `saveAndFlush` the event id first (entity implements `Persistable` -> plain INSERT),
  then `checkout.session.completed` + paid + amount matches -> `markPaid()`;
  `checkout.session.expired` -> `markCancelled()` if PENDING. Unrecoverable cases
  (unknown session, wrong state, amount mismatch) log and return 200, so Stripe stops retrying.
- Local webhooks: `stripe listen --events checkout.session.completed,checkout.session.expired,charge.refunded
  --forward-to localhost:8080/api/stripe/webhook`; `.env` STRIPE_WEBHOOK_SECRET must equal
  `stripe listen --print-secret`.
- Frontend: static files in `resources/static/` (no template engine): `index.html`
  (shop), `checkout/success.html` (polls every 2s, max 15, while PENDING),
  `checkout/cancel.html`. DOM built with `textContent`, never `innerHTML`.
- Read-only order API (`controller/OrderController`): `GET /api/orders/by-session/{id}`,
  `GET /api/orders/{id}` -> `OrderStatusResponse`. `OrderRepository` methods use
  `@EntityGraph(attributePaths = "product")`.
- Decisions: Spring Boot 4.2 (no 3.x offered by Initializr), Java 21, Stripe
  sandbox account, `ddl-auto: update` (no Flyway/Liquibase).
- Service layer: `checkout/CheckoutService` and `refund/RefundService` (DB + Stripe),
  `webhook/StripeWebhookService` (DB only, `@Transactional`). No `@Transactional`
  around Stripe calls (don't hold a DB transaction during an external API call).
- Logging: `info` normal flow, `warn` unusual but harmless / client mistakes, `error`
  only when a human must act.
- Stripe Session uses inline `price_data` from our DB, `client_reference_id` = order id;
  the order stores `stripeCheckoutSessionId` (unique) for the M5 webhook lookup.
- Return URLs: `/checkout/success.html?session_id={CHECKOUT_SESSION_ID}` and
  `/checkout/cancel.html?order_id=...` (static pages in `resources/static/checkout/`).
- `Order` status methods (I wrote them): `markCancelled()` and `markPaid()` (PENDING
  only), `markRefunded()` (PAID only).
- Config: `application.yml` imports `.env` via `spring.config.import`;
  `StripeConfig` sets `Stripe.apiKey` at startup. Secrets in `.env`: DB_*,
  STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET, ADMIN_API_TOKEN (no defaults; APP_BASE_URL optional).
- Dev env: Windows + PowerShell (use PowerShell syntax in commands).
- Run: VS Code launch config "PayFlow (DB + Stripe + app)" (Postgres + `stripe listen` +
  app; `test:` tasks for webhook checks) or "PayFlow (DB + app)". Without VS Code:
  `docker compose up -d` then `.\mvnw.cmd spring-boot:run`. (`.vscode/` is gitignored.)
- Reference notes in `docs/notes/`.

## How to work with me (I am learning — no vibe coding)
1. Before writing code for a step, explain the concept in 5-10 lines in plain English.
2. Show the plan first (files, classes, endpoints), then wait for my "go".
3. Make small changes: one file or one class at a time, then STOP so I can review.
4. After each change, tell me: what changed, why, and how I can test it myself.
5. Never jump ahead to a later milestone, even if it seems convenient.
6. For the core payment logic (webhook handler, order status changes), give me
   hints and let me write it. Only give the full code if I ask for it.
7. If I ask "why", answer the why — don't just rewrite the code.
8. At the end of a milestone, quiz me with 3 short questions about what we built.

## Rules for the code
- NEVER put secrets in code or commit them. Secrets live in `.env` (gitignored).
  `.env.example` holds placeholder values only.
- Money is always `long` amounts in cents (e.g. $19.99 = 1999). Never `double`/`float`.
- Prices always come from our database, never from the browser.
- The order is marked PAID only by a verified Stripe webhook, never by the
  success-page redirect.
- Keep it simple: no extra frameworks or layers unless I ask.
- Package root: `com.pulock.payflow`.

## Notes
After each milestone I write `docs/notes/M<n>-<name>.md` myself. Don't write
those notes for me; you may review them if I ask.