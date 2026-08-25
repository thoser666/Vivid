# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.5.x   | :white_check_mark: |
| 0.4.x   | :white_check_mark: |
| < 0.4   | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability in Vivid, please report it responsibly.

**Do not open a public issue.** Instead, please email [vivid.security@proton.me](mailto:vivid.security@proton.me) with:

- A description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### What to expect

- **Acknowledgment** within 48 hours
- **Initial assessment** within 1 week
- **Fix or mitigation** for confirmed vulnerabilities will be prioritized
- You will be credited in the release notes (unless you prefer to remain anonymous)

### Scope

Vivid is an open-source IRL streaming app. Security issues particularly relevant to:

- **Stream keys and OAuth tokens** — must never be logged or exposed
- **Network communications** — RTMPS/TLS enforcement
- **Chat bot credentials** — bot OAuth tokens, LLM API keys
- **Remote control** — LAN-based web remote (no external exposure intended)
- **Location data** — GPS coordinates used for the text/info widget

### Out of scope

- Social engineering attacks
- Issues requiring physical access to the user's device
- Vulnerabilities in third-party dependencies (report these upstream)
