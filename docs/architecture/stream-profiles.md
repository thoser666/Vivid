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
Jedes Profil hat eigene Unterseiten für Video, Replay,
Twitch-Kanalsteuerung (nur Twitch), Multi-Streaming und OBS. Multi-Streaming steht
unmittelbar vor OBS. Video enthält Encoder-Presets,
Codec/Fallback/Adaptivbitrate, FPS, LLB, Bitrate und Schlüsselbildintervall
auf einer gemeinsamen, scrollbaren Video-Seite. Die Bitrate ist 1–15 Mbit/s in 1-Mbit/s-Schritten;
das Schlüsselbildintervall ist 1–10 Sekunden. Unterseiten bearbeiten denselben
Entwurf. Jede Unterseite besitzt einen eigenen „Speichern“-Button, der das Profil
sofort persistiert und zur Stream-Liste zurückkehrt; ein weiteres Speichern auf
der Profilseite ist nicht nötig. Die Auswahl des aktiven Streams bleibt erhalten.
Bei einem Fehler bleibt der Entwurf für einen erneuten Versuch geöffnet und die
Fehlermeldung wird eingeblendet. Zurück von der Profilseite verwirft nur noch
nicht gespeicherte Änderungen.

Die primäre und optionale zweite URL werden bereits während der Eingabe mit
`StreamConfigValidator` geprüft. Protokoll- und Hostfehler erscheinen direkt am
jeweiligen Feld und sperren alle Speichern-Buttons; auch der ViewModel-Save prüft
vor dem Persistieren dieselben Regeln. Eine leere zweite URL deaktiviert das
optionale Ziel. Hinweise zu Stream-Key und SRT/TLS bleiben nicht blockierend.
Der Go-Live-Koordinator prüft gespeicherte Daten weiterhin als letzte Absicherung.

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

Der Betriebsmodus legt für die Kamera 9:16 (Portrait) oder 16:9 (Querformat)
fest. Die Codec-Prüfung verwendet die tatsächlich codierten Pixelachsen;
Camera2 erhält weiterhin seine native Aufnahmegröße. Der GL-Szenenlayer und
Preview verwenden dieselbe Ausrichtung, auch im experimentellen Kamerapfad.
Eine laufende Aufnahme/Übertragung behält ihre ursprüngliche Ausrichtung.

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
