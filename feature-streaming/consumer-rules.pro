# RootEncoder 2.7.5 exposes no accessor for the configured video MediaCodec.
-keepclassmembers class com.pedro.library.base.Camera2Base {
    com.pedro.encoder.video.VideoEncoder videoEncoder;
}
-keepclassmembers class com.pedro.encoder.BaseEncoder {
    android.media.MediaCodec codec;
}
# External capture adapter, pinned to RootEncoder 2.7.5.
-keepclassmembers class com.pedro.library.base.Camera2Base {
    com.pedro.encoder.input.video.Camera2ApiManager cameraManager;
    private void prepareGlView(int, int, int, int, int);
}
-keepclassmembers class com.pedro.encoder.input.video.Camera2ApiManager {
    boolean isRunning;
}
# Audio/video-only standalone recording transitions.
-keepclassmembers class com.pedro.library.base.Camera2Base {
    boolean audioInitialized;
}
