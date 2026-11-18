package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminLog
import com.example.data.model.AppCategory
import com.example.data.model.ReportItem
import com.example.data.model.UserRole
import com.example.data.model.VerificationRequest
import com.example.data.repository.AdminRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModel(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)

    val reports: StateFlow<List<ReportItem>> = adminRepository.observeReports()
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val verificationRequests: StateFlow<List<VerificationRequest>> = _isAdmin
        .flatMapLatest { allowed ->
            if (!allowed) flowOf(emptyList()) else adminRepository.observeVerificationRequests()
        }
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminLogs: StateFlow<List<AdminLog>> = _isAdmin
        .flatMapLatest { allowed ->
            if (!allowed) flowOf(emptyList()) else adminRepository.observeAdminLogs()
        }
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val categories: StateFlow<List<AppCategory>> = adminRepository.observeCategories()
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun setAdminAccess(isAdmin: Boolean) {
        _isAdmin.value = isAdmin
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun reviewVerification(request: VerificationRequest, approve: Boolean, note: String, adminName: String) {
        viewModelScope.launch {
            val res = adminRepository.reviewVerification(request, approve, note, adminName)
            _feedback.value = if (res.isSuccess) {
                if (approve) "Vérification approuvée." else "Vérification refusée."
            } else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun resolveReport(report: ReportItem, status: String, removeContent: Boolean, adminName: String) {
        viewModelScope.launch {
            val res = adminRepository.resolveReport(report, status, removeContent, adminName)
            _feedback.value = if (res.isSuccess) "Signalement traité." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun toggleSuspendUser(userId: String, suspend: Boolean, adminName: String) {
        viewModelScope.launch {
            val res = adminRepository.toggleUserSuspension(userId, suspend, adminName)
            _feedback.value = if (res.isSuccess) {
                if (suspend) "Compte suspendu." else "Compte réactivé."
            } else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun assignRole(userId: String, role: UserRole, adminName: String) {
        viewModelScope.launch {
            val res = adminRepository.assignRole(userId, role, adminName)
            _feedback.value = if (res.isSuccess) "Rôle mis à jour : ${role.labelFr}" else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun addCategory(name: String, kind: String, adminName: String) {
        viewModelScope.launch {
            val res = adminRepository.addCategory(name, kind, adminName)
            _feedback.value = if (res.isSuccess) "Catégorie ajoutée." else res.exceptionOrNull()?.localizedMessage
        }
    }
}
