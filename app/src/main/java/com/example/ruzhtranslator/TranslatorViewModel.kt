package com.example.ruzhtranslator

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {

    private enum class Outcome { OK, EMPTY, FATAL }

    private val ctx: Context = application.applicationContext
    private val speech = SpeechManager(ctx)
    private val tts = TtsManager(ctx)
    private val translator = TranslatorManager()
    private val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    val messages = mutableStateListOf<ChatMessage>()

    var status by mutableStateOf("Готов")
        private set
    var running by mutableStateOf(false)
        private set
    var continuous by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)
        private set
    var ttsMissing by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var modelsReady = false

    fun reportNotice(text: String) {
        notice = text
    }

    fun dismissNotice() {
        notice = null
        ttsMissing = false
    }

    fun start() {
        if (running) return
        notice = null
        ttsMissing = false
        running = true
        job = viewModelScope.launch {
            try {
                runSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = "Ошибка: ${e.message}"
            } finally {
                finishSession()
            }
        }
    }

    fun stop() {
        job?.cancel()
        finishSession()
    }

    private fun finishSession() {
        running = false
        status = "Готов"
        abandonFocus()
        tts.stop()
    }

    private suspend fun runSession() {
        if (!speech.isAvailable()) {
            notice = "На устройстве нет службы распознавания речи. Установите/включите приложение «Google» и разрешите ему микрофон."
            return
        }
        if (!tts.awaitReady()) {
            notice = "Синтез речи (TTS) не удалось инициализировать. Установите «Службы речи Google»."
            ttsMissing = true
            return
        }
        val missing = Lang.values().filter { !tts.isLanguageAvailable(it.locale) }
        if (missing.isNotEmpty()) {
            notice = "Не установлен голосовой пакет TTS: " +
                missing.joinToString(", ") { it.title } +
                ". Установите голоса в настройках синтеза речи."
            ttsMissing = true
            return
        }
        if (!modelsReady) {
            status = "Загрузка языковых моделей..."
            try {
                translator.ensureModels()
                modelsReady = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = "Не удалось загрузить языковые модели перевода. Проверьте интернет (нужен только при первом запуске)."
                return
            }
        }

        var from = Lang.RU
        var emptyCount = 0
        while (true) {
            when (turn(from, from.other)) {
                Outcome.FATAL -> return
                Outcome.EMPTY -> {
                    emptyCount++
                    if (emptyCount >= 3) {
                        notice = "Речь не распознана. Остановлено."
                        return
                    }
                    continue // повторяем прослушивание того же языка
                }
                Outcome.OK -> emptyCount = 0
            }
            if (from == Lang.ZH && !continuous) return // «Один цикл» завершён
            from = from.other
        }
    }

    private suspend fun turn(from: Lang, to: Lang): Outcome {
        status = if (from == Lang.RU) "Слушаю русский..." else "Слушаю китайский..."
        val text = when (val res = speech.listen(from.tag)) {
            is SttResult.Text -> res.value
            SttResult.NoSpeech -> return Outcome.EMPTY
            is SttResult.Error -> {
                notice = res.message
                return Outcome.FATAL
            }
        }

        status = "Перевожу..."
        val translated = try {
            translator.translate(text, from, to)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notice = "Ошибка перевода: ${e.message}"
            return Outcome.FATAL
        }
        if (translated.isBlank()) return Outcome.EMPTY

        messages.add(ChatMessage(from = from, original = text, translated = translated))

        status = "Говорит перевод..."
        requestFocus()
        val spoken = try {
            tts.speak(translated, to.locale)
        } finally {
            abandonFocus()
        }
        if (!spoken) {
            notice = "Не удалось озвучить перевод (${to.title}). Проверьте голосовой пакет TTS."
            ttsMissing = true
            return Outcome.FATAL
        }

        // Пауза, чтобы микрофон не поймал «хвост» звука из динамика (MIUI/HyperOS).
        delay(450)
        return Outcome.OK
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    override fun onCleared() {
        job?.cancel()
        abandonFocus()
        tts.shutdown()
        translator.close()
        super.onCleared()
    }
}
