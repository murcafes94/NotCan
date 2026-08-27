# NotCan test signing key

The keystore in this directory is used **only** to keep NotCan test APKs update-compatible across GitHub Actions runs.

It is intentionally a public, non-production signing key because this repository is public. It must never be reused for a Play Store or production release. A future production/distribution build must use a private keystore stored outside the repository (for example, GitHub Actions secrets).

Test key alias: `notcan-test`
Test store/key password: `notcan-test`
Certificate SHA-256: `FF:89:09:EF:CA:13:30:71:C2:08:FC:1E:85:A1:67:98:31:15:AA:E2:C5:DE:2C:84:B6:5F:1A:2F:63:F5:F5:86`
