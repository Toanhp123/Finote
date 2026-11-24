# Failed inbox (roadmap step 13): design

Date: 2026-11-23. Status: v3 draft, waiting for the user's review. v1 went through a builder check and an external critique (Gemini); v2 through an `architect` risk review against the code; v3 answers all three (see "Review log" at the end).
Branch (planned): `feature/failed-inbox` from `dev`. Source: spec Module 3 (L38–40), MoSCoW "Hộp thư lỗi (Failed Inbox) & Regex Sandbox" (Should-Have, L314); `docs/roadmap.md` step 13, debt N-07. Uses foundations N1 (one ledger write path), N2 (shared transaction form), N3 (`isCoveredByAdjustment`).

## Why

Dogfood showed the system rules catch the normal Techcombank events (transfers in and out, card spend at home). What still ends as `FAILED` is mostly bank promotions that pass the keyword filter. But real money also slips through: a credit card payment abroad posts an English alert that no rule matches, so it sits in `notification_logs` as `FAILED` and only a debug build can see it. The ledger then misses a spend and nobody knows.

The inbox is the safety net for the day a bank changes its wording and the rules (OTA included) have not caught up. It must (1) surface real missed money so the user can book it with little effort and no wrong guess, and (2) keep the promotions out of the way, learning from the user which ones are promotions.

Real samples (masked, from the user, 2026-11-23):

```text
# Missed money: Techcombank credit card abroad, no rule matches
title:   - KRW 11,111/VND 1,111,111
content: 1% Foreign Transaction Fee (Conditions Apply)
         Credit card: 1 .... .... 1111
         Converted amount: VND 1,111,111
         Available limit: VND 111,111
         Payment at (JOO)GIMCHISEUKOOL on 11/11/1111 at 11:11:11

# Promotion that passed the filter
title:   🥤 Mời bạn ly cà phê đầu tuần
content: Miễn phí loạt mã Highlands mua 1+1, Katinat mua 1+1, giảm 11K Circle K...  dành riêng cho bạn, số lượng có hạn. Bấm vào nhận ngay!
```

## Goals

- A `FAILED` log that carries signs of the user's money shows in the inbox; tapping it opens the cash entry sheet prefilled with what the app can read for sure, next to the original text; saving books it and removes it from the inbox.
- Promotions are hidden by a bank-agnostic money-signal filter. A promotion that still shows can be marked "this is an ad", which teaches a user keyword so the next one is filtered at capture. A wrong keyword can be removed, and removing it restores what it hid.
- The user learns there is something to look at: a banner on the transaction list and a red dot on the Settings tab and on the inbox row.
- Works in release builds, not only debug and dogfood.

## Non-goals

- **Regex Sandbox** (the user highlights amount, mask, balance and the app writes a regex). It is hard to do right for non-technical users; it moves to its own beta branch after step 13 (roadmap update in this branch).
- Patching system parser rules in the app. Bank wording is fixed through the OTA feed, not here.
- A system notification (needs N7, step 18).
- Detecting that the user already entered the same spend by hand before opening the inbox. The user can dismiss the item.
- Notifications of packages with no active rule (`NotTracked`): nothing is stored, so nothing can show.
- Foreign currency amounts (`originalAmount`, after v1.0). The prefill uses the VND figure only.

## Anti-goals

- **Hiding a real transaction.** A false "show" costs one tap; a false "hide" is a silent money gap, the very thing the inbox exists to catch. When in doubt, show. Hidden items stay reachable and actionable.
- **A user keyword that blocks real money.** User keywords never touch text a rule parses, and a keyword is refused when it matches a stored real notification or another inbox item that looks like money.
- **Counting money twice.** No booking without an adjustment check; a bank + wallet pair is one item and one transaction.
- **Booking into a guessed account.** The account is prefilled only when the app can resolve it; otherwise the user must pick.
- **Losing a user keyword to an OTA or seed update.** User keywords live outside `parser_rules`.
- Any notification text, amount, balance or mask in Logcat.

## Approaches

