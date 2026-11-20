package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.UiState
import com.example.data.model.Comment
import com.example.data.model.Debate
import com.example.data.model.Poll
import com.example.data.model.UserPublicProfile
import com.example.data.model.Vote
import com.example.data.repository.DebateRepository
import com.example.data.repository.FeedRepository
import com.example.data.repository.NotificationRepository
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
class DebateViewModel(
    private val debateRepository: DebateRepository,
    private val feedRepository: FeedRepository,
    private val notificationRepository: NotificationRepository,
    currentUserId: String
) : ViewModel() {

    val selectedCategory = MutableStateFlow("Toutes")

    val debatesState: StateFlow<UiState<List<Debate>>> = debateRepository.observeDebates()
        .map<List<Debate>, UiState<List<Debate>>> { UiState.Success(it) }
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            Log.w(TAG, "Debates error", fs ?: error)
            emit(UiState.Error((fs ?: error).localizedMessage ?: "Erreur chargement débats."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val polls: StateFlow<List<Poll>> = debateRepository.observePolls()
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userVotes: StateFlow<List<Vote>> = debateRepository.observeUserVotes(currentUserId)
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _selectedDebateId = MutableStateFlow<String?>(null)
    val debateArguments: StateFlow<List<Comment>> = _selectedDebateId
        .flatMapLatest { debateId ->
            if (debateId.isNullOrBlank()) flowOf(emptyList())
            else feedRepository.observeComments(debateId)
        }
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    fun selectDebate(debateId: String?) {
        _selectedDebateId.value = debateId
    }

    fun createDebate(
        profile: UserPublicProfile,
        title: String,
        question: String,
        category: String,
        pollQuestion: String?,
        pollOptions: List<String>
    ) {
        viewModelScope.launch {
            val res = debateRepository.createDebateWithOptionalPoll(
                authorName = profile.displayName,
                authorVerified = profile.verified,
                title = title,
                question = question,
                category = category,
                pollQuestion = pollQuestion,
                pollOptions = pollOptions
            )
            _feedback.value = if (res.isSuccess) "Débat publié." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun addArgument(debate: Debate, profile: UserPublicProfile, argumentText: String, parentCommentId: String = "") {
        viewModelScope.launch {
            val res = feedRepository.addComment(
                targetId = debate.id,
                targetType = "DEBATE",
                parentCommentId = parentCommentId,
                authorName = profile.displayName,
                authorRole = profile.role,
                content = argumentText,
                currentTargetCount = debate.argumentsCount
            )
            if (res.isFailure) {
                _feedback.value = res.exceptionOrNull()?.localizedMessage
            }
        }
    }

    fun voteInPoll(poll: Poll, optionIndex: Int) {
        viewModelScope.launch {
            val res = debateRepository.castPollVote(poll, optionIndex)
            _feedback.value = if (res.isSuccess) "Vote enregistré." else res.exceptionOrNull()?.localizedMessage
        }
    }

    private companion object {
        const val TAG = "DebateVM"
    }
}
