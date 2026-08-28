#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# generate_status_dashboard.sh
# Generiert .freebuff/vivid-status.html aus Live-Daten.
# Aufruf: bash scripts/generate_status_dashboard.sh
#          oder: make dashboard   (falls Makefile vorhanden)
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$REPO/.freebuff/vivid-status.html"
CATALOG="$REPO/gradle/libs.versions.toml"
NOW="$(date -u '+%Y-%m-%d %H:%M UTC')"
DATE_ONLY="$(date -u '+%Y-%m-%d')"

# ── 1) Dynamische Daten sammeln ──────────────────────────────────────────────

# Git-Daten
BRANCH="$(git -C "$REPO" branch --show-current 2>/dev/null || echo 'unknown')"
LAST_COMMIT="$(git -C "$REPO" log -1 --format='%h' 2>/dev/null || echo '—')"
LAST_MSG="$(git -C "$REPO" log -1 --format='%s' 2>/dev/null | head -c 120 || echo '—')"
TOTAL_COMMITS="$(git -C "$REPO" rev-list --count HEAD 2>/dev/null || echo '0')"
UNCOMMITTED="$(git -C "$REPO" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
UNTRACKED="$(git -C "$REPO" ls-files --others --exclude-standard 2>/dev/null | wc -l | tr -d ' ')"