| Approach | Trade-off | Verdict |
|---|---|---|
| **1. Filter at read time; user keywords in a new table** | No new log column for the filter; a filter change applies to old logs at once; one small indexed read per capture | **Chosen by the user** |
| 2. Score once at capture, store in a new log column | Marginally faster read; a filter change leaves old scores stale and needs a backfill | Rejected |
| 3. Store user keywords in a fake `isUserCustom` rule per bank | No new table; abuses the rule table with a regex that never matches; keyword handling reads per rule, easy to break | Rejected |

Filter location (asked separately): **A, in app code** as a pure domain function, chosen by the user over shipping the signals in the OTA feed. Reason: the bank wording changes often and already goes through OTA; "does this text carry the user's money" barely changes.

## Design

### 1. Which logs are inbox items

An inbox item is a log with status `FAILED` whose `failureReason` is one of:

| Reason | Origin | Has parsed fields | In inbox |
|---|---|---|---|
| `no_rule_matched` | capture | no | shown when the money-signal filter says money (§2), else under "Hidden" |
| `ambiguous_account`, `account_archived`, `invalid_amount`, `invalid_time`, `settle_error` | settlement | yes (amount may be missing for `invalid_amount`) | always shown |
| `adjusted_after` | settlement | yes | never (already covered by an adjustment) |
| `partner_failed` (new, §1.1) | settlement | yes | never (its bank side is the item) |
| `user_dismissed` (new, §4) | user | either | never |
| `stuck_repost`, `stuck_ignored` (new, §1.3) | settlement | yes | never |

Also excluded, at read time:
- a log of a package that has no active rule (§6);
- a `no_rule_matched` log whose `notificationKey` is shared by a log of the same package with status `CONFIRMED`, `PAIRED_TRANSFER` or `DEDUPLICATED` within `REPOST_WINDOW_WITH_BALANCE_MILLIS` of it: the same bank event was already booked through a later, parseable version (`KeyClusters` never roots an unparsed log, so settlement cannot dedupe it).

**Copies.** `no_rule_matched` logs of one package with identical title and content are one item (`contentHash` includes `postTime`, so true copies have different hashes, and repost detection only runs on `PENDING` logs). Booking or dismissing the item applies to every copy (§4).

Ordered newest first by `postTime`, then `id`. Hidden `no_rule_matched` logs are listed under a collapsed "Hidden (N)" section at the end. Hidden items have every action a shown item has (book, this is an ad, dismiss): the filter can be wrong.

**Counts.** Every count the user sees (banner "N", badge, section headers) counts inbox items as defined here, never raw rows.

#### 1.1 Bank + wallet pairs

Settlement pairs a bank log and a wallet log in the same direction within 30 s: one spend reported twice (a wallet payment funded from the bank), not a transfer. Today `fail()` marks both with the same reason, and no column links them, so the inbox could only re-guess the pair at read time.

Change in `NotificationSettlementRepositoryImpl.fail()`: the bank side gets the decision's reason; the wallet partner gets `partner_failed`. The two updates run in one `db.storageWrite` (today it is one `UPDATE`; split in two, a crash between them would leave the wallet `PENDING`, the next settle would book it alone, and booking the bank item would count the spend twice). Result: one inbox item per pair, the bank side; the wallet log never shows on its own, so it can never be booked or dismissed separately. Booking or dismissing the bank item does not touch the wallet log (it stays `FAILED` / `partner_failed`, invisible). No pair re-guessing anywhere.

Logs failed as pairs before this change keep the same reason on both sides and would show as two items. Accepted: wallet pairs need MoMo, which dogfood does not have. A note goes in the debt ledger.

#### 1.3 Stuck fallbacks

`SettlePendingLogsUseCase.failIfStuck` turns every stuck decision into `Fail(…, settle_error)`. For a `Repost` that would make a duplicate of a booked event bookable; for an `Ignore` it would surface a log of an account the user ignores. Change: a stuck `Repost` fails with `stuck_repost`, a stuck `Ignore` with `stuck_ignored`; both are outside the inbox set. A stuck `Book` or `Fail` keeps `settle_error` (shown).

### 2. Money-signal filter

A pure function in `domain/notification/service`, `looksLikeMoney(title, content): Boolean`, on `"$title\n$content"` after `normalizeForMatch`. It knows no bank. Signals:

