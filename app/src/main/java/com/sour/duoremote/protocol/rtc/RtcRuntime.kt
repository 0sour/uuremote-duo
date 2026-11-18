package com.sour.duoremote.protocol.rtc

import org.webrtc.EglBase

/**
 * Process-wide EGL context shared by the video decoder and SurfaceViewRenderer.
 * Keeping it alive across Activity/Presentation recreation avoids releasing the
 * context while WebRTC is still tearing down a peer connection.
 */
object RtcRuntime {
    val eglBase: EglBase by lazy { EglBase.create() }
}
