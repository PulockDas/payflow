# Frontend JS basics (success / cancel pages)

## async / await + fetch
`fetch(url)` makes an HTTP request and returns a **Promise** (a value that arrives later).
`await` pauses *that function* until the Promise is done, without freezing the page.
`await` only works inside an `async function`.

```js
async function checkOrderStatus() {
    const response = await fetch("/api/orders/by-session/" + id); // wait for HTTP response
    if (!response.ok) throw new Error("HTTP " + response.status);  // fetch does NOT throw on 404/500
    const order = await response.json();                          // wait for body -> JS object
}
```
- `fetch` only throws on **network** errors. HTTP errors (404, 500) must be checked via
  `response.ok` / `response.status`.
- Wrap in `try { ... } catch (error) { ... }` to handle both.

## setTimeout vs setInterval
| | What it does |
|---|---|
| `setTimeout(fn, 2000)` | Runs `fn` **once**, after 2 s. Doesn't block the page, just schedules |
| `setInterval(fn, 2000)` | Runs `fn` **every** 2 s until `clearInterval(...)` |

**Polling** = asking the server again and again until something changes.
In `success.html`: call `setTimeout(checkOrderStatus, 2000)` at the end of each check.
The next request starts only after the previous one finished, so slow responses never
overlap (with `setInterval` they could pile up). Stop after a max number of attempts.

## XSS (Cross-Site Scripting)
- `el.innerHTML = text` -> parses `text` **as HTML**: tags are created, scripts can run.
- `el.textContent = text` -> shows `text` **as plain characters**: nothing runs.

Example: a product name stored as `<img src=x onerror="steal(document.cookie)">`
- `innerHTML` -> real `<img>`, load fails, `onerror` runs the attacker's code.
- `textContent` -> the characters are just displayed.

**Who is who**
- **Attacker**: an outsider who gets text into our data (review, username, URL param).
- **Victim**: every customer who opens the page. The script runs in *their* browser,
  as if our site sent it -> steal sessions, fake "re-enter card" forms, redirects.
- Also bad for us: our domain served the attack (trust, reputation, legal).

**The attacker never changes our code.**
- Reading it is normal: frontend code is public (View Source).
- Editing it in their own DevTools only changes *their* tab ("self-XSS"), not what
  other customers download.
- XSS = attacker sends **data**, our own **unchanged** code mishandles it (`innerHTML`).

**Rule:** data from anywhere (even our own DB) goes on the page with `textContent`.

## encodeURIComponent
URLs have special characters: `/` (path), `?` (query), `&` (params), `#` (fragment).
A value inserted raw can change the URL's meaning:
```js
"/api/orders/by-session/" + "abc/../../products"                     // path navigation
"/api/orders/by-session/" + encodeURIComponent("abc/../../products") // abc%2F..%2F..%2Fproducts
```
`session_id` comes from the address bar (anyone can type anything), so encode it to make
sure it stays **one** value. Use it for every value put into a URL.

## formatAmount / Intl.NumberFormat
Built into JavaScript (ECMAScript Internationalization API), nothing to install.
Formats numbers by locale and currency:
```js
function formatAmount(amountCents, currency) {
    return new Intl.NumberFormat("en-US", { style: "currency", currency: currency.toUpperCase() })
        .format(amountCents / 100);
}
formatAmount(1999, "usd"); // "$19.99"
```
Dividing by 100 happens **only here, for display**. Money values stay in cents everywhere else.

## Failed requests on the Stripe Checkout page (DevTools)
Usually harmless: the Stripe page is Stripe's, not ours.

**Who makes them:** *our browser*, not Stripe's server.
1. The browser loads Stripe's Checkout page.
2. Stripe's JavaScript (running in the browser) calls Stripe's own servers for
   analytics, fraud signals (device/behaviour data for Stripe Radar), and wallet checks
   (Google Pay / Apple Pay / Link).
3. An ad/privacy blocker (uBlock, Brave Shields) inside the browser checks each outgoing
   URL against **filter lists** of known tracker/analytics domains and cancels matches
   (`(blocked:other)` / `ERR_BLOCKED_BY_CLIENT`).

**Why they're blocked:** some of those calls collect device/behaviour data. It's for fraud
detection, but it looks like tracking, and uBlock only matches URLs, not intent. The actual
payment request isn't on the lists, so paying still works.

**When to worry:** only if the payment itself fails, or our own requests
(`localhost:8080/api/...`) show 4xx/5xx.

## Browser redirect vs webhook
| | Success redirect | Webhook (M5) |
|---|---|---|
| Who sends it | Customer's **browser** | Stripe's **server** -> our server |
| Can be blocked/skipped | Yes: closed tab, extension, network drop | No browser involved |
| Can be faked | Yes: anyone can type the URL | No: signed by Stripe, we verify the signature |
| Retries | No | Yes, Stripe retries if our server is down |

So the redirect only *shows* a page; only the verified webhook may mark an order PAID.

Reference for all of the above: MDN (developer.mozilla.org).
