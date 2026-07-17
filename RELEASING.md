# Release signing

Android requires every update to be signed by the same private key. Losing the key or its passwords means existing installations cannot be upgraded.

## Local release

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

## GitHub secrets

Configure these repository Actions secrets before pushing a `v*` tag:

- `ANDROID_KEYSTORE_BASE64`: Base64 representation of the complete JKS file
- `ANDROID_STORE_PASSWORD`: Keystore password
- `ANDROID_KEY_ALIAS`: `qianxi-release`
- `ANDROID_KEY_PASSWORD`: Private-key password

After all four secrets are present, create the Actions repository variable
`SIGNED_RELEASE_ENABLED` with value `true`. Without this variable, tag builds
still run verification but deliberately skip the signed release job.

The workflow restores these values only inside the temporary GitHub Actions runner. Signing files and property files are excluded by `.gitignore`.
