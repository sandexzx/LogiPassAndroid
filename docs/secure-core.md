# Secure core

The application container is created once by `LogiPassApplication`. The placeholder activity intentionally has no authentication or vault UI yet. A future UI should observe `container.auth.state` and `container.vault.services` and use the typed results in `session/AuthManager.kt` and `data/BackupCodec.kt`.

## Storage and sessions

`noBackupFilesDir/vault.bin` contains versioned AES-256-GCM ciphertext. `keys.json` contains only wrapped keys and KDF parameters. A password-derived key wraps the data key; a non-authenticated Android Keystore key wraps that result again. Password unlock is device-bound. Portable exports use a separate password and do not depend on Keystore.

Repository mutations are serialized. A mutation publishes its new state before writing, as specified by the API; a failed write throws and leaves the published state in memory. The caller must handle that exception and must not claim that the data was saved. Locking clears published services and session keys immediately. An already-started write owns a separate key copy until it completes, then zeroes it. Unlock loads storage without publishing it until the session generation has been checked, so an intervening lock cannot reopen the session.

A process lifecycle observer locks after 60 seconds in the background, with an elapsed-realtime check on return for deep sleep. Before opening a system document picker, call `suspendAutoLock()`. Call `resumeAutoLock()` in every result path, including cancellation. Import is a two-stage operation: `probe`, then optional `decrypt`; the UI must confirm replacement before calling `replaceAll`.

## Biometrics

Only strong biometrics are accepted. Enrollment and unlock require the exact cipher issued by `AuthManager`; pass it through `BiometricAuthenticator.authenticate` before completing the operation. The password is always the fallback. A changed biometric enrollment or missing biometric key disables the biometric wrapper. Cancelling an enrollment leaves the password usable; the UI can start enrollment again.

Changing a password requires the current password or a fresh authenticated biometric decrypt cipher, not just an unlocked screen. There is no password recovery. Wipe removes both current encrypted files, legacy plaintext, old PIN material, Keystore keys and throttle preferences. Corrupt files renamed during startup are retained for diagnosis; they are not automatically deleted.

## Migration and failure handling

Legacy JSON is read only when no encrypted key file exists. Invalid legacy input is retained unchanged. Setup writes and decrypts the new vault before deleting legacy secrets. Before the verified encrypted pair is committed, setup failure removes partial encrypted files and keeps the setup state. Once legacy cleanup begins, a cleanup failure retains the verified encrypted pair instead of deleting the only remaining copy of the data.

Orphan encrypted files and malformed key files are renamed with a `.corrupt-<timestamp>` suffix. Existing encrypted files with a missing device key remain locked; password attempts return `WrongPassword` and log only the failure class. Wipe is the supported reset path.

## Security boundaries

Screenshots, recents screenshots, cloud backup and device transfer are disabled. Sensitive clipboard copies carry the sensitive flag and expire after 45 seconds unless another clip replaced them. Keystore encryption uses provider-generated nonces, as required by Android's randomized-encryption policy.

No passwords, vault contents, data keys or clipboard contents are logged. Debug builds log only KDF timing, plus non-sensitive operational failures. PBKDF2 timing must be measured on the target device once UI testing is available; desktop JVM tests use a low iteration count and do not provide a device performance measurement.

JVM coverage includes AEAD tamper/AAD checks, nested password wrapping with a fake device key, export codecs, legacy mapping, throttle schedule, repository CRUD/order/restore/replacement, persistence failures and a lock during a blocked write. Keystore authorization, biometric enrollment changes, lifecycle timing/deep sleep, clipboard restrictions and screenshot prevention require device tests after the UI is available.
