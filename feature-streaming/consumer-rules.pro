# RootEncoder 2.7.5 exposes no accessor for the configured video MediaCodec.
-keepclassmembers class com.pedro.library.base.Camera2Base {
    com.pedro.encoder.video.VideoEncoder videoEncoder;
}
-keepclassmembers class com.pedro.encoder.BaseEncoder {
    android.media.MediaCodec codec;
}
