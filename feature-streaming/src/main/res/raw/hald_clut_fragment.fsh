#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform sampler2D uSampler;
uniform sampler2D uLutSampler;   // 3D-LUT as 2D texture (Hald-CLUT)
uniform float uLutSize;          // Number of entries per axis (e.g. 16 for 16x16x16)
uniform float uLutTexWidth;      // Width of the LUT texture in pixels
uniform float uLutTexHeight;     // Height of the LUT texture in pixels
uniform float uGamma;            // Gamma correction (2.2 for sRGB, 1.0 for linear)

varying vec2 vTextureCoord;

// Convert a linear color to sRGB (inverse gamma)
vec3 linearToSrgb(vec3 c) {
    return pow(c, vec3(1.0 / 2.2));
}

// Convert sRGB to linear (apply gamma)
vec3 srgbToLinear(vec3 c) {
    return pow(c, vec3(uGamma));
}

void main() {
    vec4 color = texture2D(uSampler, vTextureCoord);

    // Step 1: Convert from gamma space to linear
    vec3 linear = srgbToLinear(color.rgb);

    // Step 2: Clamp to [0, 1] for LUT lookup
    vec3 clamped = clamp(linear, 0.0, 1.0);

    // Step 3: Compute LUT lookup position
    // Haid-CLUT: for size N, the image has N^2 columns and N rows.
    // Each "slice" is a row. Within a row, the column encodes (green * N + blue).
    float scale = (uLutSize - 1.0) / uLutSize;
    float halfTexel = 0.5 / uLutSize;

    float blueIndex = clamped.b * scale;
    float greenIndex = clamped.g * scale;

    // Row = red index, Column = greenIndex * N + blueIndex
    float row = floor(clamped.r * scale);
    float col = greenIndex * uLutSize + blueIndex;

    // Convert to texture coordinates
    float texelX = (col + halfTexel) / uLutTexWidth * uLutTexWidth;
    float texelY = (row + halfTexel);

    // Normalize to [0, 1] for texture sampling
    vec2 lutCoord = vec2(
        (col + 0.5) / uLutTexWidth,
        (row + 0.5) / uLutTexHeight
    );

    vec3 lutColor = texture2D(uLutSampler, lutCoord).rgb;

    // Step 4: Convert back to gamma space
    gl_FragColor = vec4(linearToSrgb(lutColor), color.a);
}
