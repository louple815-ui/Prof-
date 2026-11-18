package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.UiState
import com.example.data.local.DownloadedResourceEntity
import com.example.data.model.Group
import com.example.data.model.GroupMember
import com.example.data.model.Post
import com.example.data.model.ResourceItem
import com.example.data.model.UserPublicProfile
import com.example.data.repository.FeedRepository
import com.example.data.repository.GroupRepository
import com.example.data.repository.ResourceRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModel(
    private val groupRepository: GroupRepository,
    private val resourceRepository: ResourceRepository,
    private val feedRepository: FeedRepository,
    currentUserId: String
) : ViewModel() {

    val publicGroupsState: StateFlow<UiState<List<Group>>> = combine(
        groupRepository.observePublicGroups(),
        groupRepository.observeOwnedGroups(currentUserId)
    ) { pub, owned ->
        (pub + owned).distinctBy { it.id }.sortedByDescending { it.membersCount }
    }
        .map<List<Group>, UiState<List<Group>>> { UiState.Success(it) }
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            Log.w(TAG, "Groups error", fs ?: error)
            emit(UiState.Error((fs ?: error).localizedMessage ?: "Erreur chargement groupes."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val myMemberships: StateFlow<List<GroupMember>> = groupRepository.observeMyMemberships(currentUserId)
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupPosts: StateFlow<List<Post>> = _selectedGroupId
        .flatMapLatest { gid -> if (gid.isNullOrBlank()) flowOf(emptyList()) else groupRepository.observeGroupPosts(gid) }
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val selectedGroupMembers: StateFlow<List<GroupMember>> = _selectedGroupId
        .flatMapLatest { gid -> if (gid.isNullOrBlank()) flowOf(emptyList()) else groupRepository.observeGroupMembers(gid) }
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val resourcesState: StateFlow<UiState<List<ResourceItem>>> = resourceRepository.observePublicResources()
        .map<List<ResourceItem>, UiState<List<ResourceItem>>> { UiState.Success(it) }
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            Log.w(TAG, "Resources error", fs ?: error)
            emit(UiState.Error((fs ?: error).localizedMessage ?: "Erreur chargement ressources."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val offlineResources: StateFlow<List<DownloadedResourceEntity>> = resourceRepository.observeOfflineDownloaded()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    fun selectGroup(groupId: String?) {
        _selectedGroupId.value = groupId
    }

    fun createGroup(
        profile: UserPublicProfile,
        name: String,
        description: String,
        domain: String,
        subject: String,
        level: String,
        region: String,
        privacy: String,
        rules: String
    ) {
        viewModelScope.launch {
            val res = groupRepository.createGroup(
                ownerName = profile.displayName,
                ownerRole = profile.role,
                name = name,
                description = description,
                domain = domain,
                subject = subject,
                level = level,
                region = region,
                privacy = privacy,
                rules = rules
            )
            _feedback.value = if (res.isSuccess) "Groupe créé avec succès." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun joinGroup(group: Group, profile: UserPublicProfile) {
        viewModelScope.launch {
            val res = groupRepository.joinGroup(group, profile.displayName, profile.role)
            _feedback.value = if (res.isSuccess) {
                if (group.privacy == "PUBLIC") "Vous avez rejoint le groupe." else "Demande d'adhésion envoyée."
            } else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun leaveGroup(group: Group) {
        viewModelScope.launch {
            val res = groupRepository.leaveGroup(group)
            _feedback.value = if (res.isSuccess) "Vous avez quitté le groupe." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun publishGroupPost(profile: UserPublicProfile, groupId: String, content: String) {
        viewModelScope.launch {
            val res = feedRepository.createPost(
                authorName = profile.displayName,
                authorRole = profile.role,
                authorVerified = profile.verified,
                content = content,
                category = "Groupe",
                visibility = "PUBLIC",
                groupId = groupId
            )
            _feedback.value = if (res.isSuccess) "Publication ajoutée au groupe." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun publishResource(
        profile: UserPublicProfile,
        title: String,
        description: String,
        resourceType: String,
        subject: String,
        level: String,
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        contentBody: String
    ) {
        viewModelScope.launch {
            val res = resourceRepository.publishResource(
                authorName = profile.displayName,
                title = title,
                description = description,
                resourceType = resourceType,
                subject = subject,
                level = level,
                fileName = fileName,
                mimeType = mimeType,
                fileSizeBytes = fileSizeBytes,
                contentBody = contentBody
            )
            _feedback.value = if (res.isSuccess) "Ressource pédagogique partagée." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun downloadResourceOffline(resource: ResourceItem) {
        viewModelScope.launch {
            val res = resourceRepository.downloadForOffline(resource)
            _feedback.value = if (res.isSuccess) {
                "Ressource enregistrée pour consultation hors connexion."
            } else {
                res.exceptionOrNull()?.localizedMessage
            }
        }
    }

    fun removeOfflineResource(resourceId: String) {
        viewModelScope.launch {
            resourceRepository.removeOfflineResource(resourceId)
            _feedback.value = "Copie locale supprimée."
        }
    }

    private companion object {
        const val TAG = "CommunityVM"
    }
}
