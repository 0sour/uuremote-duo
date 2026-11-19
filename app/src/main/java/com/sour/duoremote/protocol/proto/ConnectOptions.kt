package com.sour.duoremote.protocol.proto

/**
 * Minimal protobuf writer for com.remote.store.proto.Connect / Main.
 *
 * Reconstructed from the TV 3.5.0 protocol:
 *   Main { 1: 1, 2: -1, 3: Capture, 4: Decoder, 8: 2, 11: FeatureFlag }
 *   Capture { 1: fps-class, 2: quality, 3: 1, 4: 2, 5: Resolution, 6: Resolution }
 *   Decoder { 1: fps, 2: 1, 3: width, 4: height }
 *   Resolution { 1: width, 2: height }
 *
 * The control event prefixes these bytes with 0x04 on the wire.
 */
object ConnectOptions {

    fun encode(
        width: Int = 1920,
        height: Int = 1080,
        fps: Int = 60,
        quality: Int = 3,
        maxBitrateMbps: Int = 0,
        followLocal: Boolean = false,
        features: FeatureFlags = FeatureFlags(),
    ): ByteArray {
        require(width in 16..7680 && height in 16..4320) { "Invalid resolution" }
        require(fps in 1..240) { "Invalid fps" }
        require(quality in 0..10) { "Invalid quality" }
        require(maxBitrateMbps in 0..100) { "Invalid bitrate" }

        val resolution = ProtoWriter().apply {
            integer(1, width.toLong())
            integer(2, height.toLong())
        }.toByteArray()

        val capture = ProtoWriter().apply {
            integer(1, fpsClass(fps).toLong())
            integer(2, quality.toLong())
            integer(3, 1)
            // Keep the previously verified FOLLOW_LOCAL path for 1080p/window; pin smaller
            // explicit sizes with RESOLUTION so the host actually scales down.
            integer(4, if (followLocal || (width == 1920 && height == 1080)) 2 else 4)
            message(5, resolution)
            message(6, resolution)
            if (maxBitrateMbps > 0) integer(8, maxBitrateMbps.toLong())
        }.toByteArray()

        val decoder = ProtoWriter().apply {
            integer(1, fps.toLong())
            integer(2, 1)
            integer(3, width.toLong())
            integer(4, height.toLong())
        }.toByteArray()

        return ProtoWriter().apply {
            integer(1, 1)
            integer(2, -1L)
            message(3, capture)
            message(4, decoder)
            integer(8, 2)
            message(11, features.encode())
        }.toByteArray()
    }

    /** fps enum on the wire: 30=1, 60=2, 90=3, 144=4. */
    private fun fpsClass(fps: Int): Int = when (fps) {
        30 -> 1
        60 -> 2
        90 -> 3
        144 -> 4
        else -> 2
    }
}
