package com.example.ruzhtranslator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content
    )
}

@Composable
fun MainScreen(vm: TranslatorViewModel = viewModel()) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.start()
        } else {
            vm.reportNotice("Без разрешения на микрофон приложение работать не может. Включите его в настройках приложения.")
        }
    }

    fun onMainButton() {
        if (vm.running) {
            vm.stop()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) vm.start() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }

    Surface(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Русский ⇄ 中文", fontSize = 24.sp, fontWeight = FontWeight.Bold)

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = vm.status,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !vm.continuous,
                    onClick = { vm.continuous = false },
                    label = { Text("Один цикл") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = vm.continuous,
                    onClick = { vm.continuous = true },
                    label = { Text("Непрерывный диалог") },
                    modifier = Modifier.weight(1f)
                )
            }

            vm.notice?.let { text ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
                        Row {
                            if (vm.ttsMissing) {
                                TextButton(onClick = {
                                    try {
                                        context.startActivity(
                                            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                                        )
                                    } catch (_: Exception) {
                                        vm.reportNotice("Откройте: Настройки → Дополнительные настройки → Язык и ввод → Синтез речи, и скачайте голоса.")
                                    }
                                }) { Text("Установить голос") }
                            }
                            TextButton(onClick = { vm.dismissNotice() }) { Text("Закрыть") }
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (vm.messages.isEmpty()) {
                    Text(
                        "Нажмите «ПУСК» и говорите по-русски.\nПеревод прозвучит на китайском, затем приложение выслушает собеседника.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(vm.messages, key = { it.id }) { MessageBubble(it) }
                    }
                }
            }

            Button(
                onClick = { onMainButton() },
                modifier = Modifier.fillMaxWidth().height(96.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (vm.running) Color(0xFFC62828) else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (vm.running) "СТОП" else "ПУСК",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MessageBubble(m: ChatMessage) {
    val fromRu = m.from == Lang.RU
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromRu) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (fromRu) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(m.from.title, style = MaterialTheme.typography.labelSmall)
                Text(m.original, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    m.translated,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
