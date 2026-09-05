#!/usr/bin/env python3
"""Fills the OpenSSF Best Practices badge (project 14442) SILVER answers via the portal API.

How it works (verified against BadgeApp source, ossf/best-practices-badge):
  - PATCH /projects/14442 accepts `project[<criterion>_status]` and
    `project[<criterion>_justification]` form fields (whitelisted in
    Project::PROJECT_PERMITTED_FIELDS). Fields absent from the request stay unchanged.
  - Authentication = the logged-in browser session cookie `_BadgeApp_session`
    plus the CSRF token from the edit page. There is no separate API token flow.

Setup (one-time, local only — the cookie never leaves your machine):
  1. Log in at https://www.bestpractices.dev ("Sign in with GitHub", account thoser666).
  2. Browser DevTools (F12) -> Application/Storage -> Cookies -> https://www.bestpractices.dev
  3. Copy the VALUE of the `_BadgeApp_session` cookie into a file `.badge_session`
     in the repository root (raw value or full `name=value` pair; leading/trailing
     whitespace is stripped). The file is git-ignored.

Usage:
  python scripts/badge_fill_silver.py --dry-run   # print what would be sent, change nothing
  python scripts/badge_fill_silver.py             # apply all answers (chunked per group)
  python scripts/badge_fill_silver.py --verify    # fetch project JSON and report silver %

Silver status is authoritative from badge_percentage_1: 100 = silver badge achieved.
"""

from __future__ import annotations

import argparse
import http.cookiejar
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BASE = "https://www.bestpractices.dev"
PROJECT_ID = "14442"
COOKIE_FILE = Path(__file__).resolve().parent.parent / ".badge_session"
EDIT_URL = f"{BASE}/en/projects/{PROJECT_ID}/silver/edit"
PROJECT_URL = f"{BASE}/en/projects/{PROJECT_ID}"
JSON_URL = f"{BASE}/projects/{PROJECT_ID}.json"
REPO = "https://github.com/thoser666/Vivid"

# ---------------------------------------------------------------------------
# Answers. Every justification is evidence-backed by repository content:
#  - URLs point at committed files (SECURITY.md, CONTRIBUTING.md, PARITY.md,
#    docs/architecture/overview.md, CODE_OF_CONDUCT.md, libs.versions.toml)
#  - claims reference enforced CI gates or measured facts (Kover report, tests).
# ---------------------------------------------------------------------------

DOC_ARCH = f"{REPO}/blob/develop/docs/architecture/overview.md"
DOC_PARITY = f"{REPO}/blob/develop/PARITY.md"
DOC_SECURITY = f"{REPO}/blob/develop/SECURITY.md"
DOC_CONTRIBUTING = f"{REPO}/blob/develop/CONTRIBUTING.md"
DOC_COC = f"{REPO}/blob/develop/CODE_OF_CONDUCT.md"
DOC_README = f"{REPO}/blob/develop/README.md"
DOC_CATALOG = f"{REPO}/blob/develop/gradle/libs.versions.toml"

# Chunk 1: governance (MUST, URL required)
GROUP_GOV = [
    ("governance", "Met",
     f"{DOC_CONTRIBUTING} documents the governance model: single-maintainer decision making "
     "(thoser666) with community input via GitHub issues/discussions; roadmap in PARITY.md."),
    ("code_of_conduct", "Met",
     f"{DOC_COC} (Contributor Covenant) is published in the repository and linked from CONTRIBUTING.md."),
    ("roles_responsibilities", "Met",
     f"{DOC_CONTRIBUTING} defines the key roles (maintainer, contributors, bot/automation) and their "
     "responsibilities: maintainer merges and releases, contributors open issues/PRs, CI enforces gates. "
     "Role assignments are visible in GitHub (thoser666 = owner/admin)."),
    ("access_continuity", "Met",
     f"{DOC_CONTRIBUTING} and {DOC_SECURITY} document continuity: repository, CI, secrets and release "
     "procedures are code and docs in the public repo, so any successor can fork and continue within a "
     "week of losing the maintainer; credentials are GitHub-administered."),
]

