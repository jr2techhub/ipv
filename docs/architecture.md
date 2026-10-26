# Technical Architecture — IPV APK for Cuban Private SMEs

**Scope:** Android APK covering Inventory + Point of Sale (POS) + Electronic Invoicing + Financial Reports.
**Target:** Multi-sector private businesses in Cuba (restaurants, retail shops, services).
**Core constraint:** 100% offline-first. The device may stay disconnected for **weeks or months**; the app must be fully functional offline and synchronize opportunistically when connectivity appears.

---

## 1. Architectural Principles

1. **Local-first, cloud-optional.** The SQLite database on the device is the *single source of truth* for daily operations. The server is a synchronization and aggregation point, never a dependency for selling.
2. **Every business action is an event.** Stock is never "set" to an absolute value from the UI; it changes through append-only *movement records* (sale, purchase, adjustment, return). This makes multi-device merge mathematically safe.
3. **Idempotency everywhere.** Every record carries a globally unique ID generated on-device. Re-syncing the same record twice has zero side effects.
4. **Graceful degradation.** No feature may fail because the network is absent. Features that need the server (cloud backup, remote reports, license revalidation) queue silently and complete later.
5. **Low-end hardware friendly.** Must run on modest Android devices (Android 8.0+, 2 GB RAM), with small APK size (< 40 MB) and low battery/storage footprint.

---

## 2. Technology Stack (Recommended)

| Layer | Choice | Rationale |
|---|---|---|
| Language | **Kotlin** | Official Android language, null-safe, maintainable |
| UI | **Jetpack Compose** (Material 3) | Fast iteration; classic XML Views acceptable fallback for very old devices |
| Local DB | **Room (SQLite)** + **SQLCipher** encryption | Mature offline storage; encryption protects business data on shared devices |
| Background work | **WorkManager** | Sync, backups and report exports survive reboots and doze mode |
| Architecture pattern | **Clean Architecture + MVVM**, unidirectional data flow | Testable; isolates sync logic from UI |
| Sync transport | **REST/JSON over HTTPS** with delta payloads (gzip); optional WebSocket for live dashboard | Works over Cuba's intermittent mobile data (ETECSA 3G/4G) |
| Backend (optional phase) | **FastAPI (Python)** or **Node.js/Express** + **PostgreSQL** | Cheap to host, easy to find maintainers locally |
| Hosting | Single small VPS (Docker Compose) or serverless | Low cost; the backend is thin because logic lives on-device |
| Reports/PDF | Local generation (Android PdfDocument / MPAndroidChart for charts) | Reports must work offline |
| Updates | In-app updater against own static file server (delta APKs) | No reliance on Google Play availability |

---

## 3. System Overview

```
┌────────────────────────── ANDROID DEVICE ──────────────────────────┐
│                                                                    │
│  UI Layer (Compose)                                                │
│    POS screen · Inventory · Invoicing · Reports · Settings         │
│         │                                                          │
│  Domain Layer (use cases)                                          │
│    MakeSale · AdjustStock · IssueInvoice · DailyClose · Valuate…   │
│         │                                                          │
│  Data Layer                                                        │
│    Room DB (SQLCipher) ── Repositories ── Outbox (sync queue)      │
│         │                                     │                    │
│    License module (signed offline license)    │                    │
└─────────┼─────────────────────────────────────┼────────────────────┘
          │ local only                          │ HTTPS when available
          ▼                                     ▼
   Full app functionality            ┌──── BACKEND (optional) ────┐
   with zero connectivity            │ Sync API (delta up/down)   │
                                     │ PostgreSQL (aggregated)    │
                                     │ Backup storage             │
                                     │ License issuance service   │
                                     │ Multi-branch dashboard     │
                                     └────────────────────────────┘
```

---

## 4. Local Data Model (Core Entities)

All tables share: `id` (ULID/UUIDv7, generated on-device), `created_at`, `updated_at`, `version` (integer, incremented per change), `deleted` (soft-delete flag), `sync_state` (pending/synced).

**Catalog & Inventory**
- `categories` — id, name, parent_id
- `products` — id, sku, barcode, name, category_id, unit, cost_price, sale_price, tax_rate, min_stock, is_service
- `stock_movements` — id, product_id, type (SALE | PURCHASE | ADJUSTMENT | RETURN | TRANSFER), quantity (signed), unit_cost, reference_id, reason, user_id, occurred_at *(append-only; current stock = SUM of movements)*
- `suppliers` — id, name, tax_id, contact

