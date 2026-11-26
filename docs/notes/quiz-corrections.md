# Quiz corrections (where I was wrong or incomplete)

## M1 / M2
**Why stop startup if the Stripe key is missing?**
I said: because of the `IllegalStateException`. That's the *how*.
*Why*: **fail fast**. Find the problem at deploy time, not when the first customer's
checkout fails with a confusing Stripe 401 hours later.
Last 4 chars only: logs get copied, shared, stored for a long time; the last 4 identify
the key without leaking it.

**Why `long priceCents`, not `double`?**
I said: Stripe uses integer amounts. True, but the deeper reason:
`double` can't store most decimals exactly (`0.1 + 0.2 = 0.30000000000000004`); errors add up
across prices, totals, refunds. A `long` of cents is always exact.

## M3
**Why save the order *before* calling Stripe?**
I said: to keep history. True, but the main reason: **we need the order id first**:
`client_reference_id` and the cancel URL use it, and the id exists only after `save`.
Also safer: Stripe first + failed save = a payable session with **no order behind it**.

**Why is PENDING after the success redirect not a bug?**
Extra points: anyone can type the success URL without paying, and the customer may close
the tab so the redirect **never happens**. The webhook still arrives and is retried.

## M4
**Why return `OrderStatusResponse`, not the `Order` entity?**
I said: to hide the order id. Wrong example: `orderId` *is* in the DTO. The DTO hides
everything else (e.g. `stripeCheckoutSessionId`) **and any future field** added to the entity.
Second reason I missed:
- API is decoupled from the DB: entity fields can change without breaking the pages.
- Avoids lazy-loading problems (below).

**Lazy loading + Jackson: LAZY does not mean `null`.**
Hibernate puts a **proxy** (stand-in subclass of `Product`, knows only the id) in the field.
Jackson sees a non-null object and calls its getters, which triggers loading:
| Situation | Result |
|---|---|
| DB session still open (Spring's default "open-in-view") | Extra `SELECT` per product |
| Session closed | `LazyInitializationException` |
| Proxy not initialised | Jackson tries Hibernate internals -> "No serializer found" |

For lists this becomes the **N+1 problem**: 1 query for 100 orders + 100 for their products.
Fix: DTO + `@EntityGraph(attributePaths = "product")` -> one `JOIN` query, no proxies.

## M5
**Why read the webhook body as a raw `String`?**
I said: for the signature, and we may not know the body's structure. Second part wrong:
we know it (a Stripe `Event`, the SDK parses it).
Real reason: the signature is an HMAC over the **exact bytes** Stripe sent. Parsing then
re-serializing can change spacing / key order / number format -> different bytes ->
genuine events fail. Verify raw bytes first, *then* parse (`Webhook.constructEvent`).

**Completed event for an order that's already PAID: why 200, not 500?**
I said: 500 makes Stripe retry. True, but the *why*: **a retry can't fix it**, the order
stays PAID forever. 500 = up to 3 days of identical failing deliveries + noise, and Stripe
may flag/disable a constantly failing endpoint. 200 = "received, nothing to do" + `log.error`
so a human looks.

**Why `@Transactional` in the webhook service but not in `CheckoutService`?**
I said: two tables, both writes must succeed or roll back together. Correct for *why it's
needed*. Missing: *why it's safe here*: the webhook does **no external API calls**, only fast
DB work, so the transaction is short. `CheckoutService` calls Stripe (slow, can hang):
holding a DB transaction/connection open blocks the pool, and a rollback can't undo Stripe.
Rule: transactions for DB-only work, never around another company's API.

## M4 (continued)
**session_id vs order_id on the pages?**
Right: session ids are long and random (unguessable); order ids are sequential, so anyone
can try the next one (**enumeration**).
Wrong: knowing an id does **not** let anyone delete a row. The endpoints are read-only GETs.
The only risk is *reading* another order's status, product and amount.