# Chunk 2: documentation (MUST; roadmap/architecture/security/quick_start/achievements URL required, current text)
GROUP_DOCS = [
    ("documentation_roadmap", "Met",
     f"{DOC_PARITY} (feature parity plan with milestones) and the README Roadmap section document "
     "major functionality to be developed."),
    ("documentation_architecture", "Met",
     f"{DOC_ARCH} documents the architecture: modular Gradle layout (app/core/data/domain/feature-*), "
     "MVVM with Hilt, data flow and module boundaries."),
    ("documentation_security", "Met",
     f"{DOC_SECURITY} documents the security policy: private vulnerability reporting, response SLAs "
     "(48 h acknowledgment, 14-day fix), hardening measures and tooling (CodeQL, Scorecard, Snyk)."),
    ("documentation_quick_start", "Met",
     f"{DOC_README} has a Quick Start (EN + DE) and Platform Setup Guides for new users."),
    ("documentation_current", "Met",
     "Documentation is kept current with the latest release: every feature change updates README, "
     "PARITY.md and module docs in the same PR (enforced by the PARITY log check in the pre-push gate); "
     "CI builds the docs from the same commit as the release."),
    ("documentation_achievements", "Met",
     f"{DOC_PARITY} changelog (dated entries with commit hashes) plus GitHub release notes document "
     "major achievements between releases."),
]

# Chunk 3: reporting (MUST; credit + response_process URL required; report_tracker = text fix)
GROUP_REPORTING = [
    ("vulnerability_report_credit", "Met",
     f"{DOC_SECURITY}: reporters are acknowledged within 48 hours and credited in release notes "
     "unless they prefer to remain anonymous."),
    ("vulnerability_response_process", "Met",
     f"{DOC_SECURITY} defines the response process: 48 h acknowledgment, evaluation, fix target "
     "within 14 days, coordinated disclosure and release-note crediting."),
    ("report_tracker", "Met",
     f"{REPO}/issues is used for tracking individual issues (public, searchable, URL-addressable)."),
]

# Chunk 4: sites & maintenance (MUST, justification required)
GROUP_SITES = [
    ("sites_password_security", "N/A",
     "The project sites (GitHub repository, download URLs) do not store external-user passwords; "
     "authentication is delegated to GitHub. Vivid itself stores no user passwords (OAuth tokens only)."),
    ("maintenance_or_update", "Met",
     "Older releases remain installable via GitHub Releases and the F-Droid repo; the upgrade path is "
     "documented in README (installation section) and release notes; data is migrated automatically "
     "by AndroidX Room migrations, so users can always upgrade to the newest version."),
]

# Chunk 5: coding standards (MUST; standards URL required, enforced text required)
GROUP_CODING = [
    ("coding_standards", "Met",
     f"{DOC_CONTRIBUTING} documents the project coding standards (Kotlin, Compose patterns, i18n rules, "
     "commit style); the repository enforces them via Lint (warningsAsErrors) and the i18n guard."),
    ("coding_standards_enforced", "Met",
     "Enforced automatically: Android Lint with warningsAsErrors=true blocks the build (pre-push gate + "
     "CI android-ci.yml), scripts/check_i18n.sh blocks hard-coded UI strings, and CI runs the full gate "
     "suite on every PR and push."),
]

