# Release signing

Android requires every update to be signed by the same private key. Losing the key or its passwords means existing installations cannot be upgraded.

## Local release

Before publishing, update `versionCode` and `versionName` in `app/build.gradle.kts`,
the latest version in `USAGE.md`, `CHANGELOG.md` and the Chinese release body in
`RELEASE_NOTES.md`. The tag must match the app version (for example `v1.18.9`).
Tag builds publish the signed APK, source ZIP and `USAGE.md` with the existing
release asset names, and use `RELEASE_NOTES.md` as the release description.

Create an ignored `keystore.properties` in the project root:

```properties
storeFile=../QianxiSchedule-signing/qianxi-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=qianxi-release
keyPassword=YOUR_KEY_PASSWORD
```

Then run:

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleRelease
```

With an Android emulator or test device connected, run the image and editor
interaction checks with `./gradlew.bat connectedDebugAndroidTest`.

## GitHub secrets

Configure these repository Actions secrets before pushing a `v*` tag:

- `ANDROID_KEYSTORE_BASE64`: Base64 representation of the complete JKS file
- `ANDROID_STORE_PASSWORD`: Keystore password
- `ANDROID_KEY_ALIAS`: `qianxi-release`
- `ANDROID_KEY_PASSWORD`: Private-key password

After all four secrets are present, tag builds sign and publish by default.
Set the Actions repository variable `SIGNED_RELEASE_ENABLED` to `false` only
when signed publication should deliberately be skipped.

The workflow restores these values only inside the temporary GitHub Actions runner. Signing files and property files are excluded by `.gitignore`.
