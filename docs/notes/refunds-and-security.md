# Refunds, idempotency keys and admin token (M6)

## Refund flow
```
POST /api/orders/{id}/refund  (header X-Admin-Token)
  -> token ok? (401)  -> order exists? (404)  -> PAID? (409)  -> has pi_...? (409)
  -> Refund.create(paymentIntent, idempotency key "refund-order-{id}")  -> 202, order stays PAID
Stripe -> charge.refunded webhook -> find order by PaymentIntent -> fully refunded? -> markRefunded()
```
- Refunds are made against the **PaymentIntent** (`pi_...`, the actual payment), not the
  Checkout Session. We store it on the order when it becomes PAID.
- Same pattern as PAID: the API call only *asks*; the **verified webhook** changes the status.
  Bonus: refunds done in the Stripe dashboard also reach our DB.
- `charge.refunded` carries a **Charge**, not a Session. It also fires for partial refunds:
  `charge.getRefunded() == true` means fully refunded.

## Idempotency keys
Problem: we call `Refund.create`, Stripe refunds, but the response is lost (timeout).
We retry -> without a key, a **second refund**.

```java
RequestOptions.builder().setIdempotencyKey("refund-order-" + orderId).build()
```
Same key again (within 24 h) -> Stripe returns the **original** refund, no new one.

- Key must be **deterministic** (from the order id), not a random UUID per call: a new key
  = a new request to Stripe.
- M5 vs M6: in M5 **we** deduplicate events Stripe sends us (events table);
  in M6 **Stripe** deduplicates requests we send it (idempotency key).

## Admin token
Order ids are guessable (7, 8, 9...), so an open refund endpoint = anyone can refund anything.
Simple protection: header `X-Admin-Token` compared with `ADMIN_API_TOKEN` from `.env`
(no default -> app fails at startup without it). Not real user auth, enough for a demo.
`required = false` on the header so a missing token gets our 401, not Spring's 400.

## Constant-time comparison
```java
MessageDigest.isEqual(providedToken.getBytes(StandardCharsets.UTF_8), adminTokenBytes)
```
**Timing attack** on `String.equals`: it stops at the first wrong character.
```
real:  s3cr3tToken
guess: axxxxxxxxxx -> stops after 1 char (fast)
guess: s3xxxxxxxxx -> stops after 3 chars (slightly slower)
```
Measure thousands of responses -> slower = more correct -> recover the secret one character
at a time.

`MessageDigest.isEqual` always checks **every byte**: same time whether the first or last
character is wrong. (Name says "digest", but it's just Java's constant-time `byte[]` compare.)
- `getBytes(UTF_8)`: it compares bytes; explicit encoding = same bytes on every machine.
- Server token converted once in the constructor (`adminTokenBytes`).
- Same idea as Stripe's signature check (M5). **Compare secrets in constant time, never `equals`.**