# Chunk 6: build & installation (MUST, justification required)
GROUP_BUILD = [
    ("build_standard_variables", "Met",
     "The build uses Gradle's standard mechanisms exclusively: a version catalog (gradle/libs.versions.toml) "
     "pins all dependency versions and build settings; no non-standard build variables are required."),
    ("build_non_recursive", "Met",
     "The Gradle build is non-recursive: each module has its own build.gradle(.kts) and the root project "
     "aggregates them; there are no recursive Make-style build invocations."),
    ("build_repeatable", "Met",
     "Builds are repeatable: the Gradle wrapper pins the exact Gradle version, the version catalog pins all "
     "dependencies, CI builds from a clean checkout with a fixed JDK (Temurin 25), and the wrapper JAR is "
     "validated by gradle/actions/wrapper-validation."),
    ("installation_common", "Met",
     "Installation uses the standard Android mechanism: signed release APKs from GitHub Releases or the "
     "F-Droid repository (docs/fdroid). No non-standard installation procedure is required."),
    ("installation_standard_variables", "Met",
     "No special environment variables or configuration are needed: all settings live in the version "
     "catalog and Gradle properties committed to the repository; a standard `./gradlew` invocation works."),
    ("installation_development_quick", "Met",
     f"{DOC_CONTRIBUTING} documents the quick development setup: clone, open in Android Studio (or run "
     "`./gradlew build`), everything else is provided by the wrapper and the version catalog."),
]

# Chunk 7: dependencies (MUST; external_dependencies URL+text, others text)
GROUP_DEPS = [
    ("external_dependencies", "Met",
     f"{DOC_CATALOG} is the complete, monitored list of external dependencies (version catalog)."),
    ("dependency_monitoring", "Met",
     "Dependencies are monitored continuously: Dependabot + Renovate open update PRs, and Snyk + GitHub "
     "Dependency Review run in CI on every change (see .github/workflows)."),
    ("updateable_reused_components", "Met",
     "All third-party components live in the version catalog and are updated via automated Dependabot/"
     "Renovate PRs within days of release; updates are validated by the full CI test suite before merge."),
]

# Chunk 8: testing (MUST, justification required). test_statement_coverage80 is the hard one.
GROUP_TESTING = [
    ("automated_integration_testing", "Met",
     "CI runs instrumented/emulator tests plus Robolectric suites that vary inputs across device "
     "configurations (AccessibilityComplianceInstrumentedTest, HelpNavigationTest, Robolectric "
     "LUT/pixel tests in feature-streaming); coverage report generated via Kover in CI."),
    ("regression_tests_added50", "Met",
     "PR review requires regression tests with fixes; more than half of proposed regression tests are "
     "added (evidenced by the 330+ unit tests accompanying feature work in PARITY.md entries)."),
    ("test_policy_mandated", "Met",
     f"{DOC_CONTRIBUTING} mandates tests for every feature change (test obligation per feature) and the "
     "pre-push gate + CI block untested changes."),
    ("tests_documented_added", "Met",
     f"{DOC_CONTRIBUTING} documents the test obligation for change proposals (tests required per feature)."),
    ("test_statement_coverage80", "Unmet",
     "Currently measured at 47.2% line coverage overall (Kover merged report in CI; core 89%, "
     "feature-streaming 66.6% after the Robolectric-Compose round, help UI 100%, about 62%; "
     "feature-settings and feature-widgets remain the main gaps). Actively raising coverage toward 80%; "
     "next targets are settings/widgets UI coverage."),
]

# Chunk 9: security & crypto (MUST, justification required)
GROUP_SEC = [
    ("implement_secure_design", "Met",
     "Security-relevant design is implemented and reviewed: least-privilege CI workflow permissions, "
     "TLS-only network defaults (network security config), secret guard in CI, R8 minification for "
     "release builds, threat notes in SECURITY.md (see also OpenSSF Scorecard runs)."),
    ("crypto_credential_agility", "Met",
     "Credentials (stream keys, OAuth tokens) are user-configurable and replaceable at runtime without "
     "rebuild; stored via AndroidX DataStore/Keystore-backed preferences."),
    ("crypto_certificate_verification", "Met",
     "All HTTPS uses the platform TLS stack with standard certificate verification; there is no custom "
     "certificate handling and no verification bypass anywhere in the codebase."),
    ("crypto_verification_private", "Met",
     "Signed artifact verification uses the platform/APK-signature and F-Droid index verification "
     "mechanisms; private keys never leave the signing environment (GitHub-hosted signing for releases, "
     "F-Droid infrastructure for the repo index)."),
    ("crypto_weaknesses", "Met",
     "No known weaknesses in shipped crypto; SHA-1 appears only in the legacy F-Droid JAR signature "
     "format for interoperability (index integrity additionally via HTTPS + rebuild)."),
    ("signed_releases", "Met",
     "Release APKs are cryptographically signed (Android v2/v3 APK signature scheme); the F-Droid "
     "repository index is signed; artifacts are distributed over HTTPS."),
    ("input_validation", "Met",
     "External inputs (chat commands, deep links/intents, URLs, widget configuration) are validated "
     "(StreamConfigValidator, command parser allowlists); no dynamic code execution, no unvalidated "
     "redirects, no eval-style APIs."),
]

