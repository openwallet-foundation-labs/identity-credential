package org.multipaz.issuance.evidence

data class EvidenceRequestSelfieVideo(val poseSequence: List<Poses>): EvidenceRequest() {
    enum class Poses {
        FRONT,
        SMILE,
        TILT_HEAD_UP,
        TILT_HEAD_DOWN
    }
}
