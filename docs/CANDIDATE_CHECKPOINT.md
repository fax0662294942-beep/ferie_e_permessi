# Candidate physical checkpoint: 0.2.0-rc1

## Verified candidate
- Source: 0997347dcc81996b86a86d69c7f44bc39bce8cee on android-native.
- Green Action #20: https://github.com/fax0662294942-beep/ferie_e_permessi/actions/runs/36804400687
- Download the FeriePermessiNative-debug ZIP from that Action's Artifacts section:
  https://github.com/fax0662294942-beep/ferie_e_permessi/actions/runs/36804400687/artifacts/11136817681
- Extract and install app-debug.apk. Package: it.feriepermessi.nativeapp; versionCode 2; versionName 0.2.0-rc1.
- Minimum Android 8. The APK is development-signed with the Firebase-registered CI certificate.
- All build/lint/JVM/signature gates and 10 instrumentation tests on each of API 26/35 passed.

## Human checks
1. Export any existing native local ledger backup before installing or reconciling accounts. If Android refuses an update due to an older different signing key, do not uninstall a data-bearing app without a backup.
2. Open Account e cloud, tap Accedi con Google, select the intended account. Verify the real Google provider completes and the existing Firebase project's approval/role screen matches the account.
3. Check the local/cloud comparison. Authentication testing alone does not require selecting local upload over an existing cloud ledger. Any real-data replacement requires a deliberate data-direction decision.
4. Check the screens/layout on the actual phone, keyboard/dialog Back dismissal, navigation to Home, and two Back presses to exit from Home.
5. Check SAF backup export/file selection on the phone. Use synthetic data for destructive restore tests.

Report any error verbatim or provide its screenshot. Once live Google/existing-project rules and physical/UI behavior are accepted, B8/B9 can be closed. Production signing, merge and final release retain their separate human checkpoint.

## Why development stops here
AGENTS.md says: "Stop only for genuine product ambiguity, destructive real-user-data operations, credentials/secrets, physical-device-only validation, subjective UI acceptance, or final production merge/release."

Real Google account selection and vendor/UI validation cannot be established by Firebase-emulator tests. No production data or security rules were modified to make tests pass.
