package com.example.ruzhtranslator

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/** Обёртка над ML Kit On-Device Translation. */
class TranslatorManager {

    private val clients = mutableMapOf<String, Translator>()

    private fun client(from: Lang, to: Lang): Translator =
        clients.getOrPut("${from.code}>${to.code}") {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(from.mlKit)
                .setTargetLanguage(to.mlKit)
                .build()
            Translation.getClient(options)
        }

    /** Скачивает модели (нужен интернет только при первом запуске). */
    suspend fun ensureModels() {
        val conditions = DownloadConditions.Builder().build()
        client(Lang.RU, Lang.ZH).downloadModelIfNeeded(conditions).await()
        client(Lang.ZH, Lang.RU).downloadModelIfNeeded(conditions).await()
    }

    suspend fun translate(text: String, from: Lang, to: Lang): String =
        client(from, to).translate(text).await()

    fun close() {
        clients.values.forEach { it.close() }
        clients.clear()
    }
}
