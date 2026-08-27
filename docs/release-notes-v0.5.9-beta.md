# Release Notes: Vivid v0.5.9-beta

**Release Date:** 2026-08-27
**Version:** 0.5.9-beta (versionCode 5090)
**Channel:** Beta (GitHub Releases)

## 🎉 Highlights

### ☀️ Low-Light-Boost (Helligkeitsanhebung)
Software-basierte Helligkeitsanhebung für schlechte Lichtverhältnisse — funktioniert auf **allen** Videoquellen (Kamera, Screen-Capture, Video-Player):

- **OpenGL-Brightness-Filter** (RGB × 1.5 Gain) via eigenem GLSL-Shader
- **UI-Toggle** im Streaming-Screen (oben rechts, neben Torch/Filter)
- **Bot-Befehl `!boost`** (Owner-only, Aliasse `!lowlight`/`!low-light`)
- Bestätigung: „☀️ Low-Light-Boost AN" / „🌙 Low-Light-Boost AUS"
- Ergänzt den Hardware-Torch (beide unabhängig aktivierbar)

### 🎨 Color-Spaces + 3D-LUTs (PoC)
Architektureller Proof-of-Concept für die Farbraum-Pipeline:

- **Color-Space-Auswahl:** sRGB (Standard), Display P3 (breiterer Farbraum), Apple Log (logarithmisches Encodierung)
- **3D-LUT-Presets:** Warm (Amber-Ton), Cool (Blau-Ton), None (Passthrough)
- **GLSL 3D-LUT-Shader** mit Hald-CLUT Texture-Lookup + Gamma-Korrektur
- **Encoder-Pfad verifiziert:** RootEncoder `setFilter()` wirkt auf GL-Pipeline (Encoder + Preview) — bestätigt per Wiki-Doku
- **UI-Buttons** im Streaming-Screen (zirkulär: LUT-Preset + Color-Space)
- **Bot-Befehle `!lut`/`!colorspace`** (Owner-only, Aliasse `!cs`)

### 📊 Metriken

| Metrik | Wert |
|--------|------|
| **Moblin-Parität** | 29 → **30** ✅ (Kamera & Video 7 → 8) |
| **Tests** | 14× LowLightBoostController + 12× LutController + 7× BotCommandProcessor |
| **Pre-Push-Gate** | ✅ Alle Checks grün |
| **I18n** | de/en/fr vollständig |

## 📦 Was ist drin

| Feature | Status | Bot-Befehl |
|---------|--------|------------|
| Low-Light-Boost (1.5x Gain) | ✅ | `!boost` |
| Color-Spaces (sRGB/P3/Log) | ✅ PoC | `!colorspace` / `!cs` |
| 3D-LUT Presets (Warm/Cool) | ✅ PoC | `!lut` |

## 🔧 Technische Details

- **LowLightBoostController:** toggle/setEnabled/resetState + `BoostApplier`-Lambda
- **LowLightBrightnessFilterRender:** GLSL-Brightness-Shader in `res/raw/`
- **LutController:** toggle/setPreset/setColorSpace/resetState + `LutApplier`-Lambda
- **HaldClutFilterRender:** GLSL 3D-LUT-Shader mit Hald-CLUT Texture-Lookup
- **ColorSpace enum:** sRGB (γ=2.2), Display P3 (γ=2.2), Apple Log (γ=1.0)
- **LutPreset enum:** NONE/WARM/COOL mit Bitmap-Generierung

## 📋 Nächste Schritte

- [ ] Echte Hald-CLUT-PNGs als Bundled-LUTs (Benutzer-Import via SAF)
- [ ] Color-Space-Überprüfung auf unterstützten Geräten
- [ ] LUT-Persistenz in den Settings
- [ ] Performanz-Optimierung (Texture-Resolution, Frame-Drop-Test)
