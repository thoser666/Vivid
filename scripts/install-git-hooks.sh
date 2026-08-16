#!/usr/bin/env bash
# Installiert das Vivid-Pre-Push-Gate als Git-Hook.
# Git-Hooks liegen in .git/ und sind nicht versioniert — dieser Installer schon.
#
#   bash scripts/install-git-hooks.sh
set -euo pipefail

cd "$(dirname "$0")/.."

GIT_DIR="$(git rev-parse --git-dir)"
HOOK_FILE="$GIT_DIR/hooks/pre-push"
SCRIPT_PATH="$(pwd)/scripts/pre-push.sh"

if [[ -f "$HOOK_FILE" ]] && ! grep -q "scripts/pre-push.sh" "$HOOK_FILE" 2>/dev/null; then
  echo "⚠️  $HOOK_FILE existiert bereits und gehört nicht zu Vivid."
  read -r -p "Überschreiben? (y/N) " answer
  if [[ "${answer,,}" != "y" ]]; then
    echo "Abgebrochen — Hook nicht installiert."
    exit 1
  fi
fi

cat > "$HOOK_FILE" <<EOF
#!/usr/bin/env bash
# Vivid Pre-Push-Gate — installiert via scripts/install-git-hooks.sh
exec bash "$SCRIPT_PATH"
EOF
chmod +x "$HOOK_FILE"

echo "✅ Pre-Push-Hook installiert: $HOOK_FILE"
echo "   Jeder \`git push\` läuft jetzt lokal: Unit-Tests + Lint + Secret-Guard."
echo "   Optional zusätzlich Release-Build (R8/ProGuard): PRE_PUSH_RELEASE=1 git push"
echo "   Umgehen nur bewusst: \`git push --no-verify\`."
