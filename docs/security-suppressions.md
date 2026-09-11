# Security-Suppressions-Register

> **Zweck:** Dieses Register ist die zentrale, versionskontrollierte Übersicht **aller** Security-Suppressions in Vivid — also aller Stellen, an denen ein Sicherheitstool ein Finding meldet (oder gemeldet hätte) und wir es bewusst nicht als Code-Fix behandeln. Jeder Eintrag braucht eine **Begründung**, einen **Verantwortlichen** und ein **Prüfbis-Datum**; abgelaufene Einträge blockieren das Pre-Push-Gate (siehe [Review-Prozess](#review-prozess)).

> **English:** This register is the single source of truth for every security suppression in Vivid — each entry documents why a finding is accepted instead of fixed, who owns it, and when it must be re-reviewed. Expired entries fail the pre-push gate (`scripts/check_suppressions_register.sh`).

**Stand der Registerdaten:** 2026-09-11 · **Letzte Vollprüfung:** 2026-09-11 (Guard `check_suppressions_register.sh` gegen Live-APIs verifiziert)

<!-- review-dates
Maschinenlesbare Prüffristen — geparst von scripts/check_suppressions_register.sh.
Format pro Zeile: JJJJ-MM-TT Beschreibung. Abgelaufene Daten schlagen beim Gate an.
-->

<!-- review-dates-data
2026-12-10 Snyk-Ignore rubyzip (SNYK-RUBY-RUBYZIP-19666145): Re-Assessment, falls fastlane 3.x zulässt oder CVE/GHSA publiziert wird
2027-03-11 Halbjährlicher Review: Code-Scanning-Dismissals, Dependabot-Dismissals, NOSONAR-Stellen, Scorecard-Annotationen
-->

---

## Übersicht nach Quelle

| Quelle | Suppressions-Mechanismus | Aktive Einträge | Automatisierte Prüfung |
|---|---|---|---|
| Snyk | `.snyk`-Policy (Ignore mit reason + expiry) | 1 | `check_snyk_policy.sh` (Pre-Push-Gate) |
| GitHub Code Scanning (CodeQL) | Alert-Dismissal (false positive / mitigated) | 13 | monatlicher Review-Workflow (Issue-Automat) + halbjährlicher Termin in diesem Register |
| GitHub Dependabot | Alert-Dismissal (`tolerable_risk`) | 9 (davon 1 obsolet: #26, Graph ≥ Fix) | Einzel-Review 2026-09-11 (Ist-Versionen + Scope-Nachweis im Register) + monatlicher Review-Workflow |
| SonarCloud | `// NOSONAR`-Kommentare (S5332) | 3 (an 1 Stelle) | SonarCloud markiert Zeile; Review-Kontext hier |
| OpenSSF Scorecard | `.github/scorecard.yml`-Annotationen | 3 Check-Blöcke | Scorecard-Viewer zeigt Begründung neben dem Finding |
| Secret Scanning | — (Feature im Repo deaktiviert) | 0 | — |

**Regel:** Eine Suppression ist nur legitim, wenn sie (a) in diesem Register steht, (b) eine prüfbare Begründung hat und (c) ein Prüfbis-Datum trägt. „Damit CI grün wird" ist keine Begründung.

---

## 1. Snyk-Policy (`.snyk`)

Maschinell geprüft durch `scripts/check_snyk_policy.sh`: reason (≥ 40 Zeichen), zukünftiges `expires`, `--policy-path`-Verdrahtung in `security-snyk.yml` (test + monitor). Expires-Ablauf blockiert das Gate.

| ID | Paket / Pfad | Severity | Art | Begründung (Kurzfassung) | Expires | Verantwortlich |
|---|---|---|---|---|---|---|
| SNYK-RUBY-RUBYZIP-19666145 | rubyzip 2.4.1 (via fastlane) | High | Directory Traversal in `Zip::Entry#extract` | **Nicht behebbar:** fastlane pinnt `rubyzip >= 2.0.0, < 3.0.0` (auch fastlane 2.239.0), der Fix liegt erst in 3.4.0+ — ein Fork wäre unverhältnismäßig. Snyk-Research ohne CVE/GHSA (OSV listet nur die Alt-CVEs 2017–2019). Risiko bewusst akzeptiert: rubyzip läuft ausschließlich pipeline-seitig in fastlane und verpackt/entpackt nur von der Pipeline selbst erzeugte Archive (Release-APK/AAB) — keine Angreifer-kontrollierten Zip-Pfade, nichts davon landet im APK. | **2026-12-10** | thoser666 |

**Re-Assessment-Trigger (vor dem Datum):** fastlane lockert den Constraint auf 3.x, ein CVE/GHSA wird publiziert, oder Snyk meldet einen zweiten verwundbaren Pfad → Ignore zurückziehen, Fix bewerten.

---

## 2. GitHub Code Scanning (CodeQL) — dismissed Alerts

Dismissals sind in GitHub nicht mit Ablaufdatum belegbar — daher werden sie hier mit einem Review-Intervall von **6 Monaten** geführt. Der Guard vergleicht Register und Live-API; Abweichungen (neue Dismissals ohne Registereintrag, verschwundene Einträge) schlagen an.

Dismissed am 2026-08-26 bzw. 2026-09-03 durch **thoser666**, Review bis **2027-03-11**:

| Alert # | Regel | Datei | Reason | Begründung |
|---|---|---|---|---|
| #8 | `java/android/backup-enabled` | `app/src/main/AndroidManifest.xml` | **mitigated** | `allowBackup` ist zwar `true`, aber über `android:fullBackupContent="@xml/backup_rules"` inklusive `dataExtractionRules` restriktiv konfiguriert (keine Secrets/Tokens in den Backup-Domänen; Sentry-DSN ist kein Secret). Das Finding stammt aus der Zeit vor der Regel-Verfeinerung. |
| #9 | `java/android/implicit-pendingintents` | `app/src/main/java/com/vivid/irlbroadcaster/StreamingService.kt` | false positive | PendingIntent nur mit explizitem Intent auf die eigene App-Komponente (Service); kein impliziter Intent, keine fremde Component-Auflösung. |
| #10 | `java/local-variable-is-never-read` | `domain/.../RegistrationResult.kt` | false positive | Variable ist über Logger-Interpolation/`when`-Exhaustiveness formal gelesen; Meldung ist ein Grenzfall der Dead-Store-Analyse. |
| #11 | `java/local-variable-is-never-read` | `feature-chat/.../BotCommandProcessor.kt` | false positive | dito |
| #12 | `java/local-variable-is-never-read` | `feature-chat/.../ChatStreamControl.kt` | false positive | dito |
| #13 | `java/local-variable-is-never-read` | `feature-chat/.../ChatConnectionState.kt` | false positive | dito |
| #14 | `java/local-variable-is-never-read` | `feature-obs-control/.../ConnectionState.kt` | false positive | dito |
| #15 | `java/local-variable-is-never-read` | `feature-obs-control/.../ObsControlUiState.kt` | false positive | dito |
| #16 | `java/field-masks-super-field` | `feature-obs-control/.../ConnectionState.kt` | false positive | Feld verdeckt bewusst ein Interface-/Basisklassen-Feld mit gleichem Namen und identischem Zweck (State-Holding); beide werden gepflegt, kein Shadowing-Bug. |
| #17 | `java/field-masks-super-field` | `feature-streaming/.../StreamingState.kt` | false positive | dito |
| #18 | `java/field-masks-super-field` | `feature-chat/.../ThirdPartyEmoteService.kt` | false positive | dito |
| #46 | `java/local-variable-is-never-read` | `feature-chat/.../ChatPollManager.kt` | false positive | dito (Dead-Store-Grenzfall) |
| #461 | `java/local-variable-is-never-read` | `feature-streaming/.../ReplayRecording.kt` | false positive | dito |

**Sammelbegründung `local-variable-is-never-read`:** Die Meldungen betreffen temporäre Variablen, deren Wert über `when`-Exhaustiveness, Logger-Interpolation oder Data-Class-Destructuring formal konsumiert wird — die Analyse übersieht diese Konsumpfad-Klassen. Bei jedem neuen Dismissal dieser Regel ist individuell zu prüfen, dass tatsächlich kein toter Code verbleibt (toter Code wird sonst regulär entfernt, nicht dismissed).

---

## 3. GitHub Dependabot — dismissed Alerts

Dismissed am 2026-08-15 durch **thoser666**, einheitlich `tolerable_risk`. **Einzel-Review 2026-09-11** (verwundbare Range vs. tatsächlich aufgelöster Version in `settings.gradle.kts`-Submission-Grafik + Runtime-Classpath-Scan beider Flavors): **#26 ist obsolet** (Graph löst 4.5.14 ≥ Fix 4.5.13 — Alert müsste bei nächster Submission automatisch auf `fixed` gehen), die übrigen 8 sind weiterhin in der verwundbaren Range, aber durch den harten Scope-Nachweis entschärft: **keines der 9 Pakete liegt im App-Runtime-Classpath** (`:app:dependencies` standard+foss, je 1.481 Zeilen, 0 Treffer) — sie leben ausschließlich im Build-/Submission-Kontext (AGP-Tooling: jetifier, manifest-merger; Sonar-Scanner-JAR; Ktor-3.x-Buildzeitlinie). Review bis **2027-03-11**:

| Alert # | Advisory (Kurztitel) | Paket | Verwundbarer Bereich | Ist-Version (11.09.) | Status | Begründung |
|---|---|---|---|---|---|---|
| #14 | XXE Injection in JDOM | `org.jdom:jdom2` | < 2.0.6.1 | **2.0.6** | in Range (Fix 2.0.6.1) | AGP-Tooling (jetifier/manifest-merger-Kontext), nicht im App-Runtime; XML-Parsing nur über von der App kontrollierte Dokumente. |
| #16 | DoS via komprimiertem JWE-Content | `org.bitbucket.b_c:jose4j` | < 0.9.6 | **0.9.5** | in Range (Fix 0.9.6) | Build-/Submission-Kontext (Sonar-Scanner-Abhängigkeit), keine Angreifer-kontrollierten JWE-Inputs im Produktionspfad. |
| #19 | Riskante Kryptografie in bcpkix | `org.bouncycastle:bcpkix-jdk18on` | >= 1.49, < 1.84 | **1.80.2** | in Range (Fix 1.84) | Build-/Submission-Kontext; bcpkix-Modul nicht für Signatur-/Zertifikatspfade der App verwendet, verwundbarer Codepfad im Shipped-Build nicht erreichbar. |
| #20 | LDAP Injection in bcprov | `org.bouncycastle:bcprov-jdk18on` | >= 1.74, < 1.84 | **1.80.2** | in Range (Fix 1.84) | Build-/Submission-Kontext; App nutzt keine LDAP-Verbindungen. Fix folgt mit dem nächsten BC-Transitiv-Update. |
| #26 | XSS in Apache HttpClient (4.x) | `org.apache.httpcomponents:httpclient` | < 4.5.13 | **4.5.14** | ✅ **obsolet** (4.5.14 ≥ Fix 4.5.13), Alert verbleibt `dismissed` | Graph löst bereits die gepatchte Version auf (httpmime 4.5.6-Kette → `-> 4.5.14`). Empirisch (11.09., nach vollständiger Submission): **GitHub wandelt dismissed Alerts bei Fix nicht in `fixed`** — Auto-Resolve gilt nur für offene Alerts; der Zustand ist also registertechnisch neutral, die Befund-Substanz ist weg. |
| #27 | Uncontrolled Recursion in Commons Lang | `org.apache.commons:commons-lang3` | >= 3.0, < 3.18.0 | **3.16.0** | in Range (Fix 3.18.0) | Build-/Submission-Kontext (AGP-Tooling); Rekursionsvektor adressiert adversariale Klassennamen aus nicht vertrauenswürdiger Quelle — im Vivid-Kontext nicht vorhanden. |
| #62 | HTTP/1 Header-Parsing-Memory-Exhaustion | `org.apache.httpcomponents.core5:httpcore5` | < 5.4.3 | **5.3.6** (Cache-Beleg) | in Range (Fix 5.4.3), nicht im App-Runtime | Ktor-3.x-Buildzeitlinie (ktor-client-apache5, nicht im Vivid-Graph); DoS-Vektor adressiert Server-Parsing — im App-Runtime nicht vorhanden. |
| #64 | HPackDecoder unbegrenzte Header-Liste | `org.apache.httpcomponents.core5:httpcore5-h2` | < 5.4.3 | **5.3.6** (Cache-Beleg) | in Range (Fix 5.4.3), nicht im App-Runtime | Ktor-3.x-Buildzeitlinie; HTTP/2-Decode-Pfad wird in der App nicht als Server betrieben. |
| #65 | Connection Leak bei Content-Encoding-Fehler (DoS) | `org.apache.httpcomponents.client5:httpclient5` | >= 5.0-alpha1, < 5.6.3 | **5.5.1** (Cache-Beleg) | in Range (Fix 5.6.3), nicht im App-Runtime | Ktor-3.x-Buildzeitlinie (ktor-client-apache5 3.5.2, nicht im Vivid-Graph); Pool-Exhaustion-Szenario betrifft Langzeit-Server-Verbindungen. |

**Gemeinsamer Re-Assessment-Trigger:** Dependabot öffnet automatisch neue Alerts, sobald eine betroffene Dependency die verwundbare Range verlässt — die Dismissals beziehen sich immer nur auf die notierte Range. Enthält eine neue Dependency-Version weiterhin die Range, erscheint ein neuer Alert, der neu bewertet wird. **Lehre #26 (empirisch, 11.09.):** Dismissed Alerts wechseln bei Fix **nicht** automatisch auf `fixed` — GitHub behält den Zustand `dismissed` bei; der Alert lässt sich nur manuell re-openen. Für das Register zählt deshalb nicht der API-Zustand, sondern die dokumentierte Ist-Version: sie ist die Wahrheit über die Befund-Substanz.

---

## 4. SonarCloud — Inline-Suppressions (`NOSONAR`)

SonarCloud listet aktuell **keine** WONTFIX-Issues und **keine** SAFE-Hotspots (API-Abgleich 2026-09-11). Im Code existieren 3 bewusste `// NOSONAR`-Kommentare (Regel **S5332: „Using clear-text protocols is security-sensitive"**):

| Datei | Regel | Begründung | Review-Intervall |
|---|---|---|---|
| `feature-settings/.../StreamPlatform.kt` (3×) | S5332 | Die `rtmp://`-Presets sind statische, vom User konfigurierbare Ingest-Endpunkte bekannter Plattformen (Twitch/YouTube/Kick). Sie werden nicht von der App selbst als Netzwerkverbindung aufgebaut (RootEncoder konvertiert `rtmp://` → `rtmps://` bei aktiviertem TLS-Toggle); die URL ist nur ein Konfigurationswert für ein Drittanbieter-Tool. Siehe SECURITY.md „Netzwerk-Sicherheit (Cleartext blockiert)". | 6 Monate → **2027-03-11** |

**Konvention:** Jedes `NOSONAR` muss mit `: bewusste…` + Verweis auf SECURITY.md bzw. dieses Register begründet werden. Ungeratene `NOSONAR`-Kommentare (ohne Begründung im selben Kommentar) schlagen beim Review an.

---

## 5. OpenSSF Scorecard — Annotationen (`.github/scorecard.yml`)

Annotationen sind kein „Ignoring" von Findings, sondern Maintainer-erklärter Kontext, den der Scorecard-Viewer neben dem Finding anzeigt. Vollständige Begründungen stehen in der Datei selbst; hier die Kurzfassung mit Review-Datum:

| Check | Finding | Reason | Kurzbegründung | Review bis |
|---|---|---|---|---|
| `binary-artifacts` | Alerts #34–#39: `docs/fdroid/{repo,archive}/index*.jar` | `not-applicable` + `remediated` | Das sind die **signierten F-Droid-Distributions-Indizes**, die der F-Droid-Client von festen URLs lädt — Umsiedeln würde Client-URLs brechen. Integrität ist abgesichert (reproduzieller wöchentlicher Rebuild, PR-basiert, signaturverifiziert durch den Client, gehärtete Supply-Chain). | 2027-03-11 |
| `binary-artifacts` | Alert #40: `gradle/wrapper/gradle-wrapper.jar` | `not-supported` | Der Wrapper-JAR ist der Gradle-Bootstrap, byte-identisch zur offiziellen Distribution; CI validiert jede Ausführung gegen die Gradle-Wrapper-Checksummen-DB (`gradle/actions/wrapper-validation`). Scorecard hat keine Erkennung dafür. | 2027-03-11 |
| `fuzzing` | Alert #43: „no fuzzer integrations found" | `not-applicable` | Scorecard erkennt nur Go/Haskell/JS/Erlang-Fuzzer sowie OSS-Fuzz (C/C++). Vivid ist eine reine Kotlin/Android-App (memory-safe, kein C/C++ im eigenen Code) — ein echter Fuzzer würde den Score nicht verändern. | 2027-03-11 |

---

## Review-Prozess

1. **Halbjährlicher Termin (nächster: 2027-03-11):** Alle Einträge mit `Review bis`/`Expires` durchgehen — per `gh api` die dismissals gegen die Tabelle abgleichen, Snyk-Expiry prüfen, `NOSONAR`-Stellen neu bewerten.
2. **Ablauf-Schutz:** Der Guard `scripts/check_suppressions_register.sh` (Teil des Pre-Push-Gates) blockiert Pushes, sobald ein Datum in der Vergangenheit liegt, ein Snyk-Ignore ohne reason/expiry existiert, ein Register-Eintrag fehlt (Alert-Gegenprobe) oder ein `NOSONAR` ohne Begründung im Code steht.
3. **Monatliche Automatisierung:** Der Workflow `.github/workflows/automation-suppressions-register.yml` (Cron: 11. des Monats, 06:00 UTC, manuell per `workflow_dispatch` triggerbar) führt den Guard im **Live-Modus** aus. Bei Divergenz (neue Dismissals ohne Registereintrag, abgelaufene Fristen, Snyk-Divergenz, unbegründetes NOSONAR) öffnet er das Review-Issue „Suppressions-Register: monatlicher Review — Divergenz oder abgelaufene Fristen“ bzw. kommentiert es (Marker-idempotent); ist das Register konsistent, wird das offene Review-Issue automatisch geschlossen.
4. **Neue Suppression anlegen:** Erst hier eintragen (Begründung + Datum + Verantwortlicher), dann in der jeweiligen Quelle suppressen. Suppression ohne Registereintrag = Gate-Fail.
5. **Suppression entfernen:** Eintrag aus dem Register löschen, in der Quelle zurücknehmen (Alert reopen / `.snyk`-Zeile entfernen / `NOSONAR` löschen), Register-Hash im Guard aktualisiert sich automatisch.

### Verantwortlichkeiten

| Quelle | Owner | Eskalation |
|---|---|---|
| `.snyk` + Scorecard-Annotationen | thoser666 | bei Severity-Änderung: sofort |
| Code Scanning / Dependabot Dismissals | thoser666 | halbjährlicher Review, sonst bei neuem Alert |
| SonarCloud NOSONAR | thoser666 | bei Regel-Update (S5332-Verhalten) |

### Guard & Selbsttest

| Prüfung | Skript | Verhalten |
|---|---|---|
| Register-Hygiene (offline) | `scripts/check_suppressions_register.sh` | Fehlt das Register, liegt ein Review-Datum in der Vergangenheit, divergieren SNYK-IDs zwischen `.snyk` und Register, trägt ein `NOSONAR` keine Begründung oder fehlt die Scorecard-Referenz → Gate-Fail |
| Live-Gegenprobe (optional) | dito, mit Netzwerk | Jeder per GitHub-API gelistete dismissed Code-Scanning-/Dependabot-Alert muss im Register stehen — neue Dismissals ohne Registereintrag schlagen an. Bei Netzwerk-/Berechtigungsfehlern neutral (bricht das Gate nie) |
| Selbsttest | `scripts/test_suppressions_register.sh` | 10 Offline-Fixtures (gültig, abgelaufen, NOSONAR ohne Begründung, Register-Divergenz in beide Richtungen, fehlende Dateien, leerer Prüffrist-Block, G7 mit 403 → neutral) |
| Review-Workflow-Selbsttest | `scripts/test_suppressions_register_workflow.sh` | 15 Offline-Checks zu `automation-suppressions-register.yml`: Cron/Trigger, Minimalprivilegien, SHA-Pinning, Live-Modus, Idempotenz, Issue-Close, Gate-Konsistenz |

### Changelog des Registers

| Datum | Änderung |
|---|---|
| 2026-09-11 | Erstfassung: 1 Snyk-Ignore, 13 Code-Scanning-Dismissals, 9 Dependabot-Dismissals, 3 NOSONAR, 3 Scorecard-Annotationen erfasst. |
| 2026-09-11 | Monatlicher Review-Workflow ergänzt (`automation-suppressions-register.yml`, Cron Tag 11): Live-Guard mit Issue-Automat (öffnen/kommentieren/schließen, Marker-idempotent) + Workflow-Selbsttest (15 Checks, CI + Pre-Push-Gate). |
| 2026-09-11 | **Einzel-Review der 9 Dependabot-Dismissals**: #26 httpclient obsolet (Graph löst 4.5.14 ≥ Fix 4.5.13), 8× in Range mit Ist-Version + hartem Scope-Nachweis (keines im App-Runtime-Classpath, `:app:dependencies` standard+foss je 1.481 Zeilen) — Tabelle um Ist-Version/Status erweitert. |
