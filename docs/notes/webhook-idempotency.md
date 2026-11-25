# Webhooks, transactions and idempotency (M5)

## Signature check (HMAC-SHA256)
Shared secret `whsec_...`, known only to Stripe and us.
- **Stripe:** `signature = HMAC(secret, timestamp + "." + rawBody)` -> header
  `Stripe-Signature: t=...,v1=...`
- **Us** (`Webhook.constructEvent`): recompute with our secret, compare with `v1`
  (constant-time), check `t` is recent (< 5 min, stops replay).
- Needs the **raw body**: re-serialized JSON = different bytes = signature fails.
- Proves *who sent it* and *nothing changed*. Not encryption (HTTPS does that).

## How Stripe reaches localhost
`stripe listen` opens an **outgoing** connection to Stripe; events come back down it and
the CLI POSTs them to `localhost:8080/api/stripe/webhook`. Keep it running while testing.
Production: register a public HTTPS URL in the dashboard (its own `whsec_...`).

## Reply codes
- **2xx** = "received", Stripe stops. Anything else / no answer = Stripe **retries** (up to ~3 days).
- Retry can't fix it (unknown order, wrong state, amount mismatch) -> log + return **200**.
- Bad signature -> **400**.

## @Transactional in the webhook
- Event row insert + order update = **one unit**: both commit, or both roll back.
- Exception leaves `handleEvent` -> rollback -> event not recorded -> Stripe retry works cleanly.
- Early `return` = normal exit = **commits** (event recorded, order unchanged). Intended.
- OK here because there are **no external calls** (unlike `CheckoutService`).

## Why no `save()` for the order (dirty checking)
Inside a transaction, a loaded entity is **managed**: Hibernate keeps a snapshot of it.
At commit it compares entity vs snapshot and runs `UPDATE` for changed fields.
`markPaid()` -> commit -> UPDATE, automatically.

M3 `CheckoutService` had no transaction -> the order was **detached** (nobody watching)
-> `save()` was required (my `markCancelled` bug).

`save()` on a managed entity: harmless, no difference.

## Why the processed-events table
The status guard alone (`markPaid` only from PENDING) protects only the status:
```
A: load order -> PENDING        B: load order -> PENDING   (A not committed yet)
A: guard ok -> markPaid          B: guard ok -> markPaid
A: email / stock - 1             B: email / stock - 1        <- done twice
```
Each request checks its **own in-memory copy**. The event id as **primary key** is checked
by the **database**, which sees all requests: only one INSERT can win.
Also: one rule for all event types, and an audit log.

## Persistable vs saveAndFlush (need both)
| | Controls | Without it |
|---|---|---|
| `Persistable.isNew()` | **Which** SQL: plain INSERT vs merge | Assigned id -> Spring does **merge** (SELECT, then UPDATE if found) -> duplicate silently overwrites |
| `saveAndFlush()` | **When** SQL runs: now vs at commit | INSERT at commit -> duplicate fails only **after** doing the work |

**Why `saveAndFlush` alone isn't enough** (merge does `SELECT` then INSERT/UPDATE):
| B arrives... | Result |
|---|---|
| after A committed | `existsById` skips it. OK |
| same time as A | merge SELECT sees nothing -> INSERT waits for A -> duplicate error. OK |
| A commits *between* B's `existsById` and B's SELECT | SELECT finds row -> **silent UPDATE** -> work done twice. BUG |

`Persistable` -> always plain INSERT -> always fails on duplicate, whatever the timing.

**Persistable doesn't block updates:**
- `new ProcessedStripeEvent(...)` -> `isNew() = true` -> INSERT.
- Loaded from DB -> `@PostLoad` sets flag false -> changes saved as UPDATE.
- `@Transient` just keeps the flag out of the table.
(Our event rows are write-once anyway: no setters, `processedAt` not updatable.)

**`save()` vs `saveAndFlush()` in a transaction:** `save()` only *queues* the INSERT
until commit/flush. `saveAndFlush()` sends it **now**, so a concurrent duplicate waits on
the primary key and fails **before** doing any work.

## Log levels
- `info`: normal flow, harmless skips (duplicate event, unpaid session, already cancelled).
- `warn`: unusual but harmless (unknown session, bad signature).
- `error`: a human must look (paid but order not PENDING, amount mismatch).
