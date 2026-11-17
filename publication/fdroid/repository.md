# F-Droid repository on GitHub Pages

The Pages workflow can download a published F-Droid APK and its manifest, verify
their SHA-256 and APK signing certificate, generate a signed index, and deploy the
APK and index with the website. It uses one selected stable release at a time.

## One-time setup

1. Create a separate index signing key on a trusted computer, outside the project:

   ```sh
   umask 077
   mkdir -p ~/.local/share/fm1-signing
   keytool -genkeypair -keystore ~/.local/share/fm1-signing/fm1-index.p12 \
     -storetype PKCS12 -alias fm1-index -keyalg RSA -keysize 4096 -validity 10000
   ```

   Use the same password for the PKCS12 keystore and key. Back up the keystore,
   alias and passwords securely. This key must stay the same for the repo's life;
   it is separate from the APK signing key. Never commit either keystore.
2. In GitHub Settings → Secrets and variables → Actions, add repository secrets:
   `FDROID_INDEX_KEYSTORE_BASE64` (Linux: `base64 -w 0 ~/.local/share/fm1-signing/fm1-index.p12`),
   `FDROID_INDEX_KEYSTORE_PASSWORD`, `FDROID_INDEX_KEY_ALIAS` (`fm1-index`),
   and `FDROID_INDEX_KEY_PASSWORD`.
3. Add repository variables:
   - `FDROID_REPO_ENABLED`: `true` (set this last).
   - `FDROID_RELEASE_TAG`: published stable tag, for example `v0.10.1`.
   - `FDROID_REPO_URL`: `https://ekim-pl.github.io/android-fm1-flasher/fdroid/repo` (or your actual Pages URL).
   - `FDROID_APP_CERT_SHA256`: the trusted APK signing certificate fingerprint,
     the same value as the existing `fdroid-release` environment secret.
4. Run GitHub Pages from the default branch, then add the URL to an F-Droid
   client and check the app and the index signing fingerprint. Obtain that
   fingerprint with `keytool -list -v -keystore ~/.local/share/fm1-signing/fm1-index.p12 -alias fm1-index`.

For each release, publish the signed APK using the existing release workflow,
change `FDROID_RELEASE_TAG`, and manually run GitHub Pages on the default branch.
The selected release must contain `fm1-flasher-<tag>-fdroid.apk` and
`fdroid-manifest.json`. Failed verification blocks Pages deployment. The repo
contains only the selected version; keep release assets available on GitHub.

When enabled, every default-branch Pages deployment regenerates the repo. Removing
or disabling `FDROID_REPO_ENABLED` causes the next deployment to omit the repo.
The workflow stores the index signing key in GitHub Actions secrets and uses it
on the runner. Anyone able to modify trusted default-branch workflow code can
potentially access repository secrets.