**POS & Sales**
- `sales` — id, invoice_id (nullable), customer_id, user_id, subtotal, discount, tax_total, total, payment_method, status (OPEN | COMPLETED | VOID), sold_at
- `sale_items` — id, sale_id, product_id, quantity, unit_price, discount, tax
- `payments` — id, sale_id, method (CASH | CARD | TRANSFER | MLC | USD | CRYPTO | CREDIT), amount, currency, reference
- `cash_register_sessions` — id, user_id, opened_at, closed_at, opening_balance, expected_balance, counted_balance, difference

**Invoicing**
- `invoices` — id, folio (local sequential per device+business), customer_id, sale_id, issue_date, status (DRAFT | ISSUED | SENT_TO_AUTHORITY | REJECTED), pdf_path, qr_payload, authority_ack (nullable)
- `invoice_items` — mirrors sale_items with tax breakdown
- `customers` — id, name, tax_id (NIT/personal), contact

**Financial Reports (computed views, not stored)**
- Daily close (cierre de caja), sales by product/category/period, inventory valuation (weighted average cost), receivables, simplified P&L, tax summary.

**System**
- `users` — id, name, role (ADMIN | MANAGER | CASHIER), pin_hash
- `audit_log` — id, user_id, action, entity, entity_id, before/after snapshot, at *(append-only)*
- `outbox` — id, entity, entity_id, operation, payload_json, attempts, last_error, queued_at
- `settings` — business profile, currency display, tax config, license data

---

## 5. Offline-First Sync Engine (the critical subsystem)

### 5.1 Outbox pattern
Every create/update/delete writes to the local table **and** appends a record to `outbox` in the same SQLite transaction. A WorkManager job drains the outbox whenever connectivity is detected (ConnectivityManager callback + periodic retry with exponential backoff).

### 5.2 Delta protocol
- **Upload:** `POST /sync/push` with batched changed records (max ~200 per request, gzip). Server applies idempotent upserts keyed by `id` + `version`.
- **Download:** `GET /sync/pull?since=<last_sync_cursor>` returns records changed on the server since the device's cursor (other branches, cloud edits, license updates). Cursor is a hybrid logical clock, not a wall-clock timestamp — safe against device clock changes.

### 5.3 Conflict resolution
- **Append-only entities** (stock_movements, sales, payments, audit_log): *no conflicts possible* — both sides merge by union of IDs. Stock quantities converge because they are derived from movements.
- **Mutable entities** (products, customers, settings): **last-writer-wins by version + updated_at**, with one business exception: `sale_price`/`cost_price` changes produce an audit entry so the owner can review.
- **Invoice folios:** sequence is scoped per device (`folio = device_prefix + counter`) to avoid collisions between branches that sync after months apart.

### 5.4 Long-disconnection tolerance (explicit requirement)
- No server-side TTL on pending records; a device offline for 6 months syncs completely on reconnect.
- Sync is **resumable**: cursor + outbox state persist across app restarts and partial transfers.
- Payloads are batched and compressed to survive expensive, low-bandwidth mobile data sessions.
- The UI shows a clear "pending sync: N records" badge — never blocks the cashier.
- **Warning surfaced to the user** (per requirement): when the app has been offline beyond a configurable threshold (default 7 days), a persistent banner reminds the owner that data is not yet backed up and recommends syncing soon to reduce data-loss risk if the device is lost/damaged.

### 5.5 Backup without a server
Local encrypted export (SQLCipher backup file + JSON) to SD card / USB OTG / shared via any file channel. This protects the business even if the owner never buys backend hosting.

---

## 6. Electronic Invoicing Module

Reality check: Cuba does not yet have a mass public e-invoicing API for MIPYMES/TCPs; requirements evolve (ONAT). The architecture isolates this behind an **adapter layer**:

```
InvoiceService (domain)
   ├── LocalInvoiceAdapter   → generates numbered PDF + QR (business data, tax breakdown, folio) — works offline, always available
   ├── OnatAdapter (future)  → implements whatever schema ONAT publishes; submits queued invoices when online, stores acknowledgment
   └── CustomAdapter         → per-client integrations (e.g., state-owned partners)
```