1. **Masked account or card number**: at least two mask characters (`*`, `.`, `x`, `•`, possibly separated by spaces) followed by 3 to 6 digits, which are not followed by another digit, a letter, `%` or `k`. Matches `1 .... .... 1111`, `TK ***1234`, `...5678`; does not match `Circle K... 50K` or `...50%`.
2. **Amount with currency**: a number (digits with `.` or `,` separators) with a currency token right before or after it: `vnd`, `d`/`đ`, `₫`, or a three-letter ISO-looking code before the number (`krw 11,111`). A sign is not required.
3. **Money word anywhere in the text**, whole word or phrase: balance family (`so du`, `balance`, `available`, `han muc`, `limit`) or transaction family (`thanh toan`, `payment`, `giao dich`, `gd`, `bien dong`, `chuyen tien`, `nhan tien`, `converted amount`, `so tien`, `amount`, `chi tieu`).

Rule: **money if (1), or if (2) and (3)**. Otherwise not money.

The two label families are separate sets in code: §4 uses them differently.

Test oracle (unit tests, fixtures made up where not from the corpus):
- Every `shouldIgnore: false` sample in `app/src/test/resources/fixtures/notification_corpus.json` reads as money.
- Abroad card sample: money. Coffee promo sample: not money.
- `Bạn vừa thanh toán 150,000đ tại Highlands Coffee`: money. `Biến động số dư: 500,000 VND. Số dư hiện tại: 2,000,000 VND`: money.
- `Giảm đến ...50% cho đơn từ 100K`: not money. `Circle K... 50K`: not money.
- Spam samples in the corpus are reported, not asserted. A promotion that reads as money is an accepted false show (for example "hoàn tiền 50.000đ khi thanh toán"); "this is an ad" handles it.

### 3. User ignore keywords

New table `user_ignore_keywords`: `id` (PK, UUID), `packageName`, `keyword` (as typed, for display), `normalizedKeyword` (`normalizeForMatch(keyword).trim()` with inner spaces collapsed), `createdAt`. Unique on `(packageName, normalizedKeyword)`. Index on `packageName`. `toString` hides the keyword.

Migration 10 → 11, one migration for the whole branch, with a migration test (`MigrationPolicyTest` pattern), `DATABASE_VERSION = 11`, new schema JSON. Never touched by `ParserRuleSeeder` or `applyRules`.

**Where they apply.** `classifyNotification` order becomes: secret check, rule channel and keyword filter, parse, and only when the parse finds no match, user keywords. So a user keyword can never hide a notification a rule reads as a transaction. A user-keyword match is `IGNORED_SPAM` with reason `user_keyword:<normalizedKeyword>`, instead of `FAILED` / `no_rule_matched`. `CaptureNotificationUseCase` and `ReparseFailedLogsUseCase` read the package's user keywords next to its rules (one indexed query). Matching collapses runs of whitespace (including NBSP) on both the keyword and the text.

**Re-parse covers keyword-hidden logs.** `ReparseFailedLogsUseCase` also reads `IGNORED_SPAM` logs whose reason starts with `user_keyword:`, and its guarded update accepts that status and reason as well as `FAILED`. So an OTA rule that later reads such a log books it, and a log captured with a keyword that was deleted meanwhile (capture reads keywords before it inserts) is restored on the next re-parse. Keyword-hidden logs also show under "Hidden" with a Restore action (= delete that keyword).

**"This is an ad"** (offered on `no_rule_matched` items only, shown or hidden):

