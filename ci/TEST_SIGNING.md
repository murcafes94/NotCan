# Test signing policy

NotCan debug APKs built by CI use a persistent **public test certificate** so Android can install later test versions as updates.

This certificate is deliberately not a production identity. The repository is public, so anyone can read the test key payload and its password. Never use this certificate for Play Store, production, or trusted public distribution.

Before any production release, create a private signing key outside the repository and store it in a secure secret manager / GitHub Actions secrets.

Expected test certificate SHA-256:
`FF:89:09:EF:CA:13:30:71:C2:08:FC:1E:85:A1:67:98:31:15:AA:E2:C5:DE:2C:84:B6:5F:1A:2F:63:F5:F5:86`
