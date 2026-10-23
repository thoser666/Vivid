# Gespeicherte Streams

„Streaming & OBS“ öffnet die Liste gespeicherter Streams. Der Text einer
Material-3-Listenzeile öffnet deren Einstellungen; der getrennte Switch wählt
höchstens einen Stream aus. Eine vertikale Trennlinie grenzt den Switch vom
Navigationsbereich ab, auch in der OBS-Zeile. Untermenüs stehen in einer
abgerundeten Gruppe mit horizontalen, eingerückten Trennlinien. Er startet
keine Übertragung. Alle Switches dürfen
aus sein. „Start streaming“ verwendet beim nächsten Start den ausgewählten
Stream einschließlich seines optionalen zweiten Ziels.

„Stream erstellen“ führt über Twitch, YouTube, Kick oder Custom zu Name,
URL, Schlüssel und TLS. Speichern legt das Profil zunächst ausgeschaltet an.
Jedes Profil hat eigene Unterseiten für Multi-Streaming, Video, Replay,
Twitch-Kanalsteuerung (nur Twitch) und OBS. Video enthält Encoder-Presets,
Codec/Fallback/Adaptivbitrate, FPS, LLB, Bitrate und Schlüsselbildintervall
auf einer gemeinsamen, scrollbaren Video-Seite. Die Bitrate ist 1–15 Mbit/s in 1-Mbit/s-Schritten;
das Schlüsselbildintervall ist 1–10 Sekunden. Unterseiten bearbeiten denselben
Entwurf; erst „Speichern“ auf der Profilseite persistiert ihn. Zurück von der
Profilseite verwirft ungespeicherte Änderungen.

Die Reihenfolge lässt sich durch langes Drücken und Ziehen am Griff einer
Stream-Zeile ändern. An den Bildschirmrändern scrollt die Liste automatisch.
Erst Loslassen persistiert die vollständige neue Reihenfolge; Abbrechen
verwirft die Verschiebung. TalkBack bietet am Griff „Nach oben“/„Nach unten“.
Auswahl und Profileinstellungen bleiben beim Sortieren erhalten.

`StreamProfile` liegt im Domain-Modul. Profile und eine optionale aktive ID
werden gemeinsam in einer DataStore-Transaktion geschrieben. Der
`SettingsRepository.appSettingsFlow` legt das aktive Profil über die bisherigen
AppSettings-Felder; UI, Web-Remote und Chat nutzen denselben Start-Koordinator.
Ohne aktive ID sind beide Transportziele leer. Bestehende Installationen sehen
das bisherige Stream-Ziel als aktiviertes Profil; die erste Profiländerung
persistiert die Migration. Ohne bisherige URL bleibt die Liste leer. Alte
Preset-Bitraten oberhalb 15 Mbit/s werden beim Übernehmen in ein Profil auf die
neue Obergrenze begrenzt.

FPS werden gegen Kamera und Codec geprüft. Explizite 30 FPS verwenden den
Standard-Kamerapfad; 60 FPS können den vorhandenen experimentellen Modus nutzen.
Fallbacks bleiben sichtbar wie zuvor. Bitrate und GOP werden vor Encoder-Start
an RootEncoder übergeben; der Keyframe-Watchdog verwendet dasselbe Intervall.
Eine laufende Aufnahme oder Übertragung behält ihren vorbereiteten Encoder.
Profilwechsel konfigurieren den nächsten Start und schalten keinen Live-Stream um.

Der OBS-Switch sperrt Verbindungsaufbau und trennt eine bestehende Verbindung,
wenn er ausgeschaltet wird. Die Twitch-Unterseite verwendet den Token des
bearbeiteten Entwurfs statt der Werte eines anderen aktiven Profils. Die
bestehende verschlüsselte Twitch-OAuth-Session behält ihren Vorrang.

JVM-Tests prüfen DataStore-Roundtrip, Migration, isolierte Profile, Auswahl und
Ausschalten, fehlgeschlagene Saves sowie Encoder-Übergabe und GOP-Zeitsteuerung.
Compose-Tests prüfen Erstellen, getrennte Zeilen-/Switch-Aktionen und die
Unterseiten. Eine echte Geräte-/Ingest-Verifikation bleibt ein eigener Schritt.

## Validierung des ursprünglichen Profilumbaus (2026-10-24)

Vollständiges `bash scripts/pre-push.sh` grün: Modul-Tests, Lint und alle
Shell-Guards. Ergänzend Standard-App-Tests, letzter OBS-Abbruch-Test und
FOSS-Debug-APK grün. Insgesamt 1.879 JVM-Testfälle, keine Fehler und fünf
bestehende Skips. Die Compose-Bilder von Liste, Video und Encoder wurden
auf 320 dp Breite visuell geprüft (`feature-settings/build/reports/stream-menu/`).
Geräte-/Ingest-Tests wurden in diesem Schritt nicht ausgeführt.

## Nachfolgende Menü- und Layoutprüfung (2026-10-24)

Stream-Sortierung, getrennte Navigation/Switches und die gemeinsame Video-Seite
wurden mit gezielten Settings-Tests geprüft. Für Appearance bestehen fünf
Compose-Layoutfälle mit schmalen Bildschirmen, vergrößerter Schrift und Querformat;
der Farbwähler zeigt Namen und Farbmuster in einem Dropdown. Die FOSS-Debug-APK
wurde gebaut und auf einem Samsung SM-G991B installiert. Dort wurde die
Systemleisten-Sichtbarkeit geprüft: Kamera-Vorschau ausgeblendet, Einstellungen
und Controls-Menü eingeblendet, nach Rückkehr zur Vorschau wieder ausgeblendet.
Auf Wunsch wurde die vollständige Testsuite für diese Folgeänderungen nicht erneut
ausgeführt; ein echter Stream wurde dabei nicht gestartet.
