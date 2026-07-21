# Security and final audit checklist

The codebase is organized around these invariants:

- no negative, zero, non-finite, over-scale, or over-limit transaction value;
- no order from air or prohibited/oversized metadata;
- no user-controlled MiniMessage parsing;
- no user SQL concatenation;
- no synchronous SQL in listeners or inventory callbacks;
- no title-only GUI identity;
- no duplicate confirmation event within one GUI session;
- no global marketplace lock;
- no order mutation without local guard plus SQL version/status predicate;
- no duplicate ledger idempotency key;
- no oversold remaining quantity or negative reserve;
- no cancellation refund for already committed sales;
- no collection completion without matching claim token;
- no excess item deletion when inventory space is insufficient;
- no blind retry of ambiguous Vault mutation;
- no unbounded cache or executor queue;
- no background scan of every online player or entire order table;
- no database password or full serialized item logging;
- executor and pool receive bounded shutdown;
- incomplete operations remain represented by ledger/delivery state.

Automated tests cover monetary rules, fingerprints and mode decisions, per-order locking, partial/full fulfillment invariants, duplicate idempotency, stale versions, concurrent sellers, unused escrow refund, collection claim idempotency, duration parsing, and both migration scripts.
