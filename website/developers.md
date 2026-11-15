# For firmware developers

Make your firmware available to FM-1 Flasher users through a source you control. Publish openly or restrict downloads to licensed users on your own service.

[Prepare a source](#prepare-a-source) · [Propose an integration](https://github.com/ekim-pl/android-fm1-flasher/issues/new?template=firmware-source.md)

Users can add a source themselves without submitting it to this project. A proposal to include a built-in source is reviewed separately and is not guaranteed to be accepted.

## Prepare a source

The Android app supports three options:

1. **GitHub repository:** publish `.fwsc` files as release assets and provide `https://github.com/OWNER/REPO`. Published releases and prereleases are included; drafts and files merely committed to a branch are not. GitHub Enterprise and other Git hosts are not supported by this adapter.
2. **Direct package:** provide the HTTPS URL of one `.fwsc` file. The app reads and validates it when checking the source.
3. **JSON catalog:** serve a Hub-style HTTPS catalog and package files. This allows multiple packages and publisher-provided descriptions and compatibility information.

For example, replace every placeholder below with actual package information. The hash is lowercase SHA-256 of the complete `.fwsc`; `product` must match the package identity. Confirm the correct chip for the actual hardware and bytes; `unknown` blocks installation until compatibility is confirmed.

```json
{
  "updated": "2026-01-01",
  "firmwares": [{
    "id": "example-1", "name": "Example firmware", "author": "Example author",
    "version": "1.0", "what": "Example release",
    "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
    "product": "FM-1_001", "chip": "unknown",
    "pkg": "https://firmware.example.com/releases/example.fwsc"
  }]
}
```

The example hash is a placeholder, not a usable firmware checksum. Keep IDs unique and stable. The publisher must supply accurate compatibility and license terms. A hash detects mismatched bytes but does not authenticate a publisher who controls both catalog and hash.

Tell users to open **Settings → Firmware sources**, add the appropriate source and URL, configure authentication if needed, save, select **Check now**, and enable it. Approve the required package and artwork URL scopes for JSON sources. For GitHub and direct package sources, use **Edit → Package compatibility** after checking to confirm compatibility for each package hash. Unknown compatibility blocks installation. The check interval is 24–168 hours; replacing credentials or authorizing OAuth permits another check.

## Private GitHub repository

Use a separate private distribution repository without source code if recipients should not read the project source. A recipient needs their own GitHub account with access to the repository and a fine-grained personal access token restricted to that repository with **Contents: read**. In the source settings, choose **Bearer** and enter the token without the `Bearer` prefix. Never share the owner's token or post credentials in an issue. Repository read access grants more than access to release binaries.

GitHub may return HTTP 404 for an inaccessible private repository. Check the repository URL, account access, token permissions, expiry and any organization approval requirements. Firmware remains available offline after it has been downloaded, even if repository access is later revoked.

## Authentication and licensed access

The source form supports HTTP Basic, Bearer, an API key header, and OAuth 2.0. For individual accounts, use OAuth Authorization Code with PKCE S256 and a public client where the issuer supports it. Configure the client ID, scope, authorization URL and token URL; register this redirect URI exactly:

The redirect below applies to Android 0.9.4-experimental.27 and later. Version 0.9.4-experimental.26 and earlier use the address shown in their source settings. Register the new address before users upgrade or authorize again. In build 28, a sign-in started with build 26 or earlier must be restarted after upgrading.

```text
pl.ekim.fm1-flasher://oauth/callback
```

Save the source and choose **Authorize OAuth 2.0**. The app refreshes expired tokens when the issuer supplies a refresh token. Client Credentials is also supported for service credentials and requires a client secret; do not embed a shared secret in a distributed app. No universal OAuth client is bundled. Secrets entered in the app are encrypted using Android Keystore.

Your server is responsible for accounts, payment, license checks and access decisions. It can return the catalog and package only to an entitled user. Credentials are sent only to the configured source origin, and are stripped on cross-origin redirects, including CDNs. Serve protected packages from that origin or use appropriately scoped, short-lived download URLs. Token POST redirects are refused. URLs must use HTTPS and comply with the app's URL and package scope restrictions.

**This controls downloads, not use of a downloaded package.** FM-1 Flasher supports offline installation and does not provide DRM, device-bound licenses or revoke files already downloaded. A source may retain a cached catalog after a failed check. A withdrawal cannot be learned while offline.

## Integration checklist

- Verify the `.fwsc` on supported hardware; document chip compatibility, version and license terms.
- Serve a real package with its correct hash; test source setup, download and installation with the intended credentials.
- Test expired and revoked credentials, HTTP 401/403/404, redirects, and offline behavior. Never put real credentials in documentation or issues.
- [Open an integration issue](https://github.com/ekim-pl/android-fm1-flasher/issues/new?template=firmware-source.md) for help or built-in source consideration; public issues must not contain private URLs or secrets.

GitHub Pages hosts this documentation as static content. Your authorization and licensing service runs separately. See the [full technical guide](../README.md#catalog-providers-and-authentication).
