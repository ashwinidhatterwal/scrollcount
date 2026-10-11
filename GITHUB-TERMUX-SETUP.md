Public support/privacy contact is configured as **ashwinidhatterwal@gmail.com** in this package. App/backend metadata and the static privacy page have been regenerated. Backend deployment is still required. Contact configuration commands below apply if you change the address later.

# GitHub and Termux signing setup

## Upload source

Extract the ZIP and upload the **contents** of `scrollcount` to the repository root. Include `.github/workflows/build.yml`, `gradle/wrapper/gradle-wrapper.jar`, `app`, `backend` and `gradlew`. Do not leave them inside a nested `scrollcount` folder. Check the hidden `.github` folder is present if using GitHub's web uploader.

## Configure the public privacy contact

Before building a signed release, enter the public email you actually monitor. In Termux install Python (`pkg install python -y`), open the source folder and run:

```bash
python3 tools/configure_release.py --email YOUR_REAL_EMAIL
```

Commit `release-config.json`, `ReleaseInfo.java` and `backend/release-info.mjs` together. The operator defaults to Ashwini Dhatterwal; use `--operator "Your public name"` if needed. Alternatively set the public GitHub Actions **variable** `SCROLLCOUNT_SUPPORT_EMAIL`; CI generates the app metadata, but configure the local source before backend deployment too. This email is public, not a signing secret. Signed builds intentionally stop while it is missing.

## Generate your key on your phone

Run in Termux, keeping the key outside the source repository:

```bash
pkg update
pkg install openjdk-17 coreutils -y
mkdir -p ~/scrollcount-signing
chmod 700 ~/scrollcount-signing
umask 077
keytool -genkeypair -v \
  -keystore ~/scrollcount-signing/scrollcount-upload.jks \
  -storetype JKS -alias scrollcount \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 ~/scrollcount-signing/scrollcount-upload.jks \
  > ~/scrollcount-signing/scrollcount-upload.b64
```

Keytool prompts for your password and certificate details. At the key-password prompt, press Enter to use the same password as the keystore. Keep a private offline backup of the JKS and password. Reuse this key for future builds. The base64 file also contains your private key. No production key has been generated for you.

## Add GitHub secrets

Repository Settings → Secrets and variables → Actions → New repository secret:

| Name | Value |
| --- | --- |
| `SCROLLCOUNT_KEYSTORE_B64` | Entire contents of `scrollcount-upload.b64` |
| `SCROLLCOUNT_STORE_PASSWORD` | Your keystore password |
| `SCROLLCOUNT_KEY_ALIAS` | `scrollcount` |
| `SCROLLCOUNT_KEY_PASSWORD` | Your key password; same as store password if you pressed Enter |

Display the base64 file on your own phone for copying with `cat ~/scrollcount-signing/scrollcount-upload.b64`. Paste directly into GitHub's secret field. Never commit these files/passwords or send them in chat. `.gitignore` excludes common keys, but keep them outside the repository.

## Build and download

Open Actions → Build ScrollCount → Run workflow on your main branch. Pushes also trigger builds. Without secrets, only a debug APK is produced. With all four secrets, tests and lint run and signed release files are built. Partial signing setup fails with an explanation. Pull requests do not use signing secrets.

Download **ScrollCount-Signed-Release** from a successful run's Artifacts section. Extract `app-release.aab` for Google Play internal testing and `app-release.apk` for direct installation. **ScrollCount-Debug-APK** is a separate development artifact, unsuitable for Play submission. Check the first Actions run succeeds before treating these files as build-verified.

## Certificates and friend links

With Play App Signing, the Play app-signing certificate can differ from your upload certificate. Copy the **app signing key certificate SHA-256** from Play Console App integrity. Add that colon-separated uppercase fingerprint to `appSigningCertificates` in `release-config.json`, then run `python3 tools/configure_release.py`. The backend includes it alongside the older test certificate. Add your upload certificate too if directly installed release APKs should open verified HTTPS links. Obtain its fingerprint in Termux:

```bash
keytool -list -v -keystore ~/scrollcount-signing/scrollcount-upload.jks -alias scrollcount
```

Redeploy using `backend/README.md` and test invitation confirmation on a recipient phone. Only deploy actual fingerprints, never placeholders.

Your new key differs from the earlier test APK's key. Android rejects updates across different signing certificates. Test on another device to preserve the current phone's history. Export history before any uninstall: CSV is a readable backup, and there is currently no CSV restore feature. Uninstalling removes sharing credentials/private keys too, requiring a new sharing profile and friend setup. Play and directly installed upload-key APKs may also have different signatures.

References: [Termux OpenJDK 17](https://github.com/termux/termux-packages/blob/master/packages/openjdk-17/build.sh), [Android signing](https://developer.android.com/studio/publish/app-signing), [App Links](https://developer.android.com/training/app-links/verify-android-applinks).
