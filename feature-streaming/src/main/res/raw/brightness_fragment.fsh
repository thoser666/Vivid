#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform sampler2D uSampler;
varying vec2 vTextureCoord;

// Low-Light-Boost: multiplies RGB channels to brighten the image.
// 1.5x is a good default — visible improvement without washing out highlights.
// For a more aggressive boost, increase BRIGHTNESS (max ~2.5 before clipping).
const float BRIGHTNESS = 1.5;

void main() {
    vec4 color = texture2D(uSampler, vTextureCoord);
    color.rgb *= BRIGHTNESS;
    gl_FragColor = color;
}
