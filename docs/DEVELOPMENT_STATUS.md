# Development Status

## Project
Ferie & Permessi Native — native Android migration of the existing PWA in this repository.

## Baselines
- Functional source: `main:index.html`; existing PWA remains untouched.
- Android architecture/delivery source: `fax0662294942-beep/android-app-factory@develop`.
- Native work branch: `android-native`.

## Product invariants discovered from the PWA
- Vacation and paid-leave accrual, usage, balances and previous-year residuals.
- Initial balances and contract/start-date configuration.
- Vacation ranges and hourly leave entries.
- Leave liquidation.
- Time bank.
- Law 104 and study leave with annual budgets.
- Workday/holiday configuration.
- Month simulation and confirmation.
- Calendar, statistics, tags and multiple users.
- Existing Google/Firebase authentication, approval roles and Firestore sync are requirements to preserve, not silently remove.
- Existing data must not be destroyed during migration.

## Android invariants
- Kotlin + Jetpack Compose + Material 3.
- Room with explicit migrations and checked-in schemas.
- Versioned JSON backup/restore through SAF.
- Back navigates screens/modals to Home; on Home two Back presses within about two seconds exit.
- Permanent regression tests for stable business rules.
- GitHub Actions is the authoritative build/device-test gate.

## Roadmap
- [x] B0 — inspect PWA and Factory; establish native branch and migration contract.
- [x] B1 — transplant Factory Android skeleton and CI, rename application/package, establish green baseline.
- [x] B2 — native domain model + Room schema for users/configuration/entries/tags/special leave/time bank.
- [ ] B3 — port deterministic accrual/calendar/business-rule engine with unit tests.
- [ ] B4 — Home/month summary + entry CRUD.
- [ ] B5 — calendar, simulation, liquidation, special leave and time bank.
- [ ] B6 — statistics, settings, tags and multi-user flows.
- [ ] B7 — legacy PWA JSON import + native backup/restore regression coverage.
- [ ] B8 — Firebase/Google authentication and Firestore sync compatibility.
- [ ] B9 — candidate hardening, emulator regression suite and APK candidate.

## Human checkpoints
Stop only for genuine product ambiguity, destructive real-data migration, credentials/secrets, subjective UI acceptance, unavoidable physical-device validation, or final production release.

## Verification 2026-09-30
- Latest upstream commit: fd4c97024e6ed6f0987281b1d60078db580bd64b.
- Action #2 succeeded (build, lint, JVM tests); B1 emulator coverage is now added and awaiting the new gate.
- B2 implementation: independent native Room database, users, yearly configuration, all seven legacy entry types, tags/links, holidays and app state. Negative leave balances and per-user legacy IDs preserved.
- Room exportSchema enabled; generated schema must be checked in after the authoritative build. No destructive migration fallback.
- PWA baseline inspected directly at main:index.html; no legacy files changed.
- B1/B2 closure pending build + emulator gate and exported schema verification.

## B1/B2 closed
- Action #4 (run 36753384138), commit 89a9dc1da185680871fc0408d53faf838ba6dfb0: build/lint/JVM and API 35 emulator all green.
- Exported Room v1 schema checked in. Room test covers all entry kinds, negative initial leave, per-user identity isolation and cascading tag links.
- Compose regression verifies back from Calendar to Home, first Back hint, second Back exit. The test waits for recomposition before dispatching Back.

## B3 awaiting gate
- Injected-date deterministic engine ports PWA monthly allocation, 15-day gate, 24/48-month CCNL thresholds, FIFO, carry-forward (leave debt retained), liquidation, initial-date filtering, simulation, special leave, time bank, workdays and holidays.
- Differential expected values generated read-only by tools/pwa-oracle.cjs against main:index.html.
- Eight business-rule unit tests added, including the original PWA oracle fixture.
