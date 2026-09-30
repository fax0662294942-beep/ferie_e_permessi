# B8 Android Firebase checkpoint

## Existing PWA contract
Inspected directly at `main:index.html` on 2026-09-30.

- Firebase project: `viaggi-camper`.
- Account data: `feriePermessi_data/{firebaseUid}` containing `users`, `currentUserId`, `lastModified` server timestamp.
- Approval registry: `feriePermessi_registry/{firebaseUid}` containing `name`, `email`, `photoURL`, `status` (`pending`, `approved`, `rejected`), `role`, `registeredAt`, `updatedAt`.
- Admin authority: `feriePermessi_config/admins`, field `uids`.
- Logged-out local usage remains possible. Signed-in pending/rejected users must receive the matching access screen and must not synchronize data. Read failures must not grant admin privileges.
- `PwaCloudContract` maps native records back to the exact PWA field names; tests verify all fields and approval decisions. No live Firestore writes have been executed.

## Required configuration before live authentication can be completed
The repository contains only the PWA web Firebase configuration, not an Android app registration or OAuth configuration. The web app ID cannot stand in for an Android Firebase app ID.

1. In the existing project's Firebase settings, register Android package `it.feriepermessi.nativeapp`.
2. Add the SHA-1 of the actual persistent certificate used to sign the candidate. CI currently generates disposable default debug keys; a stable CI signing certificate must be chosen before Google sign-in device validation. Do not commit signing private keys or passwords.
3. Enable the Google authentication provider and download the updated Android `google-services.json` containing the web OAuth client ID.
4. Supply the configuration file for this package. Do not supply service-account private keys.

Official reference: https://firebase.google.com/docs/auth/android/google-signin

## Remaining B8 work after configuration
- Integrate Credential Manager Google ID token and Firebase Authentication.
- Port registry approval screens, admin roles/actions and logout while preserving local data.
- Stage first cloud/local reconciliation with a reviewable conflict preview; never overwrite real local/cloud records automatically merely on first login.
- Test account isolation, listeners, offline handling, denied access and revocation with Firebase emulators before live validation.
- Preserve unknown existing cloud fields, and use explicit conflict detection before updating the shared PWA document.

B8 is not complete until authentication and Firestore integration are implemented and gated. B9 candidate hardening follows that gate. The original PWA and production data remain untouched.

## Configuration received 2026-09-30
The uploaded Android configuration matches the correct package/project and includes a web OAuth client. It is now used by the Google Services Gradle plugin. Firebase mobile configuration identifies the project and is not a service-account private key.

The uploaded file contains no Android OAuth client/certificate pairing. CI therefore publishes `signing-report.txt` alongside the debug APK. Register that build's SHA-1 (and SHA-256) in the existing Firebase Android app, download the updated configuration, and validate Google account selection on a physical device. The Actions cache retains the development debug certificate across successful builds; cache eviction causes certificate rotation and requires re-registration. This certificate is for development only and is not a production signing strategy.

## Implemented test boundary
FirebaseGatewayTest starts named apps pointed exclusively at Auth/Firestore emulators in project demo-feriepermessi. `firebase-test.json` and `tests/firestore.rules` are test fixtures, not production deployment configuration. The CI command is emulators:exec and never firebase deploy.

Backend contract coverage includes first-admin bootstrap, pending/rejected access, promotion/demotion, account isolation, revision conflicts, extension preservation, listeners, logout preserving Room records, safe local archive before cloud load, and admin deletion. Live Google OAuth provider behavior and the existing project's actual security rules still require the candidate/device checkpoint.
