package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.UiState
import com.example.data.model.Comment
import com.example.data.model.Post
import com.example.data.model.UserPublicProfile
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

enum class FeedSortMode { RECENT, PERTINENCE }

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(
    private val feedRepository: FeedRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _pageLimit = MutableStateFlow(25L)
    val sortMode = MutableStateFlow(FeedSortMode.RECENT)
    val selectedCategory = MutableStateFlow("Toutes")
    val searchQuery = MutableStateFlow("")

    val postsState: StateFlow<UiState<List<Post>>> = _pageLimit
        .flatMapLatest { limit -> feedRepository.observePublicPosts(limit) }
        .map<List<Post>, UiState<List<Post>>> { UiState.Success(it) }
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            Log.w(TAG, "Feed stream error", fs ?: error)
            emit(UiState.Error((fs ?: error).localizedMessage ?: "Erreur de chargement du fil."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _selectedPostId = MutableStateFlow<String?>(null)
    val activeComments: StateFlow<List<Comment>> = _selectedPostId
        .flatMapLatest { postId ->
            if (postId.isNullOrBlank()) flowOf(emptyList())
            else feedRepository.observeComments(postId)
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

    fun selectPostForComments(postId: String?) {
        _selectedPostId.value = postId
    }

    fun loadMore() {
        _pageLimit.value = (_pageLimit.value + 25L).coerceAtMost(200L)
    }

    fun createPost(
        profile: UserPublicProfile,
        content: String,
        category: String,
        imageData: String = "",
        attachmentName: String = "",
        attachmentType: String = "",
        attachmentUriString: String = "",
        groupId: String = ""
    ) {
        viewModelScope.launch {
            val res = feedRepository.createPost(
                authorName = profile.displayName,
                authorRole = profile.role,
                authorVerified = profile.verified,
                content = content,
                category = category,
                visibility = "PUBLIC",
                groupId = groupId,
                imageData = imageData,
                attachmentName = attachmentName,
                attachmentType = attachmentType,
                attachmentUriString = attachmentUriString
            )
            _feedback.value = if (res.isSuccess) "Publication ajoutée." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun updatePost(postId: String, content: String, category: String) {
        viewModelScope.launch {
            val res = feedRepository.updatePost(postId, content, category)
            _feedback.value = if (res.isSuccess) "Publication modifiée." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            val res = feedRepository.deletePost(postId)
            _feedback.value = if (res.isSuccess) "Publication supprimée." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun toggleReaction(post: Post, actorName: String) {
        viewModelScope.launch {
            val res = feedRepository.toggleReaction(post, "LIKE")
            if (res.isFailure) {
                _feedback.value = res.exceptionOrNull()?.localizedMessage
            }
        }
    }

    fun sharePost(post: Post) {
        viewModelScope.launch {
            feedRepository.incrementShare(post)
        }
    }

    fun addComment(post: Post, profile: UserPublicProfile, content: String, parentCommentId: String = "") {
        viewModelScope.launch {
            val res = feedRepository.addComment(
                targetId = post.id,
                targetType = "POST",
                parentCommentId = parentCommentId,
                authorName = profile.displayName,
                authorRole = profile.role,
                content = content,
                currentTargetCount = post.commentsCount
            )
            if (res.isFailure) {
                _feedback.value = res.exceptionOrNull()?.localizedMessage
            }
        }
    }

    fun reportContent(reporterName: String, targetType: String, targetId: String, reason: String, details: String) {
        viewModelScope.launch {
            val res = feedRepository.submitReport(reporterName, targetType, targetId, reason, details)
            _feedback.value = if (res.isSuccess) "Signalement transmis aux modérateurs." else res.exceptionOrNull()?.localizedMessage
        }
    }

    private companion object {
        const val TAG = "FeedVM"
    }
}
