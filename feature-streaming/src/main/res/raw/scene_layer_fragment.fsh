#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform sampler2D uSampler;
uniform samplerExternalOES uScene;
uniform float uHidden;
uniform float uReady;
uniform float uMirrored;
varying vec2 vTextureCoord;
void main() {
    vec4 source = mix(texture2D(uSampler, vTextureCoord), vec4(0.0, 0.0, 0.0, 1.0), uHidden);
    // Canvas buffers are premultiplied. A second alpha multiplication darkens edges.
    // RootEncoder mirrors the final camera output. Compensate only the scene so
    // front-camera mirroring keeps text readable and placements unchanged.
    vec2 sceneCoord = vec2(mix(vTextureCoord.x, 1.0 - vTextureCoord.x, uMirrored), 1.0 - vTextureCoord.y);
    vec4 scene = texture2D(uScene, sceneCoord) * uReady;
    gl_FragColor = vec4(scene.rgb + source.rgb * (1.0 - scene.a), 1.0);
}