# Version aus toml
APP_VERSION="$(grep -oP 'composeBom\s*=\s*"\K[^"]+' "$CATALOG" 2>/dev/null || echo '—')"
AGP_VERSION="$(grep -oP '^agp\s*=\s*"\K[^"]+' "$CATALOG" 2>/dev/null || echo '—')"
KOTLIN_VERSION="$(grep -oP '^kotlin\s*=\s*"\K[^"]+' "$CATALOG" 2>/dev/null || echo '—')"

# Module + Kotlin-Dateien
declare -A MODULE_FILES
declare -A MODULE_STATUS
TOTAL_KOTLIN=0

for mod_dir in "$REPO"/*/src; do
  [ -d "$mod_dir" ] || continue
  mod_name="$(basename "$(dirname "$mod_dir")")"
  # Nur Gradle-Module (haben build.gradle.kts)
  [ -f "$REPO/$mod_name/build.gradle.kts" ] || continue

  kt_count="$(find "$REPO/$mod_name/src" -name '*.kt' 2>/dev/null | wc -l | tr -d ' ')"
  TOTAL_KOTLIN=$((TOTAL_KOTLIN + kt_count))

  # Status bestimmen
  if [ "$kt_count" -eq 0 ]; then
    status="placeholder"
    status_label="Platzhalter"
  elif [ "$kt_count" -le 3 ]; then
    status="wip"
    status_label="früh"
  elif [ "$kt_count" -le 10 ]; then
    status="wip"
    status_label="in Arbeit"
  else
    status="live"
    status_label="aktiv"
  fi

  MODULE_FILES["$mod_name"]="$kt_count"
  MODULE_STATUS["$mod_name"]="$status:$status_label"
done

# Module nach Dateianzahl sortieren (absteigend)
SORTED_MODULES=()
for mod in "${!MODULE_FILES[@]}"; do
  SORTED_MODULES+=("${MODULE_FILES[$mod]}|$mod")
done
IFS=$'\n' SORTED_MODULES=($(sort -t'|' -k1 -rn <<<"${SORTED_MODULES[*]}")); unset IFS

# Max-Dateianzahl für Balken-Berechnung
MAX_FILES=1
for entry in "${SORTED_MODULES[@]}"; do
  count="${entry%%|*}"
  [ "$count" -gt "$MAX_FILES" ] && MAX_FILES="$count"
done

# ── 2) HTML generieren ──────────────────────────────────────────────────────

cat > "$OUT" <<'HEADER'
<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Vivid — Projektstatus</title>
<style>
  :root {
    --bg: #0b0f1a; --bg2: #101726; --card: #151d30; --card2: #1b2438;
    --border: #24304d; --text: #e6ecf7; --muted: #8b98b3;
    --accent: #3ddc84; --accent2: #4f8cff; --warn: #ffb84d; --danger: #ff5d6c; --ok: #3ddc84;
    --mono: ui-monospace, "Cascadia Code", Consolas, monospace;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: "Segoe UI", system-ui, -apple-system, sans-serif;
    background: radial-gradient(1000px 500px at 85% -10%, rgba(79,140,255,.12), transparent 60%),
      radial-gradient(800px 400px at -10% 20%, rgba(61,220,132,.08), transparent 60%), var(--bg);
    color: var(--text); min-height: 100vh; padding: 32px 20px 64px;
  }
  .wrap { max-width: 1080px; margin: 0 auto; }
  header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; flex-wrap: wrap; margin-bottom: 8px; }
  h1 { font-size: 30px; font-weight: 800; letter-spacing: -0.5px; }
  h1 .dot { color: var(--accent); }
  .sub { color: var(--muted); margin-top: 6px; font-size: 14px; }
  .meta { text-align: right; font-size: 12px; color: var(--muted); font-family: var(--mono); }
  .meta b { color: var(--accent); }
  .badges { display: flex; gap: 8px; flex-wrap: wrap; margin: 16px 0 28px; }
  .badge {
    font-size: 12px; font-weight: 600; padding: 6px 12px; border-radius: 999px;
    border: 1px solid var(--border); background: var(--card); color: var(--text); font-family: var(--mono);
  }
  .badge.green { border-color: rgba(61,220,132,.4); color: var(--ok); background: rgba(61,220,132,.08); }
  .badge.yellow { border-color: rgba(255,184,77,.4); color: var(--warn); background: rgba(255,184,77,.08); }
  .badge.blue { border-color: rgba(79,140,255,.4); color: var(--accent2); background: rgba(79,140,255,.08); }

  section { margin-bottom: 30px; }
  h2 {
    font-size: 13px; text-transform: uppercase; letter-spacing: 2px; color: var(--muted);
    margin-bottom: 14px; display: flex; align-items: center; gap: 10px;
  }
  h2::after { content: ""; flex: 1; height: 1px; background: var(--border); }

  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 14px; }
  .card {
    background: linear-gradient(180deg, var(--card), var(--card2));
    border: 1px solid var(--border); border-radius: 14px; padding: 18px;
    transition: transform .15s ease, border-color .15s ease;
  }
  .card:hover { transform: translateY(-2px); border-color: #33436b; }
  .card h3 { font-size: 15px; margin-bottom: 10px; display: flex; align-items: center; gap: 8px; }
  .card p, .card li { font-size: 13px; color: var(--muted); line-height: 1.6; }
  .card ul { list-style: none; }
  .card ul li { padding: 4px 0; border-bottom: 1px dashed rgba(36,48,77,.6); }
  .card ul li:last-child { border-bottom: none; }
  .card ul li b { color: var(--text); font-weight: 600; }
  .kv { display: grid; grid-template-columns: 110px 1fr; gap: 6px 12px; font-size: 13px; }
  .kv .k { color: var(--muted); }
  .kv .v { color: var(--text); font-family: var(--mono); font-size: 12.5px; word-break: break-word; }

  .module { display: flex; align-items: center; gap: 14px; padding: 12px 14px; border: 1px solid var(--border); border-radius: 12px; background: var(--card); margin-bottom: 8px; }
  .module .name { font-family: var(--mono); font-size: 13px; font-weight: 700; width: 180px; flex-shrink: 0; }
  .module .bar { flex: 1; height: 8px; border-radius: 999px; background: #0d1424; overflow: hidden; }
  .module .fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, var(--accent2), var(--accent)); }
  .module .fill.low { background: linear-gradient(90deg, var(--warn), var(--danger)); }
  .module .fill.mid { background: linear-gradient(90deg, var(--warn), var(--accent)); }
  .module .count { font-family: var(--mono); font-size: 12px; color: var(--muted); min-width: 120px; text-align: right; }
  .module .tag { font-size: 10.5px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; padding: 3px 8px; border-radius: 6px; flex-shrink: 0; }
  .tag.placeholder { background: rgba(255,93,108,.12); color: var(--danger); }
  .tag.wip { background: rgba(255,184,77,.12); color: var(--warn); }
  .tag.live { background: rgba(61,220,132,.12); color: var(--ok); }

  .gitstat { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 14px; }
  .stat { text-align: center; padding: 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--card); }
  .stat .num { font-size: 28px; font-weight: 800; font-family: var(--mono); }
  .stat .num.green { color: var(--ok); }
  .stat .num.yellow { color: var(--warn); }
  .stat .num.red { color: var(--danger); }
  .stat .lbl { font-size: 12px; color: var(--muted); margin-top: 6px; }

  footer { text-align: center; font-size: 12px; color: var(--muted); margin-top: 40px; padding-top: 20px; border-top: 1px solid var(--border); }
</style>
</head>
<body>
<div class="wrap">
HEADER

# ── Header + Badges ──────────────────────────────────────────────────────────
cat >> "$OUT" <<EOF
<header>
  <div>
    <h1>Vivid<span class="dot">.</span></h1>
    <div class="sub">IRL-Streaming-App — Auto-generiertes Projekt-Dashboard</div>
  </div>
  <div class="meta">
    <div>Stand: <b>$NOW</b></div>
    <div>Branch: <b>$BRANCH</b></div>
    <div>Commit: <b>$LAST_COMMIT</b></div>
  </div>
</header>

<div class="badges">
  <span class="badge green">$TOTAL_COMMITS Commits</span>
  <span class="badge green">$TOTAL_KOTLIN Kotlin-Dateien</span>
  <span class="badge blue">AGP $AGP_VERSION</span>
  <span class="badge blue">Kotlin $KOTLIN_VERSION</span>
  <span class="badge blue">Compose BOM $APP_VERSION</span>
</div>
EOF

# ── Module ───────────────────────────────────────────────────────────────────
cat >> "$OUT" <<'EOF'
<section>
  <h2>Module &amp; Reifegrad</h2>
EOF

for entry in "${SORTED_MODULES[@]}"; do
  count="${entry%%|*}"
  mod="${entry#*|}"
  IFS=':' read -r status status_label <<< "${MODULE_STATUS[$mod]}"
  pct=$(( count * 100 / MAX_FILES ))
  [ "$pct" -lt 5 ] && pct=5

  # Fill-Klasse
  fill_class="fill"
  [ "$pct" -lt 25 ] && fill_class="fill low"
  [ "$pct" -ge 25 ] && [ "$pct" -lt 60 ] && fill_class="fill mid"

  # Beschreibung
  case "$mod" in
    core) desc="Basis" ;;
    app) desc="Einstieg" ;;
    feature-streaming) desc="Kamera/Stream" ;;
    feature-chat) desc="Chat/Bot" ;;
    feature-settings) desc="Einstellungen" ;;
    feature-obs-control) desc="OBS WS" ;;
    feature-widgets) desc="Widgets" ;;
    data) desc="Daten" ;;
    domain) desc="Domäne" ;;
    *) desc="" ;;
  esac

  count_text="$count Dateien"
  [ -n "$desc" ] && count_text="$count_text · $desc"

  cat >> "$OUT" <<EOF
  <div class="module">
    <span class="name">:$mod</span>
    <div class="bar"><div class="$fill_class" style="width:${pct}%"></div></div>
    <span class="count">$count_text</span>
    <span class="tag $status">$status_label</span>
  </div>
EOF
done

echo "</section>" >> "$OUT"

# ── Git-Zustand ──────────────────────────────────────────────────────────────
# Uncommitted-Farbe
uc_class="green"
[ "$UNCOMMITTED" -gt 0 ] && uc_class="yellow"
[ "$UNCOMMITTED" -gt 10 ] && uc_class="red"

cat >> "$OUT" <<EOF
<section>
  <h2>Git-Zustand</h2>
  <div class="gitstat">
    <div class="stat"><div class="num $uc_class">$UNCOMMITTED</div><div class="lbl">Uncommitted Dateien</div></div>
    <div class="stat"><div class="num yellow">$UNTRACKED</div><div class="lbl">Untracked Dateien</div></div>
    <div class="stat"><div class="num green">OK</div><div class="lbl">Letzter Commit: $LAST_COMMIT</div></div>
  </div>
  <div class="card" style="margin-top:14px">
    <h3>Letzter Commit</h3>
    <p><code>$LAST_COMMIT</code> — $LAST_MSG</p>
  </div>
</section>
EOF

# ── Footer ───────────────────────────────────────────────────────────────────
cat >> "$OUT" <<EOF
<footer>
  Vivid · Stand $DATE_ONLY · Automatisch generiert via scripts/generate_status_dashboard.sh
</footer>

</div>
</body>
</html>
EOF

echo "✅ Dashboard generiert: $OUT ($(wc -c < "$OUT") Bytes, $(wc -l < "$OUT") Zeilen)"
