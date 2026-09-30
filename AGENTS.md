# Autonomous Development Protocol

Follow the Android App Factory protocol from fax0662294942-beep/android-app-factory@develop.

For every block: read docs/DEVELOPMENT_STATUS.md; inspect latest CI; implement the next unchecked block; add tests for stable rules; commit/push; inspect Actions; diagnose and fix failures without asking the user; update status after a green gate.

Never modify or delete the legacy PWA baseline merely to make native CI pass. Preserve its behavior as the functional specification until the corresponding native feature has regression coverage.

Stop only for genuine product ambiguity, destructive real-user-data operations, credentials/secrets, physical-device-only validation, subjective UI acceptance, or final production merge/release.

Back behavior is mandatory: navigate back to Home first; on Home first Back shows "Premi di nuovo Indietro per uscire", second Back within about two seconds exits.
