package com.android.mdl.appreader.issuerauth.vical

class VicalDecoderException : Exception {
    constructor(message: String?) : super(message) {}
    constructor(message: String?, cause: Throwable?) : super(message, cause) {}
}
