#!/usr/bin/env bash
# Wiki-Sync: generiert die GitHub-Wiki-Startseite aus der Source of Truth im Repo.
#
# Die Befehls-Tabelle wird aus docs/user-guide.md (DE, der gepflegten
# Referenz) extrahiert — dieselbe Tabelle, die der CI-Guard
# check_bot_commands_doc.sh gegen den BotCommandProcessor-Code prüft.
# Dadurch kann das Wiki nicht mehr veralten: Sobald sich docs/user-guide.md
# oder der Bot-Code ändert, generiert der Workflow (automation-wiki-sync.yml)
# Home.md neu und pusht es nach thoser666/Vivid.wiki.git.
#
# Modi:
#   scripts/sync_wiki.sh --generate <zielverzeichnis>   Home.md generieren
#   scripts/sync_wiki.sh --push                          generieren + pushen (CI)
#   scripts/sync_wiki.sh --check                         generieren + vergleichen
#                                                        (Exit 1 = Wiki veraltet)
set -euo pipefail

GUIDE_DE="docs/user-guide.md"
REPO_URL="https://github.com/thoser666/Vivid"
WIKI_URL="https://github.com/thoser666/Vivid.wiki.git"

mode="${1:---check}"

# 1) Befehls-Tabelle aus dem DE-Handbuch extrahieren (Quick-Reference-Sektion).
if [[ ! -f "$GUIDE_DE" ]]; then
  echo "❌ [wiki-sync] $GUIDE_DE fehlt — Guard-Setup kaputt."
  exit 1
fi
TABLE=$(sed -n '/^## Quick-Reference: Alle Bot-Befehle/,/^> \*\*PREFIX-Scope\*\*/p' "$GUIDE_DE" \
  | sed '$d')
if ! grep -q '!tts' <<<"$TABLE"; then
  echo "❌ [wiki-sync] Extraktion kaputt — Tabelle ohne Befehle."
  exit 1
fi

# 2) Home.md generieren.
HOME_MD=$(cat <<EOF
<!-- 🤖 AUTO-GENERIERT von scripts/sync_wiki.sh — NICHT im Wiki editieren.
     Änderungen bitte in docs/user-guide.md (Source of Truth) machen. -->

# Vivid Wiki 📚

**Mobile IRL-Streaming-App** — Android-Umsetzung von [Moblin](https://github.com/eerimoq/moblin).

Die vollständige Dokumentation lebt im Repo und wird hierher gespiegelt:

| Sprache | Handbuch |
|---|---|
| 🇩🇪 Deutsch | [docs/user-guide.md](${REPO_URL}/blob/develop/docs/user-guide.md) |
| 🇬🇧 English | [docs/user-guide.en.md](${REPO_URL}/blob/develop/docs/user-guide.en.md) |
| 🇫🇷 Français | [docs/user-guide.fr.md](${REPO_URL}/blob/develop/docs/user-guide.fr.md) |
| 🤖 Bot | [docs/ai-chat-bot.md](${REPO_URL}/blob/develop/docs/ai-chat-bot.md) |

Weiteres: [README](${REPO_URL}#readme) · [Tutorials](${REPO_URL}/tree/develop/docs/tutorials) · [FAQ](${REPO_URL}/blob/develop/docs/faq/common-issues.md) · [Troubleshooting](${REPO_URL}/tree/develop/docs/troubleshooting) · [Releases](${REPO_URL}/releases) · [Issues](${REPO_URL}/issues)

---

${TABLE}

---

> 🔄 **Dieser Wiki-Inhalt wird automatisch synchronisiert** (Workflow
> \`automation-wiki-sync.yml\`): Bei jeder Änderung an den Handbüchern oder am
> Bot-Code wird diese Seite aus dem Repo neu generiert. Der CI-Guard
> \`check_bot_commands_doc.sh\` stellt sicher, dass jeder Bot-Befehl aus dem
> Code in allen drei Handbuch-Sprachen dokumentiert ist — das Wiki kann also
> nicht veralten. **Direkte Wiki-Edits werden beim nächsten Sync überschrieben.**
EOF
)

case "$mode" in
  --generate)
    [[ -n "${2:-}" ]] || { echo "usage: sync_wiki.sh --generate <dir>"; exit 2; }
    mkdir -p "$2"
    printf '%s\n' "$HOME_MD" > "$2/Home.md"
    echo "✅ [wiki-sync] Home.md nach $2/Home.md generiert."
    ;;
  --check)
    TMP=$(mktemp -d)
    trap 'rm -rf "$TMP"' EXIT
    git clone --depth 1 "$WIKI_URL" "$TMP/wiki" >/dev/null 2>&1
    printf '%s\n' "$HOME_MD" | tr -d '\r' > "$TMP/expected-Home.md"
    # Zeilenenden normalisieren (Windows-Checkouts/autocrlf liefern CRLF).
    tr -d '\r' < "$TMP/wiki/Home.md" > "$TMP/wiki-Home.norm.md"
    if diff -q "$TMP/expected-Home.md" "$TMP/wiki-Home.norm.md" >/dev/null 2>&1; then
      echo "✅ [wiki-sync] Wiki-Home ist aktuell."
    else
      echo "❌ [wiki-sync] Wiki-Home ist veraltet (kein Match mit generierter Version)."
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
    printf '%s\n' "$HOME_MD" | tr -d '\r' > "$TMP/wiki/Home.md"
    cd "$TMP/wiki"
    if git diff --quiet Home.md; then
      echo "✅ [wiki-sync] Wiki-Home unverändert — nichts zu pushen."
      exit 0
    fi
    git config user.name "github-actions[bot]"
    git config user.email "github-actions[bot]@users.noreply.github.com"
    git add Home.md
    git commit -m "Wiki-Home aus Repo-Doku neu generiert (auto-sync)"
    git push origin master >/dev/null 2>&1
    echo "✅ [wiki-sync] Wiki-Home gepusht."
    ;;
  *)
    echo "usage: sync_wiki.sh --generate <dir> | --check | --push"; exit 2
    ;;
esac
