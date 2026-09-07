#!/usr/bin/env bash
# Wiki-Sync: generiert die GitHub-Wiki-Seiten aus der Source of Truth im Repo.
#
# Generierte Seiten:
#   - Home.md           Startseite mit der DE-Quick-Reference (aus docs/user-guide.md)
#   - User-Guide-EN.md  vollständiger Mirror von docs/user-guide.en.md
#   - User-Guide-FR.md  vollständiger Mirror von docs/user-guide.fr.md
#
# Die Befehls-Tabelle wird aus docs/user-guide.md (DE, der gepflegten
# Referenz) extrahiert — dieselbe Tabelle, die der CI-Guard
# check_bot_commands_doc.sh gegen den BotCommandProcessor-Code prüft.
# Dadurch kann das Wiki nicht mehr veralten: Sobald sich die Handbücher
# oder der Bot-Code ändern, generiert der Workflow (automation-wiki-sync.yml)
# alle Seiten neu und pusht sie nach thoser666/Vivid.wiki.git.
#
# Modi:
#   scripts/sync_wiki.sh --generate <zielverzeichnis>   alle Seiten generieren
#   scripts/sync_wiki.sh --push                          generieren + pushen (CI)
#   scripts/sync_wiki.sh --check                         generieren + vergleichen
#                                                        (Exit 1 = Wiki veraltet)
set -euo pipefail

GUIDE_DE="docs/user-guide.md"
GUIDE_EN="docs/user-guide.en.md"
GUIDE_FR="docs/user-guide.fr.md"
REPO_URL="https://github.com/thoser666/Vivid"
WIKI_URL="https://github.com/thoser666/Vivid.wiki.git"

mode="${1:---check}"

# ── 1) Quellen laden (CRLF normalisieren — Windows-Checkouts/autocrlf) ──────
for guide in "$GUIDE_DE" "$GUIDE_EN" "$GUIDE_FR"; do
  if [[ ! -f "$guide" ]]; then
    echo "❌ [wiki-sync] $guide fehlt — Guard-Setup kaputt."
    exit 1
  fi
done
GUIDE_DE_CONTENT=$(tr -d '\r' < "$GUIDE_DE")
GUIDE_EN_CONTENT=$(tr -d '\r' < "$GUIDE_EN")
GUIDE_FR_CONTENT=$(tr -d '\r' < "$GUIDE_FR")

# 2) Befehls-Tabelle aus dem DE-Handbuch extrahieren (Quick-Reference-Sektion).
TABLE=$(sed -n '/^## Quick-Reference: Alle Bot-Befehle/,/^> \*\*PREFIX-Scope\*\*/p' <<<"$GUIDE_DE_CONTENT" \
  | sed '$d')
if ! grep -q '!tts' <<<"$TABLE"; then
  echo "❌ [wiki-sync] Extraktion kaputt — Tabelle ohne Befehle."
  exit 1
fi

