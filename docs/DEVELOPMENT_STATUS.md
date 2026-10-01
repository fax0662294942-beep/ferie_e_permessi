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
- [x] B3 — port deterministic accrual/calendar/business-rule engine with unit tests.
- [x] B4 — Home/month summary + entry CRUD.
- [x] B5 — calendar, simulation, liquidation, special leave and time bank.
- [x] B6 — statistics, settings, tags and multi-user flows.
- [x] B7 — legacy PWA JSON import + native backup/restore regression coverage.
- [ ] B8 — Firebase/Google authentication and Firestore sync compatibility (automated gate green; live Google device checkpoint pending).
- [ ] B9 — candidate hardening, emulator regression suite and APK candidate (automated gate green; physical/UI acceptance pending).

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

## B3 closed
- Action #5 (run 36754235034), commit 258415792c55f77eb389b53fd603c9d1344b54b4: unit/lint/APK and emulator green.

## B4–B6 implementation awaiting gate
- Native Room-backed Home and monthly ledger, year FIFO summary, validated entry create/update/delete with confirmation.
- Month selector, official/simulation views and explicit confirmation of individual or monthly simulations.
- Calendar date/range visibility; liquidation, Law 104, study and time-bank entries/counters.
- User selection/add/delete (last user protected), per-year configuration, CCNL seniority settings, initial balances, weekend/holiday rules, custom holidays, editable tags and entry tag filter.
- Statistics: month/year/all/custom-date periods, accrual and consumed delta, compulsory/requested episode breakdown, paid leave and weekday distribution.
- Added repository integration and statistics unit coverage; permanent dialog-first Back regression.
- B4–B6 remain open until build/lint/unit/emulator pass.

## B4–B6 closed
- Action #6 (run 36754874208), commit 2af0561c26896c3cecf9503b45ce785373184265: build/lint/unit/APK and API 35 emulator both succeeded.

## B7 implementation awaiting gate
- Versioned native JSON codec and direct PWA users/anni/entries/tags/festivita import; preserves balances, IDs within profiles, seven entry kinds, simulation and compulsory flags.
- Android SAF create/open documents, bounded file reads and complete validation before atomic Room writes. Import adds independent profiles with remapped IDs and never replaces existing local users.
- Native transactional restore infrastructure and permanent unit/emulator round-trip, malformed-input, identity-isolation and preservation regressions.
- No live user data or PWA files modified.

## B8 preparation / human configuration checkpoint
- Inspected the original PWA Firebase collection names, approval registry, admin authority and sync format. Added a side-effect-free PWA payload adapter and fail-closed access tests.
- Live Android authentication is blocked by missing Android Firebase registration/OAuth configuration and a persistent candidate signing certificate. See docs/FIREBASE_SETUP.md for the concrete setup handoff.
- B8 remains unchecked; no real cloud writes, production release or main-branch changes.

## B7 closed / B8 configuration checkpoint
- Action #9 (run 36774213211), commit 862feac773afa922b8335469ce86d7bdc8f6493d: build, lint, JVM tests, debug APK and API 35 emulator all succeeded.
- B7 native/PWA JSON round trips, non-destructive profile import, atomic replacement, malformed-file rejection and identity isolation passed. SAF restore requires a separate explicit replacement confirmation.
- B8 payload compatibility and approval policy unit tests passed in the same gate. Actual Firebase Auth/Firestore integration remains unimplemented pending Android registration/OAuth configuration and stable signing setup.
- Stopping at the authorized credentials/configuration checkpoint described in AGENTS.md and docs/FIREBASE_SETUP.md. No final production merge/release; main/PWA unchanged.

## B8 implementation awaiting gate — Android configuration received
- User-provided app/google-services.json verified for project viaggi-camper and package it.feriepermessi.nativeapp; web OAuth client present, no Android certificate fingerprint in the supplied file.
- Credential Manager Google sign-in, Firebase Auth session/logout, approval screens, first-admin claim, registry status/role management and explicit-confirmation account deletion.
- PWA-compatible Firestore documents; approved-account listeners, manual reconciliation, recoverable local snapshots before cloud download, debounced local uploads, server transactions checking approval and exact cloud revisions, extension-field preservation.
- Firebase Auth/Firestore emulator integration gate uses demo-feriepermessi and test-only rules; no production rules deployed, no production account data accessed by development tests.
- CI retains the development debug certificate in Actions cache and exports signing-report.txt with the APK; production signing keys remain out of scope. If that cache is lost, the fingerprint must be registered again.
- B8 remains open pending build/lint/JVM/emulator verification and the real Google sign-in checkpoint.

## Updated Firebase configuration / B9 preparation — 2026-10-01
- User supplied the updated Android OAuth registration. Project/package/web client verified; SHA-1 B0:4E:A9:5E:0A:10:C9:51:5E:D0:83:0B:DA:D5:2F:35:2D:CF:EE:FC matches the actual Action #16 signing report.
- Action #16 (36783561566), commit f76fb1ea1544cf1cfb6ec4c8df67f3004ed24932: build/lint/JVM/APK and API 35 Firebase/Room/Compose instrumentation all passed. B8 implementation has passed its automated gate.
- Updated google-services.json integrated. CI now verifies the actual APK signer against registered Android OAuth fingerprints and exports certificate/digest evidence. A rotated unregistered certificate fails the gate.
- Candidate 0.2.0-rc1 (versionCode 2): extended emulator matrix to API 26 and 35, all-screen Back, invalid editor/month dialog dismissal, rejected-account/logout-modal isolation and disk database reopen/backup preservation regressions.
- Updated configuration and B9 hardening still await the new authoritative gate. B8/B9 stay unchecked until that evidence and the required real Google sign-in/device checkpoint.
- No production data, rules, main/PWA, production signing keys or final release touched.

## B8/B9 automated gates green / physical candidate checkpoint — 2026-10-01
- Action #20, run 36804400687, source commit 0997347dcc81996b86a86d69c7f44bc39bce8cee: all three jobs succeeded.
- Build/lint/JVM/APK and actual APK signing-certificate registration gate passed.
- API 26: all 10 instrumentation tests passed. API 35: all 10 instrumentation tests passed. Firebase emulators, Room/backup/persistence and Compose regressions are included.
- New modal tests initially exposed a test precondition: an open keyboard consumes Back before dialog dismissal. Tests now hide the IME explicitly before checking modal/navigation behavior; Espresso core is declared. No regression was removed or skipped.
- APK: 0.2.0-rc1, versionCode 2, artifact FeriePermessiNative-debug (11136817681), including signing-report.txt, apk-certificate.txt and apk-sha256.txt.
- Automated implementation/hardening for B8/B9 is complete. Roadmap checkboxes deliberately remain open until real Google provider/existing-project rules and physical/UI acceptance are verified; emulator-only success is not a claim of real OAuth success.
- Stop at the existing AGENTS.md physical-device/subjective-UI checkpoint. See docs/CANDIDATE_CHECKPOINT.md for the concrete handoff.
- Main/PWA files remain unchanged; no production rules/data/signing secrets or final production merge/release changed.
- This status-only checkpoint commit does not change the verified candidate source/APK.
