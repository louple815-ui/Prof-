package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AiAssistantRepository
import com.example.data.repository.AiTaskMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Streaming(val text: String) : AiUiState
    data class Success(val text: String) : AiUiState
    data class Error(val message: String) : AiUiState
}

data class AiExchange(
    val mode: AiTaskMode,
    val prompt: String,
    val response: String
)

class AiAssistantViewModel(
    private val aiRepository: AiAssistantRepository
) : ViewModel() {

    private val _selectedMode = MutableStateFlow(AiTaskMode.PREP_COURS)
    val selectedMode: StateFlow<AiTaskMode> = _selectedMode.asStateFlow()

    private val _uiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<List<AiExchange>>(emptyList())
    val history: StateFlow<List<AiExchange>> = _history.asStateFlow()

    fun selectMode(mode: AiTaskMode) {
        _selectedMode.value = mode
    }

    fun askAssistant(prompt: String, lang: String) {
        val cleaned = prompt.trim()
        if (cleaned.isEmpty()) return
        if (cleaned.length > AiAssistantRepository.MAX_PROMPT_CHARS) {
            _uiState.value = AiUiState.Error(
                if (lang == "en") {
                    "Prompt exceeds the ${AiAssistantRepository.MAX_PROMPT_CHARS}-character limit."
                } else {
                    "Votre demande dépasse la limite de ${AiAssistantRepository.MAX_PROMPT_CHARS} caractères."
                }
            )
            return
        }
        val mode = _selectedMode.value
        viewModelScope.launch {
            _uiState.value = AiUiState.Loading
            var finalText = ""
            try {
                aiRepository.streamResponse(mode, cleaned, lang).collect { partial ->
                    finalText = partial
                    _uiState.value = AiUiState.Streaming(partial)
                }
                if (finalText.isNotBlank()) {
                    _uiState.value = AiUiState.Success(finalText)
                    _history.value = listOf(AiExchange(mode, cleaned, finalText)) + _history.value
                } else {
                    _uiState.value = AiUiState.Error(
                        if (lang == "en") "The assistant returned an empty response."
                        else "L'assistant n'a renvoyé aucune réponse."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI generation timed out", e)
                _uiState.value = AiUiState.Error(
                    if (lang == "en") "Request timed out (60s). Please try a shorter prompt."
                    else "Délai dépassé (60s). Veuillez réessayer avec une demande plus courte."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI generation failed", e)
                _uiState.value = AiUiState.Error(
                    e.localizedMessage ?: "Impossible de contacter l'assistant IA Gemini."
                )
            }
        }
    }

    private companion object {
        const val TAG = "AiAssistantVM"
    }
}
