package com.example.data.repository

import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AdminLog
import com.example.data.model.AppCategory
import com.example.data.model.ReportItem
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

class AdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Utilisateur non connecté.")

    fun observeReports(): Flow<List<ReportItem>> = flow {
        val path = "reports"
        emitAll(
            db.collection("reports")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ReportItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeVerificationRequests(): Flow<List<VerificationRequest>> = flow {
        val path = "verification_requests"
        emitAll(
            db.collection("verification_requests")
                .snapshots()
                .map { snap ->
                    snap.toObjects(VerificationRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeAdminLogs(): Flow<List<AdminLog>> = flow {
        val path = "admin_logs"
        emitAll(
            db.collection("admin_logs")
                .snapshots()
                .map { snap ->
                    snap.toObjects(AdminLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeCategories(): Flow<List<AppCategory>> = flow {
        val path = "app_categories"
        emitAll(
            db.collection("app_categories")
                .whereEqualTo("visibility", "PUBLIC")
                .snapshots()
                .map { snap ->
                    snap.toObjects(AppCategory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun reviewVerification(
        request: VerificationRequest,
        approve: Boolean,
        note: String,
        adminName: String
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val status = if (approve) "APPROVED" else "REJECTED"
            val batch = db.batch()
            batch.update(
                db.collection("verification_requests").document(request.id),
                mapOf(
                    "status" to status,
                    "reviewedBy" to uid,
                    "reviewNote" to note.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (approve) {
                if (request.targetType == "TEACHER") {
                    batch.update(
                        db.collection("users").document(request.targetId),
                        mapOf("verified" to true, "updatedAt" to FieldValue.serverTimestamp())
                    )
                } else if (request.targetType == "ESTABLISHMENT") {
                    batch.update(
                        db.collection("establishments").document(request.targetId),
                        mapOf("verified" to true, "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
            }
            val logRef = db.collection("admin_logs").document()
            batch.set(
                logRef,
                mapOf(
                    "adminId" to uid,
                    "adminName" to adminName.take(120),
                    "action" to "VERIFICATION_$status",
                    "targetType" to request.targetType,
                    "targetId" to request.targetId,
                    "details" to note.take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "verification_requests/${request.id}")
            Result.failure(e)
        }
    }

    suspend fun resolveReport(
        report: ReportItem,
        newStatus: String,
        removeTargetContent: Boolean,
        adminName: String
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            batch.update(
                db.collection("reports").document(report.id),
                mapOf(
                    "status" to newStatus,
                    "reviewedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (removeTargetContent) {
                when (report.targetType) {
                    "POST" -> batch.update(
                        db.collection("posts").document(report.targetId),
                        mapOf("status" to "REMOVED", "updatedAt" to FieldValue.serverTimestamp())
                    )
                    "DEBATE" -> batch.update(
                        db.collection("debates").document(report.targetId),
                        mapOf("status" to "REMOVED", "updatedAt" to FieldValue.serverTimestamp())
                    )
                    "COMMENT" -> batch.update(
                        db.collection("comments").document(report.targetId),
                        mapOf("status" to "REMOVED", "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
            }
            val logRef = db.collection("admin_logs").document()
            batch.set(
                logRef,
                mapOf(
                    "adminId" to uid,
                    "adminName" to adminName.take(120),
                    "action" to if (removeTargetContent) "MODERATE_REMOVE" else "REPORT_$newStatus",
                    "targetType" to report.targetType,
                    "targetId" to report.targetId,
                    "details" to report.reason.take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "reports/${report.id}")
            Result.failure(e)
        }
    }

    suspend fun toggleUserSuspension(
        targetUserId: String,
        suspend: Boolean,
        adminName: String
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            batch.update(
                db.collection("users").document(targetUserId),
                mapOf("suspended" to suspend, "updatedAt" to FieldValue.serverTimestamp())
            )
            val logRef = db.collection("admin_logs").document()
            batch.set(
                logRef,
                mapOf(
                    "adminId" to uid,
                    "adminName" to adminName.take(120),
                    "action" to if (suspend) "SUSPEND_USER" else "RESTORE_USER",
                    "targetType" to "USER",
                    "targetId" to targetUserId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$targetUserId")
            Result.failure(e)
        }
    }

    suspend fun assignRole(
        targetUserId: String,
        newRole: UserRole,
        adminName: String
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            batch.update(
                db.collection("users").document(targetUserId),
                mapOf("role" to newRole.name, "updatedAt" to FieldValue.serverTimestamp())
            )
            if (newRole == UserRole.ADMINISTRATEUR) {
                batch.set(
                    db.collection("admins").document(targetUserId),
                    mapOf("userId" to targetUserId, "assignedBy" to uid, "createdAt" to FieldValue.serverTimestamp())
                )
            } else if (newRole == UserRole.MODERATEUR) {
                batch.set(
                    db.collection("moderators").document(targetUserId),
                    mapOf("userId" to targetUserId, "assignedBy" to uid, "createdAt" to FieldValue.serverTimestamp())
                )
            } else {
                batch.delete(db.collection("moderators").document(targetUserId))
            }
            val logRef = db.collection("admin_logs").document()
            batch.set(
                logRef,
                mapOf(
                    "adminId" to uid,
                    "adminName" to adminName.take(120),
                    "action" to "ASSIGN_ROLE_${newRole.name}",
                    "targetType" to "USER",
                    "targetId" to targetUserId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$targetUserId")
            Result.failure(e)
        }
    }

    suspend fun addCategory(name: String, kind: String, adminName: String): Result<Unit> {
        val docRef = db.collection("app_categories").document()
        return try {
            val uid = requireUserId()
            require(name.trim().length >= 2) { "Nom de catégorie requis." }
            val batch = db.batch()
            batch.set(
                docRef,
                mapOf(
                    "name" to name.trim().take(80),
                    "kind" to kind,
                    "active" to true,
                    "visibility" to "PUBLIC",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("admin_logs").document(),
                mapOf(
                    "adminId" to uid,
                    "adminName" to adminName.take(120),
                    "action" to "ADD_CATEGORY",
                    "targetType" to "CATEGORY",
                    "targetId" to docRef.id,
                    "details" to "$kind: ${name.trim()}".take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }
}
