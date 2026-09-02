# Personal Finance Tracker — Android App
## Project Requirements & Build Plan

**Author's background:** Python/AI engineer, first Android project, learning native Android dev intentionally (not just shipping fastest).
**Goal:** Lightweight, fully offline expense tracker with categories, trips, a dashboard, and manual backup/restore.

---

## 1. App Overview

A local-first Android app for logging personal expenses. No internet connection required, no backend, no accounts. All data lives on-device in a local database. The user can categorize expenses, tag them to trips, view sortable history, see a dashboard summary, and export/import a backup file manually.

---

## 2. Feature List

### Core (v1)
- **Add Expense**: amount, date, category, optional trip tag, optional note.
- **Add Income (cash inflow)**: amount, date, source (e.g. "Salary", "Freelance"), optional note — tracked separately from expenses so the dashboard can show net cash flow, not just spend.
- **Categories**: predefined list (Food, Transport, Lodging, Shopping, etc.) + ability to add custom categories.
- **Trips**: user-created "trip" tags (e.g. "Japan 2026") that expenses can be assigned to, for grouping travel spend separately from daily spend.
- **Recurring transactions**: mark an expense or income entry as recurring (daily/weekly/monthly), so it auto-generates on schedule instead of being re-entered manually (e.g. rent, subscriptions, salary).
- **Budgets with alerts**: set a monthly budget per category (or an overall monthly budget), track spend against it, and get a local notification when spend crosses a threshold (e.g. 80%/100% of budget).
- **History view**: full list of expenses and income, sortable by date / amount / category / trip, with filters (e.g. show only "Japan 2026", only "Food", or income vs. expense).
- **Dashboard**: summary view — total spend this month, total income, net cash flow, spend by category (chart), spend by trip, budget progress bars per category, simple trend line over time.
- **Backup/Export**: export all data (expenses, income, categories, trips, budgets, recurring rules) to a single JSON file via Android's share sheet, so the user can save it to Drive, email it to themselves, move it via USB, etc.
- **Restore/Import**: load a previously exported file back into the app (e.g. after reinstalling, or on a new device).

### Nice-to-have (later, not v1)
- Multiple currencies / simple FX conversion.
- Home screen widget showing monthly total or budget status.
- Dark mode / theming polish beyond default Material You.
- Budget rollover (unused budget carries to next month).

---

## 3. Tech Stack

| Layer | Choice | Notes |
|---|---|---|
| Language | Kotlin | Native Android dev, chosen intentionally for learning |
| UI | Jetpack Compose | Declarative UI, current Google-recommended approach |
| Local DB | Room (SQLite wrapper) | Entities: Expense, Category, Trip |
| Async | Kotlin Coroutines + Flow | Room DAOs expose Flow for reactive UI updates |
| Navigation | Navigation Compose | Screens: Add Expense, History, Dashboard, Settings/Backup |
| State mgmt | ViewModel + StateFlow | One ViewModel per screen/feature area |
| Charts | Vico (Compose-native) or MPAndroidChart | Vico is more idiomatic for Compose |
| Design system | Material Design 3 (Material You) | Built into Compose Material3 library |
| Export format | JSON (preferred) or CSV | JSON preserves structure/relations better |
| Scheduling | WorkManager | Generates recurring transactions on schedule, checks budget thresholds periodically, survives app restarts/reboots |
| Alerts | NotificationCompat / Notification Channels | Local (on-device) notifications for budget threshold alerts — no server/push needed |

---

## 4. Data Model (Room Entities)

```
Category
- id: Long (PK)
- name: String
- colorHex: String? (for dashboard chart coloring)

Trip
- id: Long (PK)
- name: String
- startDate: LocalDate?
- endDate: LocalDate?

Expense
- id: Long (PK)
- amount: Double
- date: LocalDate
- categoryId: Long (FK -> Category)
- tripId: Long? (FK -> Trip, nullable — not all expenses belong to a trip)
- note: String?
- recurringRuleId: Long? (FK -> RecurringRule, nullable — set if this row was auto-generated)

Income
- id: Long (PK)
- amount: Double
- date: LocalDate
- source: String (e.g. "Salary", "Freelance")
- note: String?
- recurringRuleId: Long? (FK -> RecurringRule, nullable)

RecurringRule
- id: Long (PK)
- type: Enum (EXPENSE, INCOME)
- amount: Double
- categoryId: Long? (used when type = EXPENSE)
- source: String? (used when type = INCOME)
- tripId: Long?
- frequency: Enum (DAILY, WEEKLY, MONTHLY)
- startDate: LocalDate
- nextDueDate: LocalDate
- note: String?
- isActive: Boolean (lets the user pause a recurring rule without deleting history)

Budget
- id: Long (PK)
- categoryId: Long? (null = overall monthly budget, not category-specific)
- monthlyLimit: Double
- alertThresholdPercent: Int (e.g. 80 — triggers a notification when spend crosses this % of limit)
```

DAO queries to plan for: insert/update/delete for each entity; get all expenses/income (Flow); filter by category/trip/date range/type; sum grouped by category; sum grouped by trip; monthly totals for expenses, income, and net; current spend vs. budget limit per category (for progress bars + alert checks); due recurring rules (`nextDueDate <= today AND isActive = true`) for the WorkManager job to process.

---

## 5. Environment Setup (steps to follow on a new/different system)

