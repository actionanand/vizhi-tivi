package com.actionanand.vizhitivi.core

import java.util.Locale

/** Display names are presentation only: stored channel language codes stay stable. */
fun displayLanguage(code: String, uiLocale: String = "en"): String {
    val normalized = code.trim().lowercase(Locale.ROOT).replace('_', '-')
    val unknown = when (uiLocale) {
        "ta" -> "தெரியாத மொழி"
        "sa" -> "अज्ञातभाषा"
        else -> "Unknown language"
    }
    if (normalized.isBlank() || normalized == "und" || normalized == "unknown") return unknown
    val aliases = mapOf(
        "tam" to "ta", "eng" to "en", "hin" to "hi", "mal" to "ml",
        "tel" to "te", "kan" to "kn", "sin" to "si", "san" to "sa",
        "ben" to "bn", "mar" to "mr", "pan" to "pa", "guj" to "gu",
        "urd" to "ur", "ori" to "or", "asm" to "as", "kas" to "ks"
    )
    val language = aliases[normalized] ?: normalized
    val target = when (uiLocale) {
        "ta" -> Locale.forLanguageTag("ta")
        "sa" -> Locale.forLanguageTag("sa")
        else -> Locale.ENGLISH
    }
    return runCatching {
        val translated = Locale.forLanguageTag(language).getDisplayLanguage(target)
        translated.takeIf { it.isNotBlank() && !it.equals(language, ignoreCase = true) }
            ?: Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH)
                .takeIf { it.isNotBlank() && !it.equals(language, ignoreCase = true) }
            ?: unknown
    }.getOrDefault(unknown)
}
