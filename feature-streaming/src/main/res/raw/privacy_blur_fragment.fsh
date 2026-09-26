#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform sampler2D uSampler;
varying vec2 vTextureCoord;

// Datenschutz-Anonymisierung (P0, Skizze
// docs/architecture/privacy-anonymization.md): bis zu MAX_ELLIPSEN Ellipsen
// (Zentrum + Radii in Textur-Koordinaten 0..1) werden mit Mosaik-Pixelierung
// unkenntlich gemacht. Ein-Pass (kein Extra-Blur-Framebuffer) — läuft damit
// in der bestehenden FBO-Filterkette von RootEncoder. Die Zonen-Koordinaten
// kommen vom PrivacyComposer (P1: manuelle Zonen, P2: BlazeFace-Boxen).

#define MAX_ELLIPSES 8
uniform vec2 uEllipseCenter[MAX_ELLIPSES];
uniform vec2 uEllipseRadii[MAX_ELLIPSES];

// Mosaik-Kantenlänge in Textur-Koordinaten (1/48 ≈ 15 px bei 720p-Breite).
const float PIXEL_GRID = 0.0208333;

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
    if (mask > 0.0) {
        vec2 grid = vec2(PIXEL_GRID);
        vec2 pixUv = (floor(vTextureCoord / grid) + 0.5) * grid;
        vec4 mosaik = texture2D(uSampler, pixUv);
        color = mix(color, mosaik, mask);
    }
    gl_FragColor = color;
}
