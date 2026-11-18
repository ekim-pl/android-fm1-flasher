# F-Droid repository on GitHub Pages

The Pages workflow can download a published F-Droid APK and its manifest, verify
their SHA-256 and APK signing certificate, generate a signed index, and deploy the
APK and index with the website. It selects the highest published stable version
whose tag is reachable from the default branch; drafts and prereleases are ignored.
The repo contains one release at a time.

The same Pages deployment includes `update.json` with the selected APK's
version and signing fingerprint and the index signer's fingerprint. F-Droid
builds use it to show an update link in Settings → App and open the F-Droid
client for installation. Local release builds can enable this check with
`-Pfm1FdroidUpdates=true`.

The project repository URL is https://ekim-pl.github.io/android-fm1-flasher/fdroid/repo/. Add it to an F-Droid client to receive the selected release. This is a project-hosted repository, separate from the official F-Droid catalog. GitHub release APKs are also available directly from the releases page. Switching between distributions requires compatible APK signing certificates.

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
   - `FDROID_REPO_URL`: `https://ekim-pl.github.io/android-fm1-flasher/fdroid/repo` (or your actual Pages URL).
   - `FDROID_APP_CERT_SHA256`: the trusted APK signing certificate fingerprint,
     the same value as the existing `fdroid-release` environment secret.
4. Run GitHub Pages from the default branch, then add the URL to an F-Droid
   client and check the app and the index signing fingerprint. Obtain that
   fingerprint with `keytool -list -v -keystore ~/.local/share/fm1-signing/fm1-index.p12 -alias fm1-index`.

Publishing a release with the existing Android publication workflows automatically
starts Pages after the publication workflow succeeds. Releases published outside
Actions also trigger Pages. The selected release must contain `fm1-flasher-<tag>-fdroid.apk` and
`fdroid-manifest.json`. Failed verification blocks Pages deployment. The repo
contains only the selected version; keep release assets available on GitHub.

When enabled, every default-branch Pages deployment regenerates the repo. A failed
publication or a missing/invalid APK leaves the previous Pages deployment in place. Removing
or disabling `FDROID_REPO_ENABLED` causes the next deployment to omit the repo.
The workflow stores the index signing key in GitHub Actions secrets and uses it
on the runner. Anyone able to modify trusted default-branch workflow code can
potentially access repository secrets.
