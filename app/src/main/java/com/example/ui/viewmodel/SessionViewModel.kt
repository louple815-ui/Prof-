package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.UiState
import com.example.data.model.AppNotification
import com.example.data.model.Bookmark
import com.example.data.model.UserPrivateInfo
import com.example.data.model.UserPublicProfile
import com.example.data.model.UserRole
import com.example.data.model.VerificationRequest
import com.example.data.repository.NotificationRepository
import com.example.data.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionViewModel(
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val currentUserId: String
) : ViewModel() {

    val profileState: StateFlow<UiState<UserPublicProfile?>> = userRepository.observeProfile(currentUserId)
        .map<UserPublicProfile?, UiState<UserPublicProfile?>> { UiState.Success(it) }
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            Log.w(TAG, "Profile stream error", fs ?: error)
            emit(UiState.Error((fs ?: error).localizedMessage ?: "Erreur chargement profil"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val privateInfo: StateFlow<UserPrivateInfo?> = userRepository.observePrivateInfo(currentUserId)
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val directory: StateFlow<List<UserPublicProfile>> = userRepository.observeDirectory()
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = userRepository.observeBookmarks(currentUserId)
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<AppNotification>> = notificationRepository.observeNotifications(currentUserId)
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val myVerifications: StateFlow<List<VerificationRequest>> = userRepository.observeMyVerificationRequests(currentUserId)
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _privileges = MutableStateFlow(false to false) // isAdmin, isModerator
    val privileges: StateFlow<Pair<Boolean, Boolean>> = _privileges.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        viewModelScope.launch {
            _privileges.value = userRepository.checkIsAdminOrModerator(currentUserId)
        }
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun createInitialProfile(
        firstName: String,
        lastName: String,
        role: UserRole,
        country: String,
        city: String,
        region: String,
        jobTitle: String,
        subject: String,
        schoolLevel: String,
        establishmentName: String,
        bio: String,
        photoData: String,
        preferredLanguage: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = userRepository.createInitialProfile(
                firstName, lastName, role, country, city, region,
                jobTitle, subject, schoolLevel, establishmentName, bio, photoData, preferredLanguage
            )
            _isBusy.value = false
            res.onFailure { _actionMessage.value = it.localizedMessage ?: "Impossible de créer le profil." }
        }
    }

    fun updateProfile(
        firstName: String,
        lastName: String,
        country: String,
        city: String,
        region: String,
        jobTitle: String,
        subject: String,
        schoolLevel: String,
        establishmentName: String,
        bio: String,
        photoData: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = userRepository.updateProfile(
                firstName, lastName, country, city, region,
                jobTitle, subject, schoolLevel, establishmentName, bio, photoData
            )
            _isBusy.value = false
            _actionMessage.value = if (res.isSuccess) "Profil mis à jour." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun submitVerification(userName: String, targetType: String, targetId: String, targetName: String, docInfo: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = userRepository.submitVerificationRequest(userName, targetType, targetId, targetName, docInfo)
            _isBusy.value = false
            _actionMessage.value = if (res.isSuccess) "Demande de vérification envoyée." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun toggleBookmark(targetId: String, targetType: String, title: String, subtitle: String, isBookmarked: Boolean) {
        viewModelScope.launch {
            val res = userRepository.toggleBookmark(targetId, targetType, title, subtitle, isBookmarked)
            if (res.isFailure) _actionMessage.value = res.exceptionOrNull()?.localizedMessage
        }
    }

    fun toggleBlockUser(targetUserId: String) {
        viewModelScope.launch {
            val blocked = privateInfo.value?.blockedUserIds.orEmpty()
            val res = userRepository.toggleBlockUser(targetUserId, blocked)
            _actionMessage.value = if (res.isSuccess) {
                if (targetUserId in blocked) "Utilisateur débloqué." else "Utilisateur bloqué."
            } else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun updateNotificationPrefs(
        notifyComments: Boolean,
        notifyReplies: Boolean,
        notifyMentions: Boolean,
        notifyMessages: Boolean,
        notifyInvitations: Boolean,
        notifyAnnouncements: Boolean,
        notifyGroups: Boolean,
        preferredLanguage: String
    ) {
        viewModelScope.launch {
            val res = userRepository.updateNotificationPreferences(
                notifyComments, notifyReplies, notifyMentions, notifyMessages,
                notifyInvitations, notifyAnnouncements, notifyGroups, preferredLanguage
            )
            _actionMessage.value = if (res.isSuccess) "Préférences enregistrées." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            val unread = notifications.value.filter { !it.read }.map { it.id }
            notificationRepository.markAllAsRead(unread)
        }
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = userRepository.deleteAccountCompletely()
            _isBusy.value = false
            if (res.isSuccess) onDeleted()
            else _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Suppression impossible (reconnectez-vous récemment)."
        }
    }

    private companion object {
        const val TAG = "SessionVM"
    }
}
