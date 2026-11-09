#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform sampler2D uSampler;
uniform sampler2D uMaskTex;
varying vec2 vTextureCoord;

// Datenschutz-Anonymisierung (P0, Skizze
// docs/architecture/privacy-anonymization.md): bis zu MAX_ELLIPSEN Ellipsen
// (Zentrum + Radii in Textur-Koordinaten 0..1) werden mit Mosaik-Pixelierung
// unkenntlich gemacht. Ein-Pass (kein Extra-Blur-Framebuffer) — läuft damit
// in der bestehenden FBO-Filterkette von RootEncoder. Die Zonen-Koordinaten
// kommen vom PrivacyComposer (P1: manuelle Zonen, P2: Gesichter).
//
// P2b: uFullFrame (0/1) pixeliert zusätzlich das **gesamte** Bild — der
// Standort des Streamers bleibt auch ohne erkennbare Zonen verborgen. Die
// Mosaik-Kantenlänge uPixelGrid (Textur-Koordinaten) unterscheidet dabei die
// schwache Stufe für Zonen/Gesichter (DEFAULT_PIXEL_GRID) von der groben
// Voll-Frame-Stufe (FULL_FRAME_PIXEL_GRID), für die Architektur nicht mehr
// lesbar ist.
//
// P4.1: uMaskTex trägt die Personen-Maske (Rot = 1 Person, 0 Hintergrund),
// uMaskEnabled (0/1) schaltet den **Umgebungs**-Modus. Er ist das Gegenstück
// zum Ganzbild: alles *außerhalb* der Person wird mit der groben Stufe
// pixeliert (Skizze §11.4). Die Maske ist die erste Textur, die der
// Privacy-Filter selbst hält; sie startet als 1x1-Schwarz und
// uMaskEnabled bleibt 0, bis der Render einen gültigen Snapshot hochgeladen
// hat (§11.6) — eine leere Textur darf nie als „alles Hintergrund" wirken.

#define MAX_ELLIPSES 8
uniform vec2 uEllipseCenter[MAX_ELLIPSES];
uniform vec2 uEllipseRadii[MAX_ELLIPSES];
uniform float uFullFrame;
uniform float uPixelGrid;
uniform float uMaskEnabled;

// Mosaik-Kantenlänge in Textur-Koordinaten (1/48 ≈ 15 px bei 720p-Breite).
const float DEFAULT_PIXEL_GRID = 0.0208333;
// Grobe Stufe für Ganzbild und Umgebung (1/16 ≈ 45 px bei 720p).
const float FULL_FRAME_PIXEL_GRID = 0.0625;

// Mosaik-Farbe an einer Textur-Koordinate, Kantenlänge grid.
vec4 mosaic(vec2 uv, float grid) {
    float stepUv = grid > 0.00001 ? grid : DEFAULT_PIXEL_GRID;
    vec2 pixUv = (floor(uv / vec2(stepUv)) + 0.5) * vec2(stepUv);
    return texture2D(uSampler, pixUv);
}

void main() {
    vec4 color = texture2D(uSampler, vTextureCoord);
    float mask = 0.0;
    for (int i = 0; i < MAX_ELLIPSES; i++) {
        // Deaktivierte Slots (Radius 0) tragen nicht zur Maske bei — ohne
        // Branch/Continue (GLSL-ES-1.0-sicher über step).
        float active = step(0.0001, uEllipseRadii[i].x) * step(0.0001, uEllipseRadii[i].y);
        vec2 rel = (vTextureCoord - uEllipseCenter[i]) / max(uEllipseRadii[i], vec2(0.0001));
        float dist = length(rel);
        // Weiche Kante: voll ab 0.85 Radius, Ausblenden bis 1.0.
        mask = max(mask, active * (1.0 - smoothstep(0.85, 1.0, dist)));
    }
    // Voll-Frame-Modus: die Maske ist überall maximal (kein Ecken-Leck).
    mask = max(mask, clamp(uFullFrame, 0.0, 1.0));
    if (mask > 0.0) {
        // uPixelGrid wird framesynchron vom Render gesetzt; der Fallback
        // deckt den ungesetzten Fall (0.0) ab, bevor der erste draw folgt.
        color = mix(color, mosaic(vTextureCoord, uPixelGrid), mask);
    }

    // P4.1: Umgebung = alles, was die Maske nicht als Person ausweist.
    // smoothstep entschärft die bilineare Masken-Kante (harte Kanten sehen
    // wie ein Greenscreen-Unfall aus und flackern stärker, §11.6). Ohne
    // gültige Maske ist uMaskEnabled 0 — der Render erzwingt dann Ganzbild.
    // Der Branch ist uniform und kohärent; ausgeschaltet kostet der Modus
    // keinen zusätzlichen Textur-Zugriff.
    if (uMaskEnabled > 0.0) {
        float person = texture2D(uMaskTex, vTextureCoord).r;
        float environment = clamp(uMaskEnabled, 0.0, 1.0) * (1.0 - smoothstep(0.35, 0.65, person));
        if (environment > 0.0) {
            color = mix(color, mosaic(vTextureCoord, FULL_FRAME_PIXEL_GRID), environment);
        }
    }
    gl_FragColor = color;
}
