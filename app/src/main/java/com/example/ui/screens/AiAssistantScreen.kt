package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.LocalAppCopy
import com.example.data.repository.AiTaskMode
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.AiAssistantViewModel
import com.example.ui.viewmodel.AiUiState

@Composable
fun AiAssistantScreen(
    aiViewModel: AiAssistantViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val copy = LocalAppCopy.current
    val context = LocalContext.current
    val selectedMode by aiViewModel.selectedMode.collectAsStateWithLifecycle()
    val uiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    val history by aiViewModel.history.collectAsStateWithLifecycle()

    var promptText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(copy.aiAssistant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (copy.lang == "en") "Powered by Firebase AI Logic • Gemini" else "Propulsé par Firebase AI Logic • Gemini",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AiTaskMode.entries) { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { aiViewModel.selectMode(mode) },
                            label = { Text(if (copy.lang == "en") mode.labelEn else mode.labelFr) },
                            modifier = Modifier.testTag("ai_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (copy.lang == "en") {
                                    "Mode: ${selectedMode.labelEn}. Always verify official ministerial texts with competent authorities."
                                } else {
                                    "Mode actif : ${selectedMode.labelFr}. Vérifiez toujours les textes réglementaires auprès de votre ministère."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    when (val state = uiState) {
                        is AiUiState.Idle -> Unit
                        is AiUiState.Loading -> {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = NavyPrimary)
                                    Text(
                                        if (copy.lang == "en") "Drafting pedagogical response..." else "Préparation de la réponse pédagogique...",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        is AiUiState.Streaming -> {
                            item {
                                AiResponseCard(
                                    modeLabel = if (copy.lang == "en") selectedMode.labelEn else selectedMode.labelFr,
                                    body = state.text,
                                    isStreaming = true,
                                    onShare = {}
                                )
                            }
                        }
                        is AiUiState.Success -> {
                            item {
                                AiResponseCard(
                                    modeLabel = if (copy.lang == "en") selectedMode.labelEn else selectedMode.labelFr,
                                    body = state.text,
                                    isStreaming = false,
                                    onShare = {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, state.text)
                                        }
                                        context.startActivity(Intent.createChooser(share, "Partager"))
                                    }
                                )
                            }
                        }
                        is AiUiState.Error -> {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (history.size > 1) {
                        item {
                            Text(
                                if (copy.lang == "en") "Session history" else "Historique de la session",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        items(history.drop(1)) { exchange ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        if (copy.lang == "en") exchange.mode.labelEn else exchange.mode.labelFr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoldAccent
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(exchange.prompt, style = MaterialTheme.typography.labelLarge)
                                    Spacer(Modifier.height(6.dp))
                                    Text(exchange.response, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        placeholder = {
                            Text(
                                if (copy.lang == "en") "Describe your lesson, concept, or question..."
                                else "Décrivez votre cours, notion ou question..."
                            )
                        },
                        maxLines = 4,
                        modifier = Modifier.weight(1f).testTag("ai_prompt_input")
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val current = promptText
                            if (current.isNotBlank()) {
                                promptText = ""
                                aiViewModel.askAssistant(current, copy.lang)
                            }
                        },
                        enabled = uiState !is AiUiState.Loading && uiState !is AiUiState.Streaming && promptText.isNotBlank(),
                        modifier = Modifier.testTag("ai_send_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer à l'assistant IA")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiResponseCard(
    modeLabel: String,
    body: String,
    isStreaming: Boolean,
    onShare: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(modeLabel, style = MaterialTheme.typography.labelLarge, color = GoldAccent)
                if (!isStreaming) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "Exporter / Partager", modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (isStreaming) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