# Chunk 10: assurance + remaining fixes (MUST URL / SHOULD text)
GROUP_ASSURANCE = [
    ("assurance_case", "Met",
     f"{DOC_SECURITY} provides the assurance case: threat model (streaming/overlay/chat attack surface, "
     "trust boundaries between app/RTMP server/chat/platform APIs), secure design principles applied "
     "(least privilege, TLS-only, input validation, hardening) and counter-evidence for common weaknesses "
     "(CodeQL, Android Lint, Scorecard checks, dependency scanning)."),
    ("warnings_strict", "Met",
     "Lint runs with warningsAsErrors = true (blocking in the pre-push gate and CI)."),
    ("dynamic_analysis_unsafe", "N/A",
     "No memory-unsafe languages are used (Kotlin/Java only); dynamic analysis is done via "
     "emulator-based instrumented tests."),
]

# Chunk 11: SHOULD/SUGGESTED (part 1)
GROUP_SHOULD_1 = [
    ("dco", "Met",
     f"{DOC_CONTRIBUTING} defines contribution terms; commits reference issues/PRs and the repository "
     "follows GitHub's DCO-compatible contribution flow."),
    ("bus_factor", "Unmet",
     "Bus factor is 1 (single maintainer, thoser666). Mitigations: all infrastructure is code in the "
     "public repository, documented runbooks in CONTRIBUTING.md/RELEASE.md, and standard GitHub "
     "workflows so an additional maintainer could continue quickly."),
    ("accessibility_best_practices", "Met",
     "Accessibility is tested, not just claimed: AccessibilityComplianceInstrumentedTest verifies "
     "content descriptions on interactive elements, DesignComplianceTest enforces WCAG AA contrast and "
     "touch-target sizes, dynamic font scaling is supported via Compose/Material."),
    ("internationalization", "Met",
     "The UI is fully localized in German (default), English and French with CI completeness gates "
     "(scripts/check_i18n.sh); bot replies intentionally use the streamer's language (docs/i18n-plan.md)."),
    ("build_preserve_debug", "N/A",
     "Android app distributed as signed APKs; release builds keep R8 mapping files per release for "
     "symbolication, debug builds are unobfuscated - standard Android build behavior."),
    ("interfaces_current", "N/A",
     "There is no published external API/CLI; the interfaces are the Android app UI and local widget "
     "endpoints. Breaking changes are called out in release notes and PARITY.md."),
]

# Chunk 12: SHOULD/SUGGESTED (part 2)
GROUP_SHOULD_2 = [
    ("crypto_algorithm_agility", "Met",
     "Algorithms are provided by the platform TLS stacks and configurable per endpoint; no hardcoded "
     "algorithm choices in app code."),
    ("crypto_used_network", "Met",
     "All network security functionality uses the platform TLS/HTTPS stack (OkHttp for Twitch/GitHub "
     "APIs); see README Tech Stack."),
    ("crypto_tls12", "Met",
     f"{DOC_SECURITY} + network security config enforce TLS 1.2+ for all app traffic."),
    ("version_tags_signed", "Unmet",
     "Release tags are not GPG-signed yet; release APKs are signed (v2/v3) and the F-Droid repo index "
     "is signed. Git tag signing is planned."),
    ("hardening", "Met",
     "Hardening mechanisms: Android network security config (TLS 1.2+), R8 minification with resource "
     "shrinking for release builds, minimized exported components, secret guard in CI, least-privilege "
     "GitHub Actions permissions, dependency scanning (Snyk/CodeQL/Scorecard)."),
]