# 3) Home.md generieren.
HOME_MD=$(cat <<EOF
<!-- 🤖 AUTO-GENERIERT von scripts/sync_wiki.sh — NICHT im Wiki editieren.
     Änderungen bitte in docs/user-guide.md (Source of Truth) machen. -->

# Vivid Wiki 📚

**Mobile IRL-Streaming-App** — Android-Umsetzung von [Moblin](https://github.com/eerimoq/moblin).

Die vollständige Dokumentation lebt im Repo und wird hierher gespiegelt:

| Sprache | Handbuch (Repo) | Wiki-Seite |
|---|---|---|
| 🇩🇪 Deutsch | [docs/user-guide.md](${REPO_URL}/blob/develop/docs/user-guide.md) | Quick-Reference unten ↓ |
| 🇬🇧 English | [docs/user-guide.en.md](${REPO_URL}/blob/develop/docs/user-guide.en.md) | [User-Guide-EN](${WIKI_URL%.git}/User-Guide-EN) |
| 🇫🇷 Français | [docs/user-guide.fr.md](${REPO_URL}/blob/develop/docs/user-guide.fr.md) | [User-Guide-FR](${WIKI_URL%.git}/User-Guide-FR) |
| 🤖 Bot | [docs/ai-chat-bot.md](${REPO_URL}/blob/develop/docs/ai-chat-bot.md) | — |

Weiteres: [README](${REPO_URL}#readme) · [Tutorials](${REPO_URL}/tree/develop/docs/tutorials) · [FAQ](${REPO_URL}/blob/develop/docs/faq/common-issues.md) · [Troubleshooting](${REPO_URL}/tree/develop/docs/troubleshooting) · [Releases](${REPO_URL}/releases) · [Issues](${REPO_URL}/issues)

---

${TABLE}

---

> 🔄 **Dieser Wiki-Inhalt wird automatisch synchronisiert** (Workflow
> \`automation-wiki-sync.yml\`): Bei jeder Änderung an den Handbüchern oder am
> Bot-Code werden alle Seiten (Home, User-Guide-EN, User-Guide-FR) aus dem
> Repo neu generiert. Der CI-Guard \`check_bot_commands_doc.sh\` stellt sicher,
> dass jeder Bot-Befehl aus dem Code in allen drei Handbuch-Sprachen
> dokumentiert ist — das Wiki kann also nicht veralten. **Direkte Wiki-Edits
> werden beim nächsten Sync überschrieben.**
EOF
)

# 4) Sprach-Seiten generieren (Mirror des kompletten Handbuchs + Header).
PAGE_EN_MD=$(cat <<EOF
<!-- 🤖 AUTO-GENERIERT von scripts/sync_wiki.sh — NICHT im Wiki editieren.
     Änderungen bitte in docs/user-guide.en.md (Source of Truth) machen. -->

> 🪞 Mirror of [docs/user-guide.en.md](${REPO_URL}/blob/develop/docs/user-guide.en.md) — automatically synced.
> Edit the repo file, not this page.

${GUIDE_EN_CONTENT}
EOF
)

PAGE_FR_MD=$(cat <<EOF
<!-- 🤖 AUTO-GENERIERT par scripts/sync_wiki.sh — NE PAS modifier ici.
     Modifications dans docs/user-guide.fr.md (source de vérité). -->

> 🪞 Miroir de [docs/user-guide.fr.md](${REPO_URL}/blob/develop/docs/user-guide.fr.md) — synchronisé automatiquement.
> Modifier le fichier du dépôt, pas cette page.

${GUIDE_FR_CONTENT}
EOF
)

# Schreibt alle drei Seiten nach $1 (CRLF-normalisiert).
write_pages() {
  mkdir -p "$1"
  printf '%s\n' "$HOME_MD" | tr -d '\r' > "$1/Home.md"
  printf '%s\n' "$PAGE_EN_MD" | tr -d '\r' > "$1/User-Guide-EN.md"
  printf '%s\n' "$PAGE_FR_MD" | tr -d '\r' > "$1/User-Guide-FR.md"
}

case "$mode" in
  --generate)
    [[ -n "${2:-}" ]] || { echo "usage: sync_wiki.sh --generate <dir>"; exit 2; }
    write_pages "$2"
    echo "✅ [wiki-sync] Home.md, User-Guide-EN.md, User-Guide-FR.md nach $2 generiert."
    ;;
  --check)
    TMP=$(mktemp -d)
    trap 'rm -rf "$TMP"' EXIT
    git clone --depth 1 "$WIKI_URL" "$TMP/wiki" >/dev/null 2>&1
    write_pages "$TMP/expected"
    # Zeilenenden sind in write_pages bereits normalisiert.
    stale=0
    for page in Home.md User-Guide-EN.md User-Guide-FR.md; do
      if ! diff -q "$TMP/expected/$page" "$TMP/wiki/$page" >/dev/null 2>&1; then
        echo "❌ [wiki-sync] Wiki-Seite ist veraltet: $page"
        stale=1
      else
        echo "✅ [wiki-sync] Wiki-Seite ist aktuell: $page"
      fi
    done
    if [[ $stale -ne 0 ]]; then
      echo "   Fix: Workflow automation-wiki-sync.yml laufen lassen oder lokal: bash scripts/sync_wiki.sh --push"
      exit 1
    fi
    ;;
  --push)
    TMP=$(mktemp -d)
    trap 'rm -rf "$TMP"' EXIT
    TOKEN="${AUTOMATION_TOKEN:-${GH_TOKEN:-}}"
    [[ -n "$TOKEN" ]] || { echo "❌ [wiki-sync] Kein Token (AUTOMATION_TOKEN/GH_TOKEN) gesetzt."; exit 1; }
    git clone --depth 1 "https://x-access-token:${TOKEN}@${WIKI_URL#https://}" "$TMP/wiki" >/dev/null 2>&1
    write_pages "$TMP/wiki"
    cd "$TMP/wiki"
    if git diff --quiet; then
      echo "✅ [wiki-sync] Wiki-Seiten unverändert — nichts zu pushen."
      exit 0
    fi
    git config user.name "github-actions[bot]"
    git config user.email "github-actions[bot]@users.noreply.github.com"
    git add Home.md User-Guide-EN.md User-Guide-FR.md
    git commit -m "Wiki-Seiten aus Repo-Doku neu generiert (auto-sync: Home + EN + FR)"
    git push origin master >/dev/null 2>&1
    echo "✅ [wiki-sync] Wiki-Seiten gepusht (Home, User-Guide-EN, User-Guide-FR)."
    ;;
  *)
    echo "usage: sync_wiki.sh --generate <dir> | --check | --push"; exit 2
    ;;
esac
