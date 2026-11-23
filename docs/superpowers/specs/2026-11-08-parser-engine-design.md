# Parser engine design (P2b-1, `feature/parser-engine`)

Spec module 4 (L41–43), tables 3.7 and 3.8 (L166–207), acceptance 6 (L407).

## Scope

P2b is split into three branches, in this order:

1. `feature/parser-engine` (this document): regex rules, parser, corpus gate.
2. `feature/failed-inbox`: screen that lists `FAILED` rows and turns one into a manual transaction.
3. `feature/rule-ota`: WorkManager downloads rule JSON; never overwrites `isUserCustom = true`; re-parses `FAILED` rows.

In this branch:

- Regex rules for Vietcombank, MB Bank, MoMo and Techcombank.
- Parser in `domain/notification`, no `android.*`.
- Capture parses right after the filter and stores one row with its final status.
- P2a debt #1 (seeder reads before its transaction), #2 (bad rule JSON drops a whole package), and ignore keywords split per bank.

Out of scope: account mapping, dedup, cold-start balance (P2c); transaction creation; Failed Inbox UI; OTA; Regex Sandbox.

## Guiding rule

A wrong value is worse than a miss. A `FAILED` row reaches the user through the Failed Inbox; a wrong amount or sign silently corrupts a balance. When in doubt the parser rejects the match and tries the next rule, and finally gives `FAILED`.

## Result of a parse

- Match: status stays `PENDING`. Fill `matchedRuleId`, `parsedAmount`, `parsedType`, `parsedAccountMask`, `parsedPostBalance`, `parsedMerchant`, `parsedNote`. P2c reads these rows and creates transactions.
- No rule matches: status `FAILED`, `failureReason = "no_rule_matched"`, parsed fields null.
- Filtered rows (`IGNORED_SPAM`) are never parsed.

## Data

Rule storage follows spec 3.8: one `parser_rules` row is one regex. A bank has several rows (`vcb_credit_v1`, `vcb_debit_v1`, …). Rejected options: a separate filter row next to regex rows (two kinds of row in one table), and separate filter and regex tables (drifts from the spec, harder OTA).

`parser_rules` gains:

| Column | Type | Note |
|---|---|---|
| `regexPattern` | `String` | Named groups `sign`, `amount`, `mask`, `balance`, `merchant`, `note`, `time`. Flags are inline in the pattern (`(?s)`, `(?i)`); the parser adds none |
| `fallbackTransactionType` | `String?` | `EXPENSE` or `INCOME`, used when the pattern has no `sign` group or the group did not take part in the match |
| `dateFormatPattern` | `String?` | Stored for OTA and the sandbox. Not used yet: the transaction time is `postTime` |
| `isActive` | `Boolean` | An inactive rule is not used to parse. It still filters (below) |
| `updatedAt` | `Long` | `0` for system rules (a constant keeps reseeding stable); OTA and user edits set the edit time |

`notification_logs` gains `parsedNote: String?`. Not in spec 3.7; P2c needs it to guess the category. Corpus labels `availableCredit`, `reference` and the `time` group are not stored: nothing reads them yet.

The four `*_filter_v1` rows go away. Every regex row of a bank carries that bank's ignore keywords and channel allowlist. Filtering keeps the P2a logic over all rules of the package, active or not: union of keywords, and a channel filter only when every rule has an allowlist. So turning rules off never lets spam through, and a user rule with an older keyword list cannot drop a keyword that another rule carries.

Domain `ParserRule` gains the fields above. `fallbackTransactionType` is `TransactionType?` (the existing enum in `domain/transaction/model`), limited to `INCOME` and `EXPENSE`.

Database version 4 → 5. The factory uses `fallbackToDestructiveMigration` (no release yet), so there is no migration code. The seeder writes the new rules on create.

### Seeder (debt #1)

`onOpen` reads the stored versions first. When nothing is outdated it returns without a write lock, as today. Otherwise it opens `BEGIN IMMEDIATE TRANSACTION`, reads the stored versions again, and writes only the rules that are still outdated. A write between the first read and the transaction can no longer overwrite a rule that just became `isUserCustom`. The upsert SQL gains the new columns.

### Mapper (debt #2)

`ParserRuleEntity.toDomain()` returns `ParserRule?`. Bad JSON in `channelIdAllowlist` or `ignoreKeywords`, or a `fallbackTransactionType` other than `INCOME` or `EXPENSE` (exact case), gives `null`, and the repository drops that rule. The other rules of the package still work. A regex that does not compile is handled in the parser (below), not in the mapper, so a bad pattern still filters.

### Per-bank keywords

`SystemParserRule.kt` holds one keyword list per bank, built from that bank's ignored corpus samples. Phrases that apply to all banks stay in one shared list that each bank list includes. `SystemParserRulesCorpusTest` stays the judge.

### Seed rule style

