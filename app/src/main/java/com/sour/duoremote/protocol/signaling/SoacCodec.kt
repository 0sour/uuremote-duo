package com.sour.duoremote.protocol.signaling

import com.google.gson.JsonObject

/**
 * Messages for the UU "soac" signaling event.
 *
 * The controller creates the offer; the host answers. Candidates must be nested
 * beneath data.candidate (the native SOAC shape), not flattened.
 */
object SoacCodec {

    fun description(
        clientId: String,
        type: String,
        sdp: String,
        iceId: String,
        appControlId: String,
    ): JsonObject {
        require(type == "offer" || type == "answer") { "Unsupported SDP type" }
        return JsonObject().apply {
            addProperty("client_id", clientId)
            add("data", JsonObject().apply {
                addProperty("type", type)
                addProperty("sdp", sdp)
                addProperty("ice_id", iceId)
                addProperty("app_control_id", appControlId)
            })
        }
    }

    fun candidate(
        clientId: String,
        candidate: JsonObject,
        iceId: String,
        appControlId: String,
    ): JsonObject {
        return JsonObject().apply {
            addProperty("client_id", clientId)
            add("data", JsonObject().apply {
                addProperty("type", "candidate")
                add("candidate", candidate)
                addProperty("ice_id", iceId)
                addProperty("app_control_id", appControlId)
            })
        }
    }

    fun candidateInit(candidate: String, sdpMid: String?, sdpMLineIndex: Int): JsonObject {
        return JsonObject().apply {
            addProperty("candidate", candidate)
            if (sdpMid != null) addProperty("sdpMid", sdpMid)
            addProperty("sdpMLineIndex", sdpMLineIndex)
        }
    }
}
