package com.example.data.repository

import android.net.Uri
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.Bookmark
import com.example.data.model.UserPrivateInfo
import com.example.data.model.UserPublicProfile
import com.example.data.model.UserRole
import com.example.data.model.VerificationRequest
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val storageRepository: StorageRepository = StorageRepository()
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Authentification Google requise.")

    fun observeProfile(userId: String): Flow<UserPublicProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserPublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.GET, path)
                    throw error
                }
        )
    }

    fun observePrivateInfo(userId: String): Flow<UserPrivateInfo?> = flow {
        val path = "users/$userId/private/info"
        emitAll(
            db.collection("users").document(userId).collection("private").document("info")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserPrivateInfo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) handleFirestoreError(fsError ?: error, OperationType.GET, path)
                    throw error
                }
        )
    }

    fun observeDirectory(): Flow<List<UserPublicProfile>> = flow {
        val path = "users"
        emitAll(
            db.collection("users")
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserPublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeBookmarks(userId: String): Flow<List<Bookmark>> = flow {
        val path = "users/$userId/bookmarks"
        emitAll(
            db.collection("users").document(userId).collection("bookmarks")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Bookmark::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeMyVerificationRequests(userId: String): Flow<List<VerificationRequest>> = flow {
        val path = "verification_requests"
        emitAll(
            db.collection("verification_requests")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VerificationRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createInitialProfile(
        firstName: String,
        lastName: String,
        requestedRole: UserRole,
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
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Non connecté"))
        val path = "users/$uid"
        return try {
            val safeRole = if (requestedRole == UserRole.RESPONSABLE_ETABLISSEMENT) {
                UserRole.RESPONSABLE_ETABLISSEMENT.name
            } else {
                UserRole.ENSEIGNANT.name
            }
            val uploadedPhoto = when {
                StorageRepository.isLocalUri(photoData) -> storageRepository.uploadProfilePhoto(uid, Uri.parse(photoData))
                else -> null
            }
            val displayName = "${firstName.trim()} ${lastName.trim()}".trim()
            val publicPayload = buildMap<String, Any> {
                put("userId", uid)
                put("firstName", firstName.trim().take(60))
                put("lastName", lastName.trim().take(60))
                put("displayName", displayName.take(120))
                put("role", safeRole)
                put("country", country.trim().ifBlank { "Burkina Faso" }.take(80))
                put("city", city.trim().ifBlank { "Ouagadougou" }.take(80))
                put("region", region.trim().ifBlank { "Centre" }.take(80))
                if (jobTitle.isNotBlank()) put("jobTitle", jobTitle.trim().take(100))
                if (subject.isNotBlank()) put("subject", subject.trim().take(80))
                if (schoolLevel.isNotBlank()) put("schoolLevel", schoolLevel.trim().take(80))
                if (establishmentName.isNotBlank()) put("establishmentName", establishmentName.trim().take(120))
                if (bio.isNotBlank()) put("bio", bio.trim().take(600))
                if (uploadedPhoto != null) {
                    put("photoUrl", uploadedPhoto.downloadUrl)
                    put("photoStoragePath", uploadedPhoto.storagePath)
                } else if (StorageRepository.isRemoteHttpsUrl(photoData)) {
                    put("photoUrl", photoData)
                }
                put("verified", false)
                put("suspended", false)
                put("discoverable", true)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            val email = auth.currentUser?.email.orEmpty()
            val privatePayload = buildMap<String, Any> {
                put("userId", uid)
                if (email.isNotBlank()) put("email", email.take(160))
                put("notifyComments", true)
                put("notifyReplies", true)
                put("notifyMentions", true)
                put("notifyMessages", true)
                put("notifyInvitations", true)
                put("notifyAnnouncements", true)
                put("notifyGroups", true)
                put("blockedUserIds", emptyList<String>())
                put("preferredLanguage", if (preferredLanguage == "en") "en" else "fr")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            val batch = db.batch()
            batch.set(db.collection("users").document(uid), publicPayload)
            batch.set(db.collection("users").document(uid).collection("private").document("info"), privatePayload)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
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
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Non connecté"))
        val path = "users/$uid"
        return try {
            val uploadedPhoto = when {
                StorageRepository.isLocalUri(photoData) -> storageRepository.uploadProfilePhoto(uid, Uri.parse(photoData))
                else -> null
            }
            val displayName = "${firstName.trim()} ${lastName.trim()}".trim()
            val updates = buildMap<String, Any> {
                put("firstName", firstName.trim().take(60))
                put("lastName", lastName.trim().take(60))
                put("displayName", displayName.take(120))
                put("country", country.trim().take(80))
                put("city", city.trim().take(80))
                put("region", region.trim().take(80))
                put("jobTitle", jobTitle.trim().take(100))
                put("subject", subject.trim().take(80))
                put("schoolLevel", schoolLevel.trim().take(80))
                put("establishmentName", establishmentName.trim().take(120))
                put("bio", bio.trim().take(600))
                if (uploadedPhoto != null) {
                    put("photoUrl", uploadedPhoto.downloadUrl)
                    put("photoStoragePath", uploadedPhoto.storagePath)
                } else if (StorageRepository.isRemoteHttpsUrl(photoData)) {
                    put("photoUrl", photoData)
                }
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateNotificationPreferences(
        notifyComments: Boolean,
        notifyReplies: Boolean,
        notifyMentions: Boolean,
        notifyMessages: Boolean,
        notifyInvitations: Boolean,
        notifyAnnouncements: Boolean,
        notifyGroups: Boolean,
        preferredLanguage: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Non connecté"))
        val path = "users/$uid/private/info"
        return try {
            db.document(path).update(
                mapOf(
                    "notifyComments" to notifyComments,
                    "notifyReplies" to notifyReplies,
                    "notifyMentions" to notifyMentions,
                    "notifyMessages" to notifyMessages,
                    "notifyInvitations" to notifyInvitations,
                    "notifyAnnouncements" to notifyAnnouncements,
                    "notifyGroups" to notifyGroups,
                    "preferredLanguage" to if (preferredLanguage == "en") "en" else "fr",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleBlockUser(targetUserId: String, currentBlocked: List<String>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Non connecté"))
        val path = "users/$uid/private/info"
        return try {
            val updated = if (targetUserId in currentBlocked) {
                currentBlocked - targetUserId
            } else {
                (currentBlocked + targetUserId).distinct().take(100)
            }
            db.document(path).update(
                mapOf(
                    "blockedUserIds" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleBookmark(
        targetId: String,
        targetType: String,
        title: String,
        subtitle: String,
        isCurrentlyBookmarked: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Non connecté"))
        val docRef = db.collection("users").document(uid).collection("bookmarks").document(targetId)
        return try {
            if (isCurrentlyBookmarked) {
                docRef.delete().await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "targetId" to targetId,
                        "targetType" to targetType,
                        "title" to title.take(200),
                        "subtitle" to subtitle.take(300),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun submitVerificationRequest(
        userName: String,
        targetType: String,
        targetId: String,
        targetName: String,
        documentInfo: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Non connecté"))
        val docRef = db.collection("verification_requests").document()
        return try {
            require(documentInfo.trim().length >= 5) { "Veuillez fournir des références justificatives d'au moins 5 caractères." }
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "userName" to userName.take(120),
                    "targetType" to targetType,
                    "targetId" to targetId,
                    "targetName" to targetName.take(140),
                    "documentInfo" to documentInfo.trim().take(600),
                    "status" to "PENDING",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun checkIsAdminOrModerator(uid: String): Pair<Boolean, Boolean> {
        return try {
            val email = auth.currentUser?.email
            val verified = auth.currentUser?.isEmailVerified == true
            val isBootstrapAdmin = email == "louple815@gmail.com" && verified
            val adminDoc = db.collection("admins").document(uid).get().await().exists()
            val modDoc = db.collection("moderators").document(uid).get().await().exists()
            val isAdmin = isBootstrapAdmin || adminDoc
            val isMod = isAdmin || modDoc
            isAdmin to isMod
        } catch (_: Exception) {
            false to false
        }
    }

    suspend fun deleteAccountCompletely(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Non connecté"))
        val uid = user.uid
        return try {
            val bookmarks = db.collection("users").document(uid).collection("bookmarks").get().await()
            val batch = db.batch()
            bookmarks.documents.forEach { batch.delete(it.reference) }
            batch.delete(db.collection("users").document(uid).collection("private").document("info"))
            batch.delete(db.collection("users").document(uid))
            batch.commit().await()
            user.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }
}
