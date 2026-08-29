package com.itantra.stt.model

/**
 * Supported Target Languages for STT Evaluation Bench.
 */
enum class Language(
    val code: String,
    val displayName: String,
    val isSupported: Boolean = true
) {
    EN("en", "English"),
    HI("hi", "Hindi"),
    GU("gu", "Gujarati"),
    MR("mr", "Marathi"),
    KN("kn", "Kannada"),
    ML("ml", "Malayalam"),
    TA("ta", "Tamil"),
    TE("te", "Telugu"),
    OR("or", "Odia", isSupported = false), // Enabled once verified mobile-compatible model added
    BN("bn", "Bengali");

    companion object {
        fun fromCode(code: String): Language? {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
        }
    }
}