- Invoices are **issued locally first** (legal folio, PDF, QR) and queued in `outbox` for authority submission when/if a connector exists.
- Tax configuration (rates, exemptions) is data-driven in `settings`, not hardcoded — survives regulatory changes without app updates.

---

## 7. POS Flow (offline, hot path)

1. Cashier scans/selects products → cart held in memory + persisted as OPEN sale (crash-safe).
2. Payment recorded (multi-method split payments supported: CUP cash + MLC card, etc.).
3. Sale COMPLETED in one transaction: insert `sale`, `sale_items`, `payments`, `stock_movements`, outbox entries.
4. Optional invoice issued (LocalInvoiceAdapter) → PDF/QR.
5. Receipt printed via Bluetooth ESC/POS printer **or** shared digitally (WhatsApp/SMS) — no network dependency for printing.
6. End of shift: `cash_register_sessions` close with counted vs. expected balance and difference report.

Target: sale completion < 500 ms on low-end hardware.

---

## 8. Security & Access Control

- **Encryption at rest:** SQLCipher; key derived from device keystore (Android Keystore).
- **Roles:** ADMIN (everything, incl. refunds/voids/price edits), MANAGER (reports, inventory), CASHIER (POS only). PIN-based fast login per user.
- **Audit log:** every void, refund, price change and manual stock adjustment is recorded with user + before/after values — essential for owner trust in multi-employee shops.
- **Transport:** HTTPS with certificate pinning for the sync API.
- **Backups:** encrypted; restore requires ADMIN PIN.

---

## 9. Licensing (aligned with the commercialization report)

Hybrid freemium + perpetual premium, validated **offline**:
- License = signed file (Ed25519) containing: business name, tier (FREE | PREMIUM), enabled modules, device limit, expiry (for subscriptions) or "perpetual".
- App verifies signature locally — no phone-home needed to operate. Online check only refreshes/extends.
- FREE tier limits (e.g., ≤ 200 products, 1 user, no e-invoicing) enforced in domain layer.
- License issuance via a simple web form / WhatsApp-assisted flow, payment in CUP by transfer — matches the distribution reality documented in the commercialization report.

---

## 10. Distribution & Updates

- **Channel:** direct APK (web landing, WhatsApp/Telegram groups, USB at purchase) — sideload is the norm in Cuba; optionally F-Droid/Aptoide listing.
- **Updates:** in-app updater checks a static JSON manifest when online; downloads delta/full APK; user confirms install. Never auto-installs mid-shift.

---

## 11. Optional Backend (Phase 2+)

Thin service, only needed for multi-branch owners and cloud backup:
- FastAPI + PostgreSQL + Docker Compose on a small VPS (hostable in-country or abroad).
- Endpoints: `/sync/push`, `/sync/pull`, `/licenses`, `/backups`, `/dashboard` (read-only aggregated reports for owners with multiple branches).
- Stateless API; all business logic remains on-device so server downtime never stops sales.

---

## 12. Delivery Roadmap

| Phase | Content | Outcome |
|---|---|---|
| **1. MVP (offline)** | Products, stock movements, POS, cash close, local PDF invoices, roles, audit log, local encrypted backup | Sellable standalone product — no server needed |
| **2. Sync & licensing** | Outbox sync engine, backend, signed licenses, in-app updater | Multi-device/multi-branch, commercial tiers live |
| **3. Reports & e-invoicing adapter** | Full financial report suite (valuation, P&L, tax summary), CSV/PDF export, ONAT adapter when regulation lands | Premium tier differentiator |
| **4. Growth** | Owner cloud dashboard, customer loyalty, supplier ordering | Retention & upsell |

---

## 13. Key Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Device loss with months of unsynced data | Persistent offline-age banner (§5.4), scheduled local backups to SD/USB, cheap sync via mobile data batches |
| ONAT e-invoicing rules change | Adapter pattern + data-driven tax config (§6) |
| Clock drift / manual date changes on device | Hybrid logical clocks for sync cursors; audit log records device time + monotonic sequence |
| Low-end device performance | Room indexes on hot paths, paginated lists, < 500 ms sale target, no heavy frameworks |
| Piracy of premium tier | Signed licenses + device binding; pricing low enough (per commercialization report) that piracy is not worth the hassle |

---

*Document prepared as the technical architecture baseline. Next concrete step: validate the MVP entity list against 2–3 real businesses (restaurant + shop + service) before writing code.*
