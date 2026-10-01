# Native candidate gates

The authoritative workflow is `.github/workflows/android-native-ci.yml`.

1. Firebase project/package/web OAuth and APK signing certificate validation.
2. JVM: accrual, FIFO, holiday rules, statistics, Back boundaries, cloud payload/policy, backup validation and round trips.
3. Android lint and APK assembly; certificate report and SHA-256 checksum exported with APK.
4. API 26 and API 35 instrumentation: Room/profile isolation/CRUD, atomic backup/restore, disk reopen, Compose navigation/modals/validation and Firebase Auth/Firestore emulator integration.

Firebase regression tests cover approval/rejection, admin roles/deletion, isolation, concurrent revision rejection, unknown-field preservation, live listeners, offline failure and logout retaining local data. Tests use `demo-feriepermessi`; production security rules are never deployed by this workflow.

Current schema is v1. Reopen/persistence is tested now; any future schema change must add real version-to-version migration coverage, not destructive fallback.

Human checkpoint after a green candidate: install APK, Google account chooser and live project approval, view local/cloud comparison, review UI, hardware Back navigation/exit, SAF file selection and vendor-specific behavior. Real-data overwrite, production merge/signing/release require explicit authorization.
