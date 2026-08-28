# Renovate Setup für Vivid

## 📋 Übersicht

Renovate ist ein Dependency-Update-Bot, der automatisch PRs für veraltete Dependencies erstellt. Im Vergleich zu Dependabot bietet Renovate:

- **Dependency Dashboard** — eine Issue als Übersicht über alle Updates
- **Automatische Gruppierung** — zusammenhängende Updates in einem PR
- **Auto-Merge (Branch-Modus)** — spart CI-Zeit durch direktes Mergen
- **Merge Confidence Badges** — 4 Metriken für bessere Entscheidungen

## 🚀 Setup-Schritte

### Schritt 1: Mend Renovate App installieren

1. **GitHub Marketplace öffnen:** https://github.com/marketplace/renovate
2. **„Install" klicken** — App auf Repository thoser666/Vivid installieren
3. **Berechtigungen prüfen:**
   - ✅ Contents: Read & Write
   - ✅ Pull Requests: Read & Write
   - ✅ Issues: Read & Write
   - ✅ Metadata: Read-only

### Schritt 2: Onboarding-PR akzeptieren

Nach der Installation erstellt Renovate einen **Onboarding-PR** mit dem Titel „Configure Renovate". Dieser enthält:

- Vorgeschlagene `renovate.json` (wird von unserer Konfiguration überschrieben)
- Liste der erkannten Package-Dateien

**Wichtig:** Renovate tut nichts, bis dieser PR gemergt wird!

### Schritt 3: Eigene Konfiguration verwenden

Unsere `renovate.json` liegt bereits im Repo-Root. Nach dem Merge des Onboarding-PRs wird diese Konfiguration verwendet.

**Alternativ:** Die `renovate.json` kann auch nach dem Onboarding-PR manuell angepasst werden.

### Schritt 4: Dependabot deaktivieren (optional)

Falls Renovate als alleiniges Update-Tool verwendet werden soll:

```bash
# .github/dependabot.yml löschen oder umbenennen
rm .github/dependabot.yml
```

**Empfehlung:** Beide parallel lassen — Dependabot für SHA-Pins, Renovate für Dependencies.

## ⚙️ Konfiguration (renovate.json)

### Kern-Features

| Feature | Wert | Bedeutung |
|---------|------|-----------|
| **Schedule** | `every weekend` | PRs nur am Wochenende erstellen |
| **Timezone** | `Europe/Berlin` | Zeitzone für Schedule |
| **prConcurrentLimit** | `10` | Max. 10 offene PRs gleichzeitig |
| **prHourlyLimit** | `2` | Max. 2 PRs pro Stunde |

### Gruppierung (packageRules)

| Gruppe | Pattern | Automerge |
|--------|---------|-----------|
| **Android Gradle Plugin** | `com.android.application`, `com.android.library` | ✅ Branch |
| **Kotlin Dependencies** | `org.jetbrains.kotlin*` | ✅ Branch |
| **Compose Dependencies** | `androidx.compose*` | ✅ Branch |
| **AndroidX Core** | `androidx.*` (ohne Compose) | ✅ Branch |
| **Hilt Dependencies** | `com.google.dagger*` | ✅ Branch |
| **Ktor Dependencies** | `io.ktor*` | ❌ Manuell |
| **Testing Dependencies** | `junit*`, `mockk*`, `mockito*`, `espresso*` | ✅ Branch |
| **RootEncoder Dependencies** | `com.github.pedroSG94*` | ❌ Manuell |

### Auto-Merge (Branch-Modus)

Für gruppierte Updates wird `automergeType: "branch"` verwendet:

1. Renovate erstellt einen Branch
2. CI läuft (Tests, Lint)
3. Bei grünem CI: Branch wird direkt gemergt (kein PR nötig)
4. Bei rotem CI: PR wird erstellt für manuellen Review

**Vorteil:** Kein PR-Overhead für sichere Updates.

### Ignorierte Dependencies

```json
"ignoreDeps": [
  "com.android.tools.build:gradle"
]
```

AGP wird ignoriert, da es spezielle Kompatibilitäten mit Kotlin/Compose erfordert.

## 🔄 Vergleich: Dependabot vs. Renovate

### PR-Aufwand

| Szenario | Dependabot | Renovate |
|----------|------------|----------|
| **Kotlin Update** | 1 PR | 1 PR (gruppiert) |
| **Compose BOM** | 5-10 PRs | 1 PR (gruppiert) |
| **AndroidX Update** | 8-15 PRs | 1-2 PRs (gruppiert) |
| **Gesamt pro Woche** | 15-30 PRs | 3-5 PRs |

### CI-Zeit

| Szenario | Dependabot | Renovate |
|----------|------------|----------|
| **Tests pro PR** | ~5 Min | ~5 Min |
| **PRs pro Woche** | 15-30 | 3-5 |
| **Gesamt CI-Zeit** | 75-150 Min | 15-25 Min |
| **Auto-Merge (Branch)** | ❌ | ✅ Kein PR nötig |

### Features

| Feature | Dependabot | Renovate |
|---------|------------|----------|
| **Dependency Dashboard** | ❌ | ✅ |
| **Merge Confidence** | 1 Badge | 4 Badges |
| **Auto-Merge (Branch)** | ❌ | ✅ |
| **Monorepo-Presets** | ❌ | ✅ |
| **Human-readable Schedule** | ❌ | ✅ |

## 📊 Dependency Dashboard

Nach der Installation erstellt Renovate eine **Dependency Dashboard Issue**:

```
## Dependency Dashboard

This issue lists Renovate updates and detected dependencies. Details...

###pending

 - [ ] Update all non-major dependencies (minor + patch)
   ☰ Update Android Gradle Plugin to v9.3.2
   ☰ Update Kotlin Dependencies to v2.2.21
   ☰ Update Compose Dependencies to v2025.10.00
   ...
```

**Vorteil:** Ein einziger Ort für alle Updates — kein莱姆licher PR-Stream.

## 🔒 Sicherheit

### Vulnerability Alerts

```json
"vulnerabilityAlerts": {
  "enabled": true,
  "labels": ["security"]
}
```

Renovate erstellt automatisch PRs für Sicherheitslücken (CVEs).

### Branch Protection

Renovate-PRs respektieren Branch-Protection-Regeln:
- ✅ CI muss grün sein
- ✅ Code-Review erforderlich (falls aktiviert)
- ✅ Status-Checks müssen bestehen

## 🛠️ Troubleshooting

### Renovate erstellt keine PRs

**Ursachen:**
1. Onboarding-PR nicht gemergt
2. `renovate.json` hat Syntaxfehler
3. Schedule erlaubt keine PRs (z. B. Feiertage)

**Lösung:**
```bash
# Syntax prüfen
cat renovate.json | jq .

# Onboarding-PR prüfen
gh pr list --search "Configure Renovate"
```

### PRs werden nicht automatisch gemergt

**Ursachen:**
1. CI ist rot
2. `automergeType: "branch"` erfordert grüne CI
3. Branch-Protection-Regeln blockieren

**Lösung:**
- PR manuell prüfen und mergen
- CI-Probleme beheben

### Zu viele PRs

**Lösung:**
```json
"prConcurrentLimit": 5,
"prHourlyLimit": 1
```

## 📚 Weiterführende Links

- [Renovate Docs](https://docs.renovatebot.com/)
- [Renovate for Android](https://proandroiddev.com/keep-your-android-project-up-to-date-with-renovate-bot)
- [Dependency Dashboard](https://docs.renovatebot.com/key-concepts/dashboard/)
- [Auto-Merge](https://docs.renovatebot.com/key-concepts/automerge/)