Prefer one regex per message shape with optional groups (`(?:…)?`) for the parts that only the expanded text has. Do not add a looser "collapsed" rule with a lower priority: an expanded message that just misses the full rule would fall through to it and lose its balance and note without a trace. The corpus gate checks expanded texts field by field, so such a fallthrough fails the build.

## Parser

`domain/notification/NotificationParser.kt`:

```kotlin
sealed interface ParseResult {
    data class Parsed(val ruleId: String, val fields: ParsedFields) : ParseResult
    data object NoMatch : ParseResult
}

fun parseNotification(title: String, content: String, rules: List<ParserRule>): ParseResult
fun parseMoney(raw: String): Long?
```

`ParsedFields`: `amount: Long`, `type: TransactionType` (`INCOME` or `EXPENSE`), `accountMask`, `postBalance`, `merchant`, `note`, all but the first two nullable.

Steps:

1. Text is `title + "\n" + content`. Content is already the expanded text when Android gave one. Some apps (MoMo) put the direction only in the title.
2. Active rules, sorted by `priority` descending, then `id` for a stable order.
3. For each rule, compile the pattern (`runCatching`; a pattern that does not compile is skipped) and run `Regex.find`, not a full match. No cache: a few notifications a minute, a handful of rules each.
4. Group names come from the pattern once per rule (`\(\?<([a-zA-Z][a-zA-Z0-9]*)>`). A field whose group is not in that set is null; no exception path.
5. Type: `sign` trimmed. `+` → `INCOME`, `-` → `EXPENSE`. A `sign` group that took part in the match with any other value rejects the rule. A missing or non-participating `sign` group uses `fallbackTransactionType`; none → reject.
6. Amount: `parseMoney`, then it must be above 0, else reject.
7. First rule not rejected wins. None → `NoMatch`.

`parseMoney`:

- Keep only digits, `.` and `,`. Any other character (spaces of every kind, `VND`, `VNĐ`, `đ`) goes.
- If the string ends with a separator and exactly two digits (`1,500,000.00`), drop that tail: VND groups are three digits, so a two-digit tail is a decimal part.
- Drop the remaining separators, then `toLongOrNull()`. Empty, or more than fits a `Long`, gives null.
- Zero is a valid result. Only the amount check in step 6 requires a value above 0, so a `0 VND` balance is stored as `0`.
- A minus sign is dropped with the other characters; sign comes from the `sign` group only. A negative balance (credit card) is out of scope.

A `balance` group that fails `parseMoney` gives a null balance; the match still counts. `mask`, `merchant` and `note` are trimmed. Blank becomes null.

## Capture

`CaptureNotificationUseCase` keeps its order: rules, hash, secret check, filter (all rules of the package). When the row passes the filter it calls `parseNotification` with the active rules and builds the log with `PENDING` and parsed fields, or `FAILED` with `no_rule_matched`. `CaptureResult` gains `Failed`. `NotificationLog` gains the parsed fields.

## Tests

`NotificationParserTest`:

- `parseMoney`: `1.500.000`, `1,500,000`, `1 500 000 VND`, `1 500 000 VND`, `50.000đ`, `1,500,000.00 VND` → 1 500 000 or 50 000; `0 VND` and `0đ` → 0; junk and a 20-digit string → null.
- A balance of `0 VND` gives `postBalance = 0`.
- Priority order; equal priority ordered by id.
- Fallback type; a rule with a `sign` group that matched something other than `+` or `-` is rejected even with a fallback.
- A rule whose amount is zero falls through to the next rule.
- A pattern that does not compile is skipped; the next rule still runs.
- A pattern without a `mask` group gives a null mask.
- Multi-line content: amount, balance and note on separate lines, pattern with `(?s)`.

Other tests:

- `ParserRuleMapperTest`: bad JSON → null; lowercase or unknown `fallbackTransactionType` → null.
- `ParserRuleSeederTest`: new rule set, user rule kept, upgrade only for lower versions, nothing outdated → no write.
- `CaptureNotificationUseCaseTest`: pass + match → `PENDING` with fields; pass + no match → `FAILED`; blocked → not parsed; every rule of the package inactive → spam is still `IGNORED_SPAM` and a real transaction is `FAILED`.
- Every ignored corpus item still ends as `IGNORED_SPAM`, never parsed.

Corpus gate `NotificationParserCorpusTest`, run on the collapsed and on the expanded text of each financial item:

- VCB, MB, MoMo (spec 6): every item parses, and each labelled field (`sign`, `amount`, `accountMask`, `postBalance`, `merchant`, `note`) equals its label. Spec 6 asks for 98%; with 26 such items that means zero misses, so the gate is 100%. Collapsed text may lack the balance or note: there `sign` and `amount` must match, other fields must equal the label or be null.
- Techcombank (best effort): a parsed item must be right by the same field check, so a wrong value fails the build. An item that ends `FAILED` does not. The parse rate is recorded in the commit body and the roadmap, not printed by the test.
