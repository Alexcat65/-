package com.example.ruzhtranslator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface SttResult {
    data class Text(val value: String) : SttResult
    object NoSpeech : SttResult
    data class Error(val message: String) : SttResult
}

/**
 * Одноразовое распознавание речи. Каждый вызов listen() создаёт новый SpeechRecognizer
 * (на MIUI/HyperOS это надёжнее, чем переиспользование). Вызывать только с главного потока.
 */
class SpeechManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    suspend fun listen(languageTag: String): SttResult = suspendCancellableCoroutine { cont ->
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val destroyed = AtomicBoolean(false)

        fun destroyLater() {
            mainHandler.post {
                if (destroyed.compareAndSet(false, true)) {
                    try {
                        recognizer.destroy()
                    } catch (_: Exception) {
                    }
                }
            }
        }

        fun finish(result: SttResult) {
            if (cont.isActive) cont.resume(result)
            destroyLater()
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                finish(mapError(error))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                finish(if (text.isEmpty()) SttResult.NoSpeech else SttResult.Text(text))
            }
        })

        cont.invokeOnCancellation { destroyLater() }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            finish(SttResult.Error("Не удалось запустить распознавание: ${e.message}"))
        }
    }

    private fun mapError(code: Int): SttResult = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_CLIENT -> SttResult.NoSpeech

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            SttResult.Error("Нет разрешения на микрофон.")

        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            SttResult.Error("Нет сети: распознавание речи Google требует интернет (или скачанные офлайн-пакеты речи).")

        SpeechRecognizer.ERROR_AUDIO ->
            SttResult.Error("Ошибка записи звука с микрофона.")

        SpeechRecognizer.ERROR_SERVER ->
            SttResult.Error("Ошибка сервера распознавания речи.")

        12, 13 ->
            SttResult.Error("Язык распознавания не поддерживается или не загружен (обновите приложение Google).")

        else -> SttResult.Error("Ошибка распознавания (код $code).")
    }
}
