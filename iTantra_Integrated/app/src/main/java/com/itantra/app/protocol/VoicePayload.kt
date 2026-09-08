package com.itantra.app.protocol

import com.itantra.stt.model.Language

/**
 * Wire format for a single spoken turn travelling over the bitchat mesh.
 *
 * The mesh transport carries an opaque UTF-8 string per message, so the pipeline
 * metadata (language tag, priority flag, capture timestamp) is packed into that
 * string ahead of the transcript itself:
 *
 *     ITV1|<languageCode>|<priority>|<capturedAtMillis>|<transcript>
 *
 * The transcript is the final field and is never split, so it may legally contain
 * the '|' separator. Anything that does not start with the "ITV1" magic is treated
 * as a legacy plain-text message (e.g. from the pre-integration mesh test app) and
 * is still delivered to TTS using the receiver's currently selected language.
 */
data class VoicePayload(
    val text: String,
    val languageCode: String,
    val priority: Priority,
    val capturedAtMillis: Long
) {

    enum class Priority {
        NORMAL,
        ALERT;

        companion object {
            fun fromToken(token: String): Priority =
                entries.firstOrNull { it.name.equals(token, ignoreCase = true) } ?: NORMAL
        }
    }

    /** Resolved [Language], or null when the sender used an unknown/legacy tag. */
    fun language(): Language? = Language.fromCode(languageCode)

    fun encode(): String =
        listOf(MAGIC, languageCode, priority.name, capturedAtMillis.toString(), text)
            .joinToString(FIELD_SEPARATOR)

    companion object {
        const val MAGIC = "ITV1"
        private const val FIELD_SEPARATOR = "|"
        const val UNKNOWN_LANGUAGE = "und"

        fun create(
            text: String,
            language: Language,
            priority: Priority,
            capturedAtMillis: Long = System.currentTimeMillis()
        ): VoicePayload = VoicePayload(
            text = text.trim(),
            languageCode = language.code,
            priority = priority,
            capturedAtMillis = capturedAtMillis
        )

        /**
         * Parses a mesh message body. Never throws: malformed or legacy bodies come
         * back as a NORMAL-priority payload with an unknown language tag.
         */
        fun decode(raw: String): VoicePayload {
            val parts = raw.split(FIELD_SEPARATOR, limit = 5)
            if (parts.size < 5 || parts[0] != MAGIC) {
                return VoicePayload(
                    text = raw,
                    languageCode = UNKNOWN_LANGUAGE,
                    priority = Priority.NORMAL,
                    capturedAtMillis = System.currentTimeMillis()
                )
            }
            return VoicePayload(
                text = parts[4],
                languageCode = parts[1].ifBlank { UNKNOWN_LANGUAGE },
                priority = Priority.fromToken(parts[2]),
                capturedAtMillis = parts[3].toLongOrNull() ?: System.currentTimeMillis()
            )
        }
    }
}
