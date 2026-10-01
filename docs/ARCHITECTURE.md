# Native architecture

Functional baseline: `main:index.html`. Native development stays on `android-native`.

- Kotlin, Compose Material 3, explicit saveable screen/month state; modal dismissal before Home, then double Back to exit.
- NativeViewModel coordinates UI and repository mutations; CloudController serializes local/cloud changes with a mutex.
- Room v1, checked-in schema, isolated profile identities and no destructive migration fallback. New schema versions require explicit migrations and fixtures.
- LeaveEngine and Statistics implement deterministic PWA rules independently of Android UI.
- BackupCodec validates bounded versioned JSON before transactional Room import/replace. SAF handles files. Legacy import adds profiles; replacement requires separate confirmation.
- Credential Manager Google tokens are exchanged by Firebase Auth. Firestore preserves PWA collection/document/field names, registry approval and administrator authority.
- First sync requires a comparison and explicit data-direction confirmation; cloud downloads retain a local recovery snapshot. Server revision checks and approval checks guard uploads. Logout retains local records.
- Firebase instrumentation uses named demo apps and test rules exclusively. Real Google provider, production rules and vendor behavior are checked on the physical candidate.

Development APKs use a cached CI debug certificate. CI verifies the APK certificate against Android OAuth registration; production signing and release require their separate human checkpoint.