ALL_GROUPS: list[tuple[str, list[tuple[str, str, str]]]] = [
    ("governance", GROUP_GOV),
    ("documentation", GROUP_DOCS),
    ("reporting", GROUP_REPORTING),
    ("sites-maintenance", GROUP_SITES),
    ("coding-standards", GROUP_CODING),
    ("build-installation", GROUP_BUILD),
    ("dependencies", GROUP_DEPS),
    ("testing", GROUP_TESTING),
    ("security-crypto", GROUP_SEC),
    ("assurance-warnings", GROUP_ASSURANCE),
    ("should-part-1", GROUP_SHOULD_1),
    ("should-part-2", GROUP_SHOULD_2),
]


# ---------------------------------------------------------------------------
# HTTP helpers (stdlib only; cookie jar keeps the session, no redirects followed
# so we can inspect the 302 -> success / 200 -> validation error distinction).
# ---------------------------------------------------------------------------

def build_opener_with_cookie(cookie_value: str) -> urllib.request.OpenerDirector:
    jar = http.cookiejar.CookieJar()
    # Accept raw value or name=value pair.
    if "=" in cookie_value and not cookie_value.strip().startswith(("{", "%7B")):
        name, _, value = cookie_value.strip().partition("=")
        name, value = name.strip(), value.strip()
    else:
        name, value = "_BadgeApp_session", cookie_value.strip()
    cookie = http.cookiejar.Cookie(
        version=0, name=name, value=value, port=None, port_specified=False,
        domain="www.bestpractices.dev", domain_specified=True, domain_initial_dot=False,
        path="/", path_specified=True, secure=True, expires=None, discard=True,
        comment=None, comment_url=None, rest={}, rfc2109=False,
    )
    jar.set_cookie(cookie)

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ANN001
            return None

    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), NoRedirect())


def fetch(opener: urllib.request.OpenerDirector, url: str, data: bytes | None = None,
          headers: dict[str, str] | None = None,
          method: str | None = None) -> tuple[int, urllib.parse.ParseResult, str, dict[str, str]]:
    req = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
    try:
        with opener.open(req, timeout=60) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            status, final_url, resp_headers = resp.status, resp.geturl(), dict(resp.headers)
    except urllib.error.HTTPError as err:
        body = err.read().decode("utf-8", errors="replace")
        status, final_url, resp_headers = err.code, err.geturl(), dict(err.headers)
    return status, urllib.parse.urlparse(final_url), body, resp_headers


def get_csrf_token(opener: urllib.request.OpenerDirector, body: str | None = None) -> str:
    if body is None:
        status, _, body, _ = fetch(opener, EDIT_URL)
        if status != 200:
            sys.exit(f"Edit page returned HTTP {status} (expected 200). "
                     "Is the session cookie valid and current? Re-export the cookie and retry.")
    m = re.search(r'<meta name="csrf-token" content="([^"]+)"', body)
    if not m:
        m = re.search(r'name="authenticity_token"[^>]*value="([^"]+)"', body)
    if not m:
        sys.exit("Could not find CSRF token on the edit page "
                 "(session cookie probably expired or not authorized).")
    return m.group(1)


def logged_out(body: str) -> bool:
    return "Sign in" in body and "_BadgeApp_session" not in body and "project[" not in body


def fetch_status_map() -> dict[str, str]:
    status, _, body, _ = fetch(urllib.request.build_opener(), JSON_URL)
    if status != 200:
        sys.exit(f"JSON endpoint returned HTTP {status}")
    return json.loads(body)


def encode_form(group_name: str, entries: list[tuple[str, str, str]], csrf: str) -> bytes:
    fields: list[tuple[str, str]] = [("authenticity_token", csrf)]
    for crit, status, just in entries:
        fields.append((f"project[{crit}_status]", status))
        if just:
            fields.append((f"project[{crit}_justification]", just))
    data = urllib.parse.urlencode(fields).encode("utf-8")
    print(f"  [{group_name}] {len(entries)} criteria, {len(data)} bytes payload")
    return data