1. A sheet shows a suggested keyword: the notification title with emoji and leading or trailing punctuation stripped, in an editable field. Under the field, live: "N other items will be hidden too".
2. On save, refused when:
   - the normalized keyword is shorter than 3 characters;
   - it does not match the selected log itself;
   - it is the bank name of that package (from its rules' `bankName`), normalized;
   - `firstMatchingKeyword` finds it in any stored log of the package that is real money: status `PENDING`, `CONFIRMED`, `DEDUPLICATED`, `PAIRED_TRANSFER`, `IGNORED_ACCOUNT`, or `FAILED` with a reason other than `no_rule_matched`;
   - it matches another `FAILED` / `no_rule_matched` log of the package, other than the selected one, for which `looksLikeMoney` is true.
   Each refusal says why ("this phrase is also in a real transaction"). The user edits and retries.
3. On success, in one write: insert the keyword, then mark the package's `FAILED` / `no_rule_matched` logs the keyword matches as `IGNORED_SPAM` / `user_keyword:<kw>` with a plain guarded `UPDATE` (matching runs before the write, so no regex parse holds the write lock; the guard makes a log that changed meanwhile a no-op).

**Managing keywords.** The notification capture settings page gets a small "Ad keywords" list per bank with a delete action. Deleting a keyword, in one write, removes the row and turns every log with `IGNORED_SPAM` / `user_keyword:<that kw>` of that package back into `FAILED` / `no_rule_matched`, then re-parses those logs (a newer rule may read them now). After "this is an ad", a snackbar offers Undo, which runs the same delete.

### 4. Booking an item

Tapping an item opens the cash entry sheet (N2) in a new **from-log** mode. The original notification (title and content, collapsible) shows above the form, so the user can check amount, card digits and direction.

Prefill:

| Field | Settlement-failure log | `no_rule_matched` log |
|---|---|---|
| Type | `parsedType` | a `+` or `-` attached to an amount with currency (signal 2 match, sign right before the number or the currency token) gives Income or Expense; else Expense, with the type selector visible |
| Amount | `parsedAmount` when > 0; else as for `no_rule_matched`; else empty | `extractVndAmount` (below); else empty |
| Account | the one live, usable owner of the parsed mask in `account_identifiers` of that package, any `IdentifierKind`; else empty | the last 4 digits of the signal-1 mask (every system rule captures exactly 4), looked up the same way; else empty |
| Note | `parsedMerchant` or `parsedNote` | text after `payment at` / `tại` / `at` up to ` on ` or end of line, if found; else empty |
| Date and time | `postTime`, exact | `postTime`, exact; for `invalid_time` the date is empty and required |

Account lookup is a plain identifier lookup, **not** `resolveAccount`: no "only account of the package" fallback, no attach, no new account. With a null rule `resolveAccount` assumes `ACCOUNT`/`BANK` and attaches to a lone account, which would book the abroad credit-card spend on the debit account when no card account exists yet. An empty account is required before Save; the sheet never falls back to the last manual account. For `account_archived` the sheet names the archived account under the empty field.

**Timestamp.** In from-log mode the stored timestamp is exactly `log.postTime` unless the user changes the date; then it is the chosen day at the same time of day as `postTime` (for `invalid_time`, noon). It must not go through `AddManualTransactionUseCase.timestampOf` (now for today, noon otherwise): a missed 08:00 spend booked at 12:00, after a 10:00 balance row that already includes it, is counted twice by the provisional start and the drift check stays silent.

`extractVndAmount(title, content)`: candidates are VND amounts (signal 2 with a VND token). Priority: (a) a signed VND amount; (b) a VND amount on the title line; (c) a VND amount after a transaction-family label; (d) the first VND amount not after a balance-family label or a fee word (`phi`, `fee`). VND has no minor unit: a trailing `.00` or `,00` (exactly two digits after the last separator) is dropped, then the remaining separators are stripped. Overflow or zero gives empty. Tested on the abroad sample (1,111,111), on `VND 1,111,111.00`, on `1.111.111,00 VND`, and on `Phí GD: VND 11,111 … Converted amount: VND 1,111,111` (1,111,111).

**Save, two phases.** The check and the writes are inside `TransactionRunner.write`; a dialog cannot be, so:

1. `BookFailedLog(logId, fields, confirmation = null)` runs one write (a data-layer repository method, like settlement's `book()`, because `LedgerWriter` is internal to data; `TransactionRules.check` with the category type runs inside the same write; no cold start):
   - Every log of the item must still be `FAILED` with an inbox reason; else return `AlreadyHandled`, write nothing.
   - The same-key check of §1 runs again; a booked later version returns `AlreadyHandled`.
   - Adjustment check on the account the user picked:
     - **Proven covered**: only when the picked account is the one the log's mask lookup (above) found and the log time is valid; run `isCoveredByAdjustment(accountId, log.postTime, rows)` exactly as settlement does. Covered → return `CoveredByAdjustment`, write nothing. The log stays in the inbox; the sheet explains that the adjustment already includes it and offers Dismiss.
     - **Possibly covered**: otherwise, with `t = min(log.postTime, stored timestamp)` (stored timestamp only for `invalid_time`), if the picked account has a live `ADJUSTMENT` after `t` → return `NeedsAdjustmentConfirmation(token)`, write nothing. `token = (accountId, t, newestAdjustmentTimestamp)`.
   - Else insert the transaction through the ledger write path (N1) and move every log of the item `FAILED → CONFIRMED` with `linkedTransactionId`, guarded by status and reason, in the same write; a guard miss rolls back and returns `AlreadyHandled`.
2. On `NeedsAdjustmentConfirmation`, the sheet asks: "A balance adjustment after this time may already include this. Saving will change the account's current balance by this amount." Confirm → the same use case with `confirmation = token`. Inside the write, the proven check runs again, and a token whose account or `t` differs from the current input, or a newer adjustment than the token's, returns `NeedsAdjustmentConfirmation` again.

The booked transaction: `source = MANUAL` (the user entered it), `notificationLogId = log.id`, `postBalance = null`. Null post balance is deliberate: drift detection and balance anchors read `Transaction.postBalance`, so a booked item never becomes a bank-balance anchor, whatever account, amount or date the user picked. Checked in code: transfer pairing only pairs notification-sourced rows; nothing assumes `MANUAL ⇒ notificationLogId == null`; deleting the transaction later is a soft delete and leaves the log `CONFIRMED` (it does not return to the inbox).

**Dismiss** ("already entered" / "not a transaction"): one write, every log of the item keeps status `FAILED` and gets reason `user_dismissed`, guarded by status and reason; a guard miss is `AlreadyHandled`. No keyword is learned. It stays `FAILED` on purpose: `getSettledLogsSince` excludes `IGNORED_SPAM`, so a dismissed log turned spam would stop being a repost original, and a repost inside the window would be booked next to the user's own entry. `user_dismissed` is outside the inbox set and the re-parse set.

Note: `getLastManualAccountId` will also return accounts of inbox bookings (`source = MANUAL`). Accepted: it only feeds the plain cash entry default.

### 5. Telling the user

- **Banner** on the transaction list, above the list, same style as the drift banner: "N transactions could not be recorded · Review", N = shown items. Shown while there are shown items. A close button hides it until a shown item appears that was not there at dismissal.
- **Red dot** on the Settings tab icon and on the "Failed notifications" row in Settings, when a shown item exists that was not there the last time the inbox was opened. Opening the inbox clears it.
- Markers are **sets of item ids** (the bank-side log id), not timestamps: a settlement failure turns `FAILED` long after its `receivedAt`, so a time marker could miss it. Two local preference string sets (`seenIds`, `bannerDismissedIds`), stored like `drift_ack.xml`; ids only. Each write keeps only ids still in the inbox, and always writes a new set (never mutates the one `getStringSet` returned).
- The inbox row in Settings shows in every build (the debug log row stays debug and dogfood only).

### 6. Debt N-07

`isActive = false` rules still filter, and a package whose rules are all inactive is still captured, so it could fill the inbox with `no_rule_matched`. Inactive rules come only from the OTA feed (nothing local sets them), for example while a broken rule is being replaced. Making such a package `NotTracked` at capture would drop every notification of that window for good (nothing stored, nothing to re-parse once the fixed rule arrives). So capture stays as it is, and the inbox hides logs of a package with no active rule at read time (§1); they come back through re-parse when a rule is active again. N-07 is closed by this read-time rule.

### 7. Roadmap

This branch also updates `docs/roadmap.md`: step 13 scope as above; a new beta step for the Regex Sandbox after step 13; N-07 removed from the debt ledger; a new small debt for wallet pairs failed before §1.1.

## Constraints

- Money is `Long` VND. Booking and the log status change are one transaction; every status change is guarded by `status = 'FAILED'`.
- No notification text, amounts, balances, masks or keywords in Logcat; new entities and models override `toString` like `NotificationLogEntity`. `Classification` and `CaptureResult.Ignored` now carry a user keyword in their reason, so they override `toString` too.
- One real migration (10 → 11) with a test; never destructive.
- Layers and names per `docs/architecture.md` and the Konsist rules; UI per `docs/design-system.md` (dialogs and sheets in their own files under the feature's `component/`).
- Strings in all four locales (`vi`, `en`, `zh-rCN`, `ja`).
- Fixtures use made-up values only.

## Left to the builder

- Exact regexes of the three signals and `extractVndAmount`, within the rules and test oracle above.
- Keyword suggestion cleanup details (which emoji and punctuation ranges are stripped).
- Layout of the inbox item, the hidden section, the original-text block and the keyword list, within the design system.
- Where the badge capability goes in `FinoteNavBar` (a design-system change, documented in `docs/design-system.md`).
- Names of use cases, results, DAOs and the nav key.

## Calls made without the user (check these)

- `adjusted_after` and `partner_failed` logs are never shown.
- Keyword refusals: under 3 characters, the bank name, any stored real-money log, any other inbox item that looks like money.
- A deletable keyword list in capture settings, with Undo after "this is an ad"; deleting restores what the keyword hid.
- Booked items: `source = MANUAL`, `postBalance = null`.
- Account is never guessed from the last manual entry; empty means the user must pick.
- Dismiss without learning a keyword exists next to "this is an ad".
- N-07 is closed at inbox read time, not at capture (capture would lose notifications for good).
- Dismissed logs stay `FAILED` with `user_dismissed`; stuck reposts and stuck ignores get their own reasons.

## Review log

Builder check (v1): pairs, adjustment check, keyword order, hidden-item actions, timestamp markers. All answered in v2.

External critique (Gemini, v1). Taken:
- Auto-marking `adjusted_after` on the picked account was wrong when the account was not the log's own; v2 proves coverage only on the resolved account and never removes the item by itself.
- Two-phase confirm; check against `min(postTime, chosen date)`; warning text says the balance will change.
- Drift anchors: answered by `postBalance = null` on booked items.
- No last-manual-account fallback; sign must be attached to an amount; decimals and fee-before-amount handled in `extractVndAmount`; original text shown in the sheet; two label families.
- Pair lifecycle: solved by `partner_failed`, so no read-time pairing and no double guard.
- Keyword refusal also checks other money-looking inbox items; normalized unique key; Undo and a delete list.
- `looksLikeMoney` false negatives (no mask, no sign): signal 2 no longer needs a sign, signal 3 is a word anywhere, not "next to a number"; mask needs two mask characters and rejects `%` and `K` suffixes.
- `invalid_amount` amount fallback, Dismiss guard, N-07 re-parse behaviour, counts by item, string-set copy.

Not taken: none.

Architect risk review (v2, effort high, against the code). All taken in v3:
- H1 account prefill: plain identifier lookup on the last 4 digits, any kind; no `resolveAccount`, no lone-account fallback.
- H2 same `sbn.key` booked through a later parseable version: hidden at read time and refused in the write. Residual: a log booked from the inbox whose later version parses only after an OTA update can still book again (the booked log has no parsed fields, so it is never a key root).
- H3 exact `postTime` as the stored timestamp, not `timestampOf`.
- M1 N-07 moved from capture to inbox read time.
- M2 two-reason `fail()` in one write.
- M3 re-parse also covers keyword-hidden logs; Restore in "Hidden".
- M4 identical-text copies are one item.
- M5 confirm token carries account and `t`; proven check uses `log.postTime` and runs again in phase 2.
- Low: `invalid_time` date, Dismiss stays `FAILED`, stuck fallbacks get own reasons, whitespace collapse and "must match the selected log", `toString` of `Classification` and `CaptureResult.Ignored`.

Plan must remember (from the same review): tests that change (`SettlePendingLogsUseCaseTest` L204–217 and L250–268, `FakeNotificationSettlementRepository.fail`, `NotificationSettlementRepositoryImplTest` L482–487); new guarded queries for book, dismiss and restore (status plus reason); the keyword refusal check loads every log of the package; migration 10→11 adds a table and index only (`FINOTE_MIGRATIONS`, schema 11 export).
