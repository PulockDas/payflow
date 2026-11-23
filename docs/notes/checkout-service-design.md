# CheckoutService design notes

## What `@Transactional` does
Wraps a method in one database transaction:
- Spring opens a transaction (and holds a DB connection) when the method starts.
- **Commit** when the method returns normally: all changes are saved together.
- **Rollback** if it throws a `RuntimeException`: none of the changes are saved.
- All or nothing: you never end up with half the writes done.

Without it, each `repository.save(...)` runs in its own short transaction and commits
immediately (Spring Data repository methods are transactional by themselves).

## Why `startCheckout` has no `@Transactional`
The method calls Stripe (network, hundreds of ms, can hang or fail).
With `@Transactional` around it:
- The DB connection stays busy while waiting on Stripe. The pool is small (~10 by
  default), so a slow Stripe could block the whole app.
- A rollback can't undo the Stripe call anyway: if Stripe created the session but our
  commit fails, Stripe still has it. The transaction gives a false sense of safety.

So: `save(order)` commits -> Stripe call (no transaction open) -> `save(order)` again.
If Stripe fails, the order stays `PENDING` with no session: harmless, can be cleaned up.

Rule of thumb: **no external API calls inside a DB transaction.**

## Why `Math.multiplyExact`
`long total = price * quantity;` silently **overflows** if the result is too big: it
wraps around to a wrong (even negative) number, no error.

`Math.multiplyExact(price, quantity)` does the same multiplication but throws
`ArithmeticException` on overflow. With money, a loud error is better than a wrong amount.
(Unlikely here with `quantity <= 10`, but it costs nothing.)

## How the Stripe key is set without the service touching it
1. `.env`: `STRIPE_SECRET_KEY=sk_test_...`
2. `application.yml`: `stripe.secret-key: ${STRIPE_SECRET_KEY}`
3. `StripeConfig` (a `@Configuration` bean) gets it via `@Value("${stripe.secret-key}")`
   and, in `@PostConstruct`, sets the **static** field `Stripe.apiKey = secretKey`
   once at startup (fails startup if blank).
4. Every SDK call (e.g. `Session.create(...)`) reads `Stripe.apiKey` and sends it as
   `Authorization: Bearer sk_test_...`.

So `CheckoutService` never sees the key: it's set globally before any request arrives.

## Who builds which URL
- **Stripe** returns the payment page URL (`session.getUrl()`); we just pass it on.
- **We** build the return URLs (`success_url`, `cancel_url`) from `app.base-url`.
  `{CHECKOUT_SESSION_ID}` in `success_url` is filled in by Stripe on redirect.
