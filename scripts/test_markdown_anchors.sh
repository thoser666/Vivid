#!/usr/bin/env bash
# Selbsttest für scripts/check_markdown_anchors.sh:
#   baut ein Wegwerf-Markdown-Fixture (TMP) mit echten GitHub-Anker-Headern
#   (Emoji, Klammern, Umlaute, Markdown-Formatierung) und prüft:
#     Positiv   : alle Anker gültig (inkl. Formatierungs-/Emoji-Header) → Exit 0
#     Negativ 1 : toter Anker in derselben Datei      → Exit 1
#     Negativ 2 : toter Anker in einer anderen Datei  → Exit 1
#     Negativ 3 : Ziel-Datei fehlt                     → Exit 1
#     Negativ 4 : Anker mit #-Präfix-Datei (file.md#) — Datei-Check greift
# Läuft im CI (android.yml) und lokal: bash scripts/test_markdown_anchors.sh
set -euo pipefail

cd "$(dirname "$0")/.."
CHECK="bash scripts/check_markdown_anchors.sh"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() { echo "❌ FAIL: $1"; exit 1; }
expect_exit() { # $1 = erwarteter Exit, $2 = Beschreibung
  local expected="$1" desc="$2" rc=0
  set +e
  $CHECK "$TMP" >/dev/null 2>&1
  rc=$?
  set -e
  if [ "$rc" -ne "$expected" ]; then
    fail "$desc (Exit $rc statt $expected)"
  fi
  echo "✅ $desc (Exit $expected)"
}

# ── Fixture: a.md verlinkt auf b.md + sich selbst ───────────────────────────
build_fixture() { # $1 = Inhalt von a.md
  cat > "$TMP/a.md" <<EOF
# Fixture A

## 🧪 Erster Beta-Build (Plan)

## 🎨 Roadmap-Bucket: Color-Spaces + 3D-LUTs

## 💬 Roadmap-Bucket: Multi-Plattform-Chat (Kick, YouTube, SOOP)

## **Fett** und \`Code\` Header

$1
EOF
  cat > "$TMP/b.md" <<'EOF'
# Fixture B

## 🧪 Erster Beta-Build (Plan)

## ✅ Play-Vorbereitung: Priorisierte Abhakliste (mit Zeitaufwand)
EOF
}

# ── Positiv: alle Anker gültig (GitHub-Algorithmus: Emoji→'-', Umlaute bleiben) ──
build_fixture '[ok](#-erster-beta-build-plan)
[ok2](b.md#-erster-beta-build-plan)
[ok3](b.md#-play-vorbereitung-priorisierte-abhakliste-mit-zeitaufwand)
[ok4](#-roadmap-bucket-color-spaces--3d-luts)
[ok5](#-roadmap-bucket-multi-plattform-chat-kick-youtube-soop)
[ok6](#fett-und-code-header)
[file](b.md)
[external](https://example.com/x#y)
[nonmd](../icon.png)'
expect_exit 0 "gültige Anker + externe/non-md-Links ignoriert"

# ── Negativ 1: toter Anker in derselben Datei ───────────────────────────────
build_fixture '[bad](#-gibts-nicht)'
expect_exit 1 "toter Anker in derselben Datei erkannt"

# ── Negativ 2: toter Anker in anderer Datei ─────────────────────────────────
build_fixture '[bad2](b.md#-falscher-anchor)'
expect_exit 1 "toter Anker in anderer Datei erkannt"

# ── Negativ 3: Ziel-Datei fehlt ─────────────────────────────────────────────
build_fixture '[missing](c.md#-x)'
expect_exit 1 "fehlende Ziel-Datei erkannt"

# ── Negativ 4: Ziel-Datei fehlt (ohne Anker) ────────────────────────────────
build_fixture '[missing2](c.md)'
expect_exit 1 "fehlende Ziel-Datei ohne Anker erkannt"

# ── Positiv 2: Anker mit Umlaut-Header ──────────────────────────────────────
cat > "$TMP/a.md" <<'EOF'
# Fixture A

## 🧪 Übersicht

[umlaut](#-übersicht)
EOF
expect_exit 0 "Umlaut-Anker korrekt berechnet"

echo "✅ check_markdown_anchors.sh: alle Fälle bestanden (2 Positiv, 4 Negativ)."
