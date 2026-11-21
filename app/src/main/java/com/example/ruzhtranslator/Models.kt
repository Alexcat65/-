package com.example.ruzhtranslator

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

enum class Lang(val code: String, val tag: String, val mlKit: String, val title: String) {
    RU("ru", "ru-RU", TranslateLanguage.RUSSIAN, "Русский"),
    ZH("zh", "zh-CN", TranslateLanguage.CHINESE, "Китайский");

    val locale: Locale get() = Locale.forLanguageTag(tag)
    val other: Lang get() = if (this == RU) ZH else RU
}

data class ChatMessage(
    val id: Long = System.nanoTime(),
    val from: Lang,
    val original: String,
    val translated: String
)
