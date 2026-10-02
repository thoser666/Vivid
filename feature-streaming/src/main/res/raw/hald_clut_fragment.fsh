precision highp float;

uniform sampler2D uSampler;
uniform sampler2D uLutSampler;
uniform float uLutSize;
uniform float uLutTexWidth;
uniform float uLutTexHeight;
uniform float uGamma;
varying vec2 vTextureCoord;

// Bitmap layout: row = red, column = green * N + blue.
vec3 sampleLut(vec3 index) {
    return texture2D(uLutSampler, vec2(
        (index.g * uLutSize + index.b + 0.5) / uLutTexWidth,
        (index.r + 0.5) / uLutTexHeight
    )).rgb;
}

void main() {
    vec4 color = texture2D(uSampler, vTextureCoord);
    vec3 index = clamp(pow(color.rgb, vec3(uGamma)), 0.0, 1.0) * (uLutSize - 1.0);
    vec3 lo = floor(index);
    vec3 hi = min(lo + 1.0, vec3(uLutSize - 1.0));
    vec3 blend = fract(index);

    // Interpolate all eight corners; hardware bilinear sampling cannot
    // interpolate across the flattened green/blue slice boundaries.
    vec3 r0g0 = mix(sampleLut(vec3(lo.r, lo.g, lo.b)), sampleLut(vec3(lo.r, lo.g, hi.b)), blend.b);
    vec3 r0g1 = mix(sampleLut(vec3(lo.r, hi.g, lo.b)), sampleLut(vec3(lo.r, hi.g, hi.b)), blend.b);
    vec3 r1g0 = mix(sampleLut(vec3(hi.r, lo.g, lo.b)), sampleLut(vec3(hi.r, lo.g, hi.b)), blend.b);
    vec3 r1g1 = mix(sampleLut(vec3(hi.r, hi.g, lo.b)), sampleLut(vec3(hi.r, hi.g, hi.b)), blend.b);
    vec3 lutColor = mix(mix(r0g0, r0g1, blend.g), mix(r1g0, r1g1, blend.g), blend.r);
    gl_FragColor = vec4(pow(max(lutColor, vec3(0.0)), vec3(1.0 / 2.2)), color.a);
}