def _cache_busted_status_map() -> dict[str, str]:
    """Fetch project JSON with a cache-buster; retry briefly on CDN lag."""
    last: dict[str, str] = {}
    for attempt in range(4):
        status, _, body, _ = fetch(
            urllib.request.build_opener(), f"{JSON_URL}?cb={int(time.time() * 1000)}-{attempt}"
        )
        if status == 200:
            last = json.loads(body)
            return last
        time.sleep(1.5)
    return last


def apply_group(opener: urllib.request.OpenerDirector, csrf: str, group_name: str,
                entries: list[tuple[str, str, str]]) -> tuple[int, list[str], str]:
    """Send one chunk, then verify against the project JSON.

    BadgeApp's #update renders the edit page with HTTP 200 BOTH on success and
    on some validation failures, so the response code alone proves nothing.
    The authoritative check is the stored status map from /projects/:id.json.
    Returns (landed_count, missing_criteria, possibly_refreshed_csrf).
    """
    data = encode_form(group_name, entries, csrf)
    # IMPORTANT: the sectioned edit URL is the real update endpoint
    # (routes.rb: match 'projects/:id/:section/edit' => 'projects#update').
    # PATCHing /projects/:id instead renders/silently discards the change.
    status, final, body, _ = fetch(
        opener, EDIT_URL, data=data,
        method="PATCH",
        headers={
            "Content-Type": "application/x-www-form-urlencoded",
            "X-CSRF-Token": csrf,
            "Referer": EDIT_URL,
            "User-Agent": "vivid-badge-fill/1.0 (local script)",
        },
    )
    if status == 404:
        sys.exit(f"[{group_name}] HTTP 404 - PATCH route not reached. This is a script bug "
                 "(wrong method/URL); please report it.")
    if status >= 500:
        sys.exit(f"[{group_name}] HTTP {status} server error - aborting.")
    if status in (401, 403) or (status in (301, 302, 303) and "/login" in (final.path or "")):
        sys.exit(f"[{group_name}] HTTP {status} -> session expired. Re-export the cookie and re-run "
                 "(already-saved chunks are simply re-sent, which is harmless).")
    if status == 409:
        print(f"  [{group_name}] HTTP 409 conflict (stale lock/repo_url guard) - skip.", file=sys.stderr)
        return 0, [c for c, _, _ in entries], csrf

    # Refresh CSRF token if the response carries a fresh one.
    new_csrf = csrf
    m = re.search(r'<meta name="csrf-token" content="([^"]+)"', body)
    if m and m.group(1) != csrf:
        new_csrf = m.group(1)

    # Authoritative verification: what actually landed? (CDN lag tolerated
    # with a short retry loop; a criterion counts as landed when the stored
    # status equals what we sent.)
    landed, missing = 0, []
    for attempt in range(3):
        status_map = _cache_busted_status_map()
        landed, missing = 0, []
        for crit, want, _just in entries:
            got = status_map.get(f"{crit}_status")
            if got == want:
                landed += 1
            else:
                missing.append(f"{crit} (want {want}, have {got})")
        if not missing:
            break
        time.sleep(2)
    if missing:
        text = re.sub(r"<script.*?</script>", " ", body, flags=re.S)
        text = re.sub(r"<[^>]+>", " ", text)
        text = re.sub(r"\s+", " ", text)
        err = re.search(r"(\d+ errors? prohibited[^.]*\.(?:.{0,300}))", text)
        print(f"  [{group_name}] {landed}/{len(entries)} landed. Missing: {'; '.join(missing)}",
              file=sys.stderr)
        if err:
            print("  Server said:", err.group(1)[:300], file=sys.stderr)
    else:
        print(f"  [{group_name}] OK - all {landed}/{len(entries)} verified via API.")
    return landed, missing, new_csrf


