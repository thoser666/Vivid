#!/usr/bin/env bash
# ⚠️ Dieses Skript pflegt CHANGELOG.md automatisch aus den GitHub-Releases.
# Aufgerufen wird es vom Workflow .github/workflows/automation-changelog.yml
# (Trigger: release:published + workflow_dispatch).
#
# Verhalten:
#   - Liest alle Nicht-Draft-Releases (stable, alpha, nightly) via gh API
#   - Hängt NEUE Releases oben in den generierten Block ein
#     (<!-- CHANGELOG-START --> … <!-- CHANGELOG-END -->)
#   - Bereits vorhandene Einträge bleiben unangetastet — auch ältere
#     Nightlies, die nach dem Cleanup (nur letzte 3) nicht mehr auf GitHub
#     existieren, bleiben so in der Historie erhalten
#   - Exit-Code 0 bei Erfolg; der Workflow committet nur bei Änderungen
#
# Lokal testen: GH_TOKEN setzen, dann  scripts/update_changelog.sh  ausführen.
set -euo pipefail

REPO="${GITHUB_REPOSITORY:-thoser666/Vivid}"
FILE="${1:-CHANGELOG.md}"
START_MARKER="<!-- CHANGELOG-START -->"
END_MARKER="<!-- CHANGELOG-END -->"

if [ ! -f "$FILE" ]; then
  echo "::error::$FILE fehlt — initiale Datei mit Markern anlegen" >&2
  exit 1
fi
if ! grep -qF "$START_MARKER" "$FILE" || ! grep -qF "$END_MARKER" "$FILE"; then
  echo "::error::Marker ($START_MARKER / $END_MARKER) fehlen in $FILE" >&2
  exit 1
fi

echo "📡 Spiegele GitHub-Releases ($REPO) nach $FILE …"

# --- 1) Alle Releases holen (neueste zuerst, keine Drafts) ---
# Hinweis: `body` liefert gh release list je nach CLI-Version nicht mit —
# der Body wird deshalb pro neuem Release per gh release view geholt.
releases_json="$(gh release list --repo "$REPO" --limit 100 \
  --json tagName,name,isDraft,publishedAt \
  --jq '[ .[] | select(.isDraft | not) ] | sort_by(.publishedAt) | reverse')"

# --- 2) Bereits dokumentierte Tags aus dem generierten Block lesen ---
# `|| true`: bei leerem Block liefert grep Exit 1 (keine Treffer) — das darf
# das Skript nicht abbrechen lassen (set -e).
existing="$(sed -n "/$START_MARKER/,/$END_MARKER/p" "$FILE" \
  | grep -oE 'releases/tag/[^)]+' | sed 's#releases/tag/##' | sort -u || true)"

# --- 3) Neue Releases als Markdown formen (neueste zuerst) ---
new_entries=""
count=0
while read -r tag; do
  # Schon dokumentiert → überspringen (kein Duplikat, Historie bleibt)
  if printf '%s\n' "$existing" | grep -qxF "$tag"; then
    continue
  fi

  # gh's eingebettetes jq (kein externes jq nötig)
  name="$(gh release view "$tag" --repo "$REPO" --json name --jq '.name')"
  published_at="$(gh release view "$tag" --repo "$REPO" --json publishedAt --jq '.publishedAt')"
  body="$(gh release view "$tag" --repo "$REPO" --json body --jq '.body // ""')"

  # Kanal-Badge aus dem Tag ableiten (robuster als isPrerelease-Flag:
  # das v0.2.0-alpha-Release wurde z. B. ohne prerelease-Flag publiziert)
  case "$tag" in
    nightly-*) badge="🌙 **Nightly**" ;;
    *-alpha)   badge="🧪 **Alpha**" ;;
    *-beta)    badge="🟡 **Beta**" ;;
    *-rc)      badge="🔶 **RC**" ;;
    *)         badge="🚀 **Stable**" ;;
  esac

  # Version aus dem Release-Namen ziehen: "Vivid nightly (0.2.0-nightly.87)"
  # → "0.2.0-nightly.87", "Vivid v0.2.0-alpha" → "v0.2.0-alpha"
  version="$(printf '%s' "$name" | sed -n 's/.*(\([^)]*\)).*/\1/p')"
  if [ -z "$version" ]; then
    version="$(printf '%s' "$name" | sed -n 's/^Vivid *//p')"
  fi
  [ -z "$version" ] && version="$tag"

  date="$(date -u -d "$published_at" +%Y-%m-%d)"

  entry="## $badge $version — $date"
  entry+=$'\n\n[GitHub-Release](https://github.com/'"$REPO"$'/releases/tag/'"$tag"')'
  if [ -n "$body" ]; then
    entry+=$'\n\n'"$body"
  fi
  case "$tag" in
    nightly-*) entry+=$'\n\n**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`' ;;
  esac

  if [ -z "$new_entries" ]; then
    new_entries="$entry"
  else
    new_entries+=$'\n\n'"$entry"
  fi
  count=$((count + 1))
  echo "  ➕ $tag ($version)"
done < <(echo "$releases_json" | grep -o '"tagName":"[^"]*"' | sed 's/"tagName":"//;s/"$//')

if [ "$count" -eq 0 ]; then
  echo "✅ CHANGELOG.md bereits aktuell — keine neuen Releases"
  exit 0
fi

# --- 4) Neue Einträge oben in den generierten Block einfügen ---
head_part="$(sed -n "1,/$START_MARKER/p" "$FILE")"                 # inkl. START-Marker
tail_part="$(sed -n "/$END_MARKER/,\$p" "$FILE")"                  # inkl. END-Marker
old_between="$(sed -n "/$START_MARKER/,/$END_MARKER/p" "$FILE" | sed '1d;$d')"

{
  printf '%s\n' "$head_part"
  printf '%s\n' "$new_entries"
  if [ -n "$old_between" ]; then
    printf '\n%s\n' "$old_between"
  fi
  printf '%s\n' "$tail_part"
} > "$FILE.tmp"
mv "$FILE.tmp" "$FILE"

echo "✅ $count neue Einträge in CHANGELOG.md eingefügt"