1. **Install Android Studio** (latest stable release) — includes Android SDK, platform tools, and the AVD (emulator) manager.
2. **Install a JDK** if not bundled (Android Studio ships its own JBR, usually no separate install needed — verify in Settings → Build Tools → Gradle).
3. **Create an AVD (emulator)**: Device Manager → Create Device → pick a recent Pixel profile → system image API 34 or 35.
4. **Enable USB debugging on your physical phone** (optional but recommended): Settings → About Phone → tap Build Number 7x → Developer Options → enable USB debugging → connect via USB and authorize the computer.
5. **Install Git** and initialize a repo for the project (for version control across systems).
6. **Install Claude Code** (CLI): follow the standard install instructions for your OS.
7. **Install the Claude Code JetBrains plugin** inside Android Studio (Marketplace → search "Claude Code [Beta]") and restart the IDE fully (may take 2 restarts to activate).
8. **Verify setup**: create a new "Empty Activity (Compose)" project from the Android Studio wizard, run it on the emulator and/or physical device to confirm the toolchain works before writing real app code.
9. **Clone/pull the project repo** on the new system and open it in Android Studio — Gradle will re-sync dependencies automatically.

---

## 6. Suggested Build Order (milestones)

1. Scaffold empty Compose project, confirm it runs on emulator + device.
2. Define core Room entities (`Expense`, `Income`, `Category`, `Trip`) + DAOs; write unit tests for DAO queries (no UI yet).
3. Build "Add Expense" and "Add Income" screens wired to Room.
4. Build "History" screen: list + sort + filter by category/trip/date/type (expense vs. income).
5. Build "Dashboard" screen (v1 pass): totals by category/trip, net cash flow, chart(s).
6. Add `RecurringRule` entity + DAO; build "Add Recurring Rule" UI (attach to an expense or income entry); implement a WorkManager job that generates due transactions daily and advances `nextDueDate`.
7. Add `Budget` entity + DAO; build "Set Budget" UI per category (and optionally overall); add budget progress bars to the Dashboard; implement threshold-check logic (in the same or a separate WorkManager job) that fires a local notification when a budget crosses its alert threshold.
8. Add Export (JSON via share sheet, covering all entities) and Import (file picker → parse → insert into Room).
9. Polish: app icon, consistent theming, empty states, basic onboarding/help text.
10. (Optional) add nice-to-have features from Section 2.

---

## 7. UI/UX Guidelines & Recommended Skills for the Agent

Since the app should look polished, not just "functional Compose defaults," give Claude Code explicit design guidance rather than letting it default to bare Material components:

- **Follow Material Design 3 (Material You)** as the base design language — it's what Compose's `Material3` library is built around, so working with it (not against it) gives the best results with the least custom code. Reference: Google's official Material 3 design guidelines and the `androidx.compose.material3` component docs.
- **Use a defined type scale and spacing scale** from the start (e.g. Material3's built-in `Typography` and a consistent 4dp/8dp spacing grid) rather than ad-hoc padding values scattered through the code — this alone makes an app feel much more "designed."
- **Dynamic color / theming**: Compose Material3 supports dynamic color (Material You theming based on the user's wallpaper on Android 12+). Worth enabling for a modern feel, with a sensible static fallback theme for older devices.
- **Meaningful empty/error states**: a finance app with no data yet or a failed import shouldn't just show a blank screen — design simple, friendly empty states.
- **Category color coding**: since categories drive the dashboard charts, pick a consistent, accessible color palette per category up front (this is a small design decision that pays off a lot visually).

**On "skills" for the agent specifically:**
This chat environment includes a built-in `frontend-design` skill, but it's tuned for web/React UI (Tailwind, HTML/CSS) — it won't directly transfer to Kotlin/Compose. Claude Code supports its own custom skills (instruction folders it reads before acting), so the more effective move is to **create a small custom skill for this project** using Claude Code's `skill-creator`-style workflow: write a short `SKILL.md` that encodes your project's Material 3 conventions (type scale, spacing grid, category color palette, component choices) so every screen Claude Code generates stays visually consistent, instead of re-explaining design preferences in every prompt. If your Claude Code setup doesn't have an equivalent skill-creation flow, the fallback is to keep a `DESIGN.md` in the repo root and reference it explicitly in prompts (e.g. "follow DESIGN.md conventions") — same effect, just manual.

---

## 8. Backup Strategy (detail)

- **Export**: serialize all `Expense`, `Income`, `Category`, `Trip`, `Budget`, and `RecurringRule` rows to a single JSON file with a version field (e.g. `{"version": 1, "categories": [...], "trips": [...], "expenses": [...], "income": [...], "budgets": [...], "recurringRules": [...]}`), then trigger Android's share intent so the user can save it anywhere (Drive, email, local storage, USB transfer).
- **Import**: use Android's file picker (`ACTION_OPEN_DOCUMENT`), parse the JSON, validate the version field, and upsert into Room — wrap in a transaction so a bad file can't half-corrupt the DB.
- Keep the export format stable/versioned from the start, so future schema changes don't break old backups.

---

## 9. Testing Strategy

- **Unit tests** (JUnit) for Room DAOs and derived calculations (e.g. "sum expenses by category," "sum by trip") — fast, no emulator needed.
- **Manual testing** on emulator + physical device for UI flows, especially the export/import share-sheet behavior (file pickers behave slightly differently across devices/Android versions).
- Skip automated Compose UI tests for v1 — revisit if the app grows significantly.

---

## 10. Reference Links

- Jetpack Compose docs: https://developer.android.com/jetpack/compose
- Room docs: https://developer.android.com/training/data-storage/room
- Material Design 3: https://m3.material.io
- Navigation Compose: https://developer.android.com/jetpack/compose/navigation
- Vico charts: https://patrykandpatrick.com/vico/
- WorkManager (recurring/background work): https://developer.android.com/topic/libraries/architecture/workmanager
- Notifications: https://developer.android.com/develop/ui/views/notifications
- Claude Code JetBrains plugin docs: https://code.claude.com/docs/en/jetbrains
