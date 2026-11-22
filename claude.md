# CLAUDE.md — PayFlow (Stripe payment integration)

## What this project is
PayFlow is a small order-and-checkout backend that takes card payments through
Stripe Checkout in TEST MODE. It is a learning + portfolio project.

Stack: Java 21, Spring Boot 4.2 (Web, Data JPA, Validation), PostgreSQL (Docker
Compose), Stripe Java SDK (`com.stripe:stripe-java`), Stripe CLI for webhooks,
one plain HTML page as the shop. Build tool: Maven.

## Milestones (work on ONE at a time, in this order)
- M0 Stripe account, keys, CLI (done)
- M1 Project skeleton: Spring Boot + Postgres + config from env vars + /health
- M2 Product and Order entities, OrderStatus lifecycle, GET /api/products
- M3 POST /api/checkout creates an Order (PENDING) + Stripe Checkout Session
- M4 Success / cancel pages that READ order status from the DB
- M5 Webhook endpoint: signature verification, idempotent event handling, Order -> PAID
- M6 Refunds with Stripe idempotency keys, Order -> REFUNDED
- M7 Tests, Dockerfile, README
- M8 GitHub polish + resume

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