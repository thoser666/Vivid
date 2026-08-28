# Dependabot vs. Renovate — Vergleich für Vivid

## 📊 Feature-Vergleich

| Feature | Dependabot | Renovate |
|---------|------------|----------|
| **GitHub-Integration** | ✅ Nativ (keine App nötig) | ✅ GitHub App (Installieren) |
| **Dependency Dashboard** | ❌ Nein | ✅ Ja (Übersichts-Issue) |
| **Grouped Updates** | ✅ Manuell konfigurierbar | ✅ Automatisch (Monorepo-Presets) |
| **Auto-Merge** | ❌ Nicht eingebaut | ✅ Ja (Branch-Modus möglich) |
| **Merge Confidence Badges** | 1 Badge (Gesamt-Score) | 4 Badges (Age, Adoption, Passing, Confidence) |
| **Changelog-Links** | ✅ Release-Seite | ✅ Spezifisches Tag |
| **Kompatibilitäts-Score** | ✅ Ja | ✅ Ja (4 Metriken) |
| **Schedule-Optionen** | daily/weekly/monthly/quarterly | Flexibler (Cron, human-readable) |
| **Plattformen** | GitHub, Azure DevOps | GitHub, GitLab, Bitbucket, Gitea, etc. |
| **Kosten** | Kostenlos | Kostenlos (Mend-App) |
| **Selbst-Hosting** | Möglich (dependabot-core) | Möglich (npm, Docker, Action) |

## 🎯 Empfehlung für Vivid

### Dependabot — ✅ Bereits aktiv

**Vorteile:**
- ✅ **Nativ in GitHub** — keine App-Installation nötig
- ✅ **SHA-Pins** — liest `# vX`-Kommentare automatisch
- ✅ **Einfach** — nur `dependabot.yml` nötig
- ✅ **Auto-Merge-Workflow** — bereits implementiert

**Nachteile:**
- ❌ Kein Dependency Dashboard
- ❌ Keine automatische Gruppierung
- ❌ Manuelles Konfigurieren von Gruppen nötig

### Renovate — ⚠️ Optionale Ergänzung

**Vorteile:**
- ✅ **Dependency Dashboard** — eine Issue als Übersicht
- ✅ **Automatische Gruppierung** — Kotlin, Compose, AndroidX in eigenen PRs
- ✅ **Auto-Merge (Branch-Modus)** — spart CI-Zeit
- ✅ **Merge Confidence Badges** — 4 Metriken statt 1
- ✅ **Flexibleres Schedule** — z. B. "every weekend", "after 10pm"

**Nachteile:**
- ❌ **App-Installation nötig** (Mend Renovate GitHub App)
- ❌ Mehr Konfiguration nötig
- ❌ Kann mehr PRs erzeugen (durch Gruppierung)

## 🛠️ Konkrete Konfiguration für Vivid

### Aktueller Dependabot-Stand

```yaml
# .github/dependabot.yml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
```

**Probleme:**
- Erzeugt **viele einzelne PRs** (pro Dependency)
- Keine Gruppierung (z. B. alle Compose-Updates separat)
- Kein Dependency Dashboard

### Renovate-Konfiguration (empfohlen)

```json
{
  "$schema": "https://docs.renovatebot.com/renovate-schema.json",
  "extends": [
    "config:recommended",
    ":semanticCommits",
    "group:allNonMajor"
  ],
  "schedule": ["every weekend"],
  "timezone": "Europe/Berlin",
  "prConcurrentLimit": 10,
  "prHourlyLimit": 2,
  "packageRules": [
    {
      "description": "Android Gradle Plugin",
      "matchDepNames": [
        "com.android.application",
        "com.android.library"
      ],
      "groupName": "Android Gradle Plugin",
      "automerge": true
    },
    {
      "description": "Kotlin Dependencies",
      "matchPackagePatterns": ["^org.jetbrains.kotlin"],
      "groupName": "Kotlin Dependencies",
      "automerge": true
    },
    {
      "description": "Compose Dependencies",
      "matchPackagePatterns": ["^androidx.compose"],
      "groupName": "Compose Dependencies",
      "automerge": true
    },
    {
      "description": "AndroidX Core",
      "matchPackagePatterns": ["^androidx."],
      "excludePackagePatterns": ["^androidx.compose"],
      "groupName": "AndroidX Core",
      "automerge": true
    },
    {
      "description": "Ktor Dependencies",
      "matchPackagePatterns": ["^io.ktor"],
      "groupName": "Ktor Dependencies",
      "automerge": false
    },
    {
      "description": "Testing Dependencies",
      "matchPackagePatterns": ["junit", "mockk", "mockito", "espresso", "turbine"],
      "groupName": "Testing Dependencies",
      "automerge": true
    },
    {
      "description": "Security Updates",
      "matchUpdateTypes": ["minor", "patch"],
      "automerge": true
    },
    {
      "description": "Major Updates",
      "matchUpdateTypes": ["major"],
      "automerge": false,
      "labels": ["breaking-change"]
    }
  ],
  "ignoreDeps": [
    "com.android.tools.build:gradle"
  ]
}
```

