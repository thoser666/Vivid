# RootEncoder 2.7.5 exposes no accessor for the configured video MediaCodec.
-keepclassmembers class com.pedro.library.base.Camera2Base {
    com.pedro.encoder.video.VideoEncoder videoEncoder;
}
-keepclassmembers class com.pedro.encoder.BaseEncoder {
    android.media.MediaCodec codec;
}

# Pinned UVCAndroid 1.0.13 bridge opens already-authorized devices without its permission UI.
-keepclassmembers class com.serenegiant.usb.USBMonitor$UsbControlBlock {
    private <init>(com.serenegiant.usb.USBMonitor, android.hardware.usb.UsbDevice);
}
