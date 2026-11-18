package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.UiState
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.Establishment
import com.example.data.model.UserPublicProfile
import com.example.data.repository.EstablishmentRepository
import com.example.data.repository.MessagingRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CommunicationViewModel(
    private val messagingRepository: MessagingRepository,
    private val establishmentRepository: EstablishmentRepository,
    private val currentUserId: String
) : ViewModel() {

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        messagingRepository.observeConversations(currentUserId)
            .map<List<Conversation>, UiState<List<Conversation>>> { UiState.Success(it) }
            .catch { error ->
                val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fs == null && error is CancellationException) throw error
                Log.w(TAG, "Conversations error", fs ?: error)
                emit(UiState.Error((fs ?: error).localizedMessage ?: "Erreur messagerie."))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    val activeMessages: StateFlow<List<ChatMessage>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId.isNullOrBlank()) flowOf(emptyList())
            else messagingRepository.observeMessages(currentUserId, convId)
        }
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val establishmentsState: StateFlow<UiState<List<Establishment>>> =
        establishmentRepository.observeEstablishments()
            .map<List<Establishment>, UiState<List<Establishment>>> { UiState.Success(it) }
            .catch { error ->
                val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fs == null && error is CancellationException) throw error
                emit(UiState.Error((fs ?: error).localizedMessage ?: "Erreur établissements."))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    fun openConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
    }

    fun startDirectConversation(myProfile: UserPublicProfile, target: UserPublicProfile, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            val res = messagingRepository.createOrOpenDirectConversation(
                myName = myProfile.displayName,
                targetUserId = target.userId,
                targetUserName = target.displayName
            )
            res.onSuccess { convId ->
                _activeConversationId.value = convId
                onOpened(convId)
            }.onFailure {
                _feedback.value = it.localizedMessage
            }
        }
    }

    fun startGroupConversation(
        myProfile: UserPublicProfile,
        title: String,
        targets: List<UserPublicProfile>,
        onOpened: (String) -> Unit
    ) {
        viewModelScope.launch {
            val ids = targets.map { it.userId }
            val summary = (listOf(myProfile.displayName) + targets.map { it.displayName }).joinToString(", ")
            val res = messagingRepository.createGroupConversation(
                myName = myProfile.displayName,
                title = title,
                participantIds = ids,
                participantSummary = summary
            )
            res.onSuccess { convId ->
                _activeConversationId.value = convId
                onOpened(convId)
            }.onFailure {
                _feedback.value = it.localizedMessage
            }
        }
    }

    fun sendMessage(
        conversation: Conversation,
        senderName: String,
        text: String,
        attachmentName: String = "",
        attachmentData: String = ""
    ) {
        viewModelScope.launch {
            val res = messagingRepository.sendMessage(conversation, senderName, text, attachmentName, attachmentData)
            if (res.isFailure) _feedback.value = res.exceptionOrNull()?.localizedMessage
        }
    }

    fun createEstablishment(
        profile: UserPublicProfile,
        officialName: String,
        country: String,
        city: String,
        region: String,
        establishmentType: String,
        description: String,
        announcement: String
    ) {
        viewModelScope.launch {
            val res = establishmentRepository.createEstablishment(
                directorName = profile.displayName,
                officialName = officialName,
                country = country,
                city = city,
                region = region,
                establishmentType = establishmentType,
                description = description,
                announcement = announcement
            )
            _feedback.value = if (res.isSuccess) "Page établissement créée." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun updateAnnouncement(establishmentId: String, announcement: String) {
        viewModelScope.launch {
            val res = establishmentRepository.updateAnnouncement(establishmentId, announcement)
            _feedback.value = if (res.isSuccess) "Annonce mise à jour." else res.exceptionOrNull()?.localizedMessage
        }
    }

    private companion object {
        const val TAG = "CommVM"
    }
}