## 🔄 Vergleich: Dependabot vs. Renovate für Vivid

### PR-Aufwand

| Szenario | Dependabot | Renovate |
|----------|------------|----------|
| **Kotlin 2.2.20 → 2.2.21** | 1 PR | 1 PR ( gruppiert mit stdlib) |
| **Compose BOM Update** | 5-10 PRs (pro Artifact) | 1 PR (alle Compose) |
| **AndroidX Update** | 8-15 PRs | 1-2 PRs (gruppiert) |
| **RootEncoder Update** | 1 PR | 1 PR |
| **Gesamt pro Woche** | 15-30 PRs | 3-5 PRs |

### CI-Zeit

| Szenario | Dependabot | Renovate |
|----------|------------|----------|
| **Tests pro PR** | ~5 Min | ~5 Min |
| **PRs pro Woche** | 15-30 | 3-5 |
| **Gesamt CI-Zeit** | 75-150 Min | 15-25 Min |
| **Auto-Merge (Branch)** | ❌ Nicht möglich | ✅ Kein PR nötig |

### Features

| Feature | Dependabot | Renovate | Nutzen für Vivid |
|---------|------------|----------|------------------|
| **Dependency Dashboard** | ❌ | ✅ | 🟢 Übersicht über alle Updates |
| **Merge Confidence** | 1 Badge | 4 Badges | 🟢 Bessere Entscheidungsgrundlage |
| **Auto-Merge (Branch)** | ❌ | ✅ | 🟢 Spart CI-Zeit |
| **Monorepo-Presets** | ❌ | ✅ | 🟢 Gruppiert Compose/AndroidX |
| **Human-readable Schedule** | ❌ | ✅ | 🟢 "every weekend" statt Cron |
| **SHA-Pins** | ✅ | ✅ | 🟡 Beide unterstützen es |

## 📋 Entscheidung

### Für Dependabot bleiben (empfohlen)

**Begründung:**
1. ✅ **Bereits aktiv** — kein Änderungsbedarf
2. ✅ **Auto-Merge-Workflow** — bereits implementiert
3. ✅ **SHA-Pins** — funktioniert einwandfrei
4. ✅ **Keine App-Installation** — weniger Overhead
5. ✅ **Einfach** — nur YAML, kein JSON

**Optimierungsmöglichkeiten:**
- Dependabot-Gruppen in `dependabot.yml` ergänzen
- Auto-Merge-Workflow für alle Minor/Patch aktivieren

### Renovate als Ergänzung

**Wann sinnvoll:**
- Wenn der PR-Aufwand zu hoch wird (15-30 PRs/Woche)
- Wenn Dependency Dashboard gewünscht ist
- Wenn Auto-Merge (Branch-Modus) gewünscht ist

**Vorgehen:**
1. Mend Renovate App installieren
2. `renovate.json` im Repo-Root anlegen
3. Onboarding-PR mergen
4. Dependabot deaktivieren (oder parallel lassen)

## 🎯 Empfohlene Vorgehensweise

**Phase 1 (sofort):** Dependabot optimieren
```yaml
# .github/dependabot.yml erweitern
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
    groups:
      kotlin:
        patterns: ["org.jetbrains.kotlin*"]
      compose:
        patterns: ["androidx.compose*"]
      android:
        patterns: ["androidx.*"]
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
```

**Phase 2 (optional):** Renovate installieren
1. Mend Renovate App auf GitHub installieren
2. `renovate.json` anlegen (obige Konfiguration)
3. Onboarding-PR mergen
4. Dependabot deaktivieren

## 📚 Weiterführende Links

- [Renovate Docs](https://docs.renovatebot.com/)
- [Dependabot Docs](https://docs.github.com/en/code-security/dependabot)
- [Bot Comparison](https://docs.renovatebot.com/bot-comparison/)
- [Renovate for Android](https://proandroiddev.com/keep-your-android-project-up-to-date-with-renovate-bot)