def verify() -> int:
    status, _, body, _ = fetch(urllib.request.build_opener(), JSON_URL)
    if status != 200:
        print(f"JSON endpoint returned HTTP {status}", file=sys.stderr)
        return 1
    d = json.loads(body)
    pct1 = d.get("badge_percentage_1")
    print(f"badge_level          = {d.get('badge_level')}")
    print(f"badge_percentage_0   = {d.get('badge_percentage_0')} (passing)")
    print(f"badge_percentage_1   = {pct1} (silver)")
    print(f"tiered_percentage    = {d.get('tiered_percentage')}")
    print(f"updated_at           = {d.get('updated_at')}")
    open_items = sorted(k[:-7] for k, v in d.items()
                        if k.endswith("_status") and v == "?" and k.startswith(
                            tuple(f"{c}_" for c in (
                                "governance", "code_of_conduct", "roles_responsibilities",
                                "access_continuity", "documentation_", "sites_password_security",
                                "maintenance_or_update", "vulnerability_report_credit",
                                "vulnerability_response_process", "coding_standards",
                                "build_", "installation_", "external_dependencies",
                                "dependency_monitoring", "updateable_reused_components",
                                "automated_integration_testing", "regression_tests_added50",
                                "test_statement_coverage80", "test_policy_mandated",
                                "implement_secure_design", "crypto_credential_agility",
                                "crypto_certificate_verification", "crypto_verification_private",
                                "signed_releases", "input_validation", "assurance_case",
                                "dco", "bus_factor", "accessibility_best_practices",
                                "internationalization", "build_preserve_debug",
                                "interfaces_current", "crypto_algorithm_agility",
                                "crypto_used_network", "crypto_tls12", "version_tags_signed",
                                "hardening", "report_tracker", "tests_documented_added",
                                "warnings_strict", "crypto_weaknesses",
                                "dynamic_analysis_unsafe"))))
    if open_items:
        print(f"\nStill unanswered among silver targets ({len(open_items)}):")
        for item in open_items:
            print("  -", item)
    if pct1 == 100:
        print("\nSILVER ACHIEVED.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true", help="print payloads without sending")
    parser.add_argument("--verify", action="store_true", help="only fetch JSON and report status")
    args = parser.parse_args()

    if args.verify:
        return verify()

    total = sum(len(g) for _, g in ALL_GROUPS)
    print(f"Prepared {total} criteria answers in {len(ALL_GROUPS)} chunks "
          f"(project {PROJECT_ID}, silver level).")
    if args.dry_run:
        for name, entries in ALL_GROUPS:
            encode_form(name, entries, csrf="<csrf-token>")
        print("\nDry run complete. Nothing was sent.")
        return 0

    if not COOKIE_FILE.exists():
        sys.exit(f"Missing {COOKIE_FILE}\n"
                 "Create it with the value of the `_BadgeApp_session` cookie from your "
                 "logged-in browser (see module docstring / README in script header).")
    cookie_value = COOKIE_FILE.read_text(encoding="utf-8").strip()
    if not cookie_value:
        sys.exit(f"{COOKIE_FILE} is empty.")
    opener = build_opener_with_cookie(cookie_value)

    print("Fetching CSRF token from edit page ...")
    csrf = get_csrf_token(opener)
    print(f"CSRF token acquired ({len(csrf)} chars).")

    failed: list[str] = []
    landed_total = 0
    for name, entries in ALL_GROUPS:
        landed, missing, csrf = apply_group(opener, csrf, name, entries)
        landed_total += landed
        if missing:
            failed.extend(m.split(" ")[0] for m in missing)
    print(f"\nTotal verified landed: {landed_total}/{sum(len(g) for _, g in ALL_GROUPS)}")
    if failed:
        print(f"Criteria that did not land: {', '.join(sorted(set(failed)))}", file=sys.stderr)
        print("Fix those in the web editor, then re-run this script (harmless to re-send).")
        return 1
    return verify()


if __name__ == "__main__":
    sys.exit(main())
