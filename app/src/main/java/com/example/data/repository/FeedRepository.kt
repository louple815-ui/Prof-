package com.example.data.repository

import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.Comment
import com.example.data.model.Post
import com.example.data.model.PostReaction
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

class FeedRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Utilisateur non connecté.")

    fun observePublicPosts(limitCount: Long = 50L): Flow<List<Post>> = flow {
        val path = "posts"
        emitAll(
            db.collection("posts")
                .whereEqualTo("visibility", "PUBLIC")
                .limit(limitCount)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.status == "ACTIVE" }
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

    fun observeComments(targetId: String): Flow<List<Comment>> = flow {
        val path = "comments"
        emitAll(
            db.collection("comments")
                .whereEqualTo("visibility", "PUBLIC")
                .whereEqualTo("targetId", targetId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Comment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.status == "ACTIVE" }
                        .sortedBy { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeUserReactions(postId: String): Flow<List<PostReaction>> = flow {
        val path = "posts/$postId/reactions"
        emitAll(
            db.collection("posts").document(postId).collection("reactions")
                .snapshots()
                .map { snap ->
                    snap.toObjects(PostReaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createPost(
        authorName: String,
        authorRole: String,
        authorVerified: Boolean,
        content: String,
        category: String,
        visibility: String = "PUBLIC",
        groupId: String = "",
        imageData: String = "",
        attachmentName: String = "",
        attachmentType: String = ""
    ): Result<String> {
        val docRef = db.collection("posts").document()
        return try {
            val uid = requireUserId()
            require(content.trim().isNotEmpty()) { "Le contenu de la publication est requis." }
            val payload = buildMap<String, Any> {
                put("authorId", uid)
                put("authorName", authorName.take(120))
                put("authorRole", authorRole.take(40))
                put("authorVerified", authorVerified)
                put("content", content.trim().take(3000))
                put("category", category.take(80))
                put("visibility", visibility)
                if (groupId.isNotBlank()) put("groupId", groupId)
                if (imageData.isNotBlank()) put("imageData", imageData)
                if (attachmentName.isNotBlank()) put("attachmentName", attachmentName.take(160))
                if (attachmentType.isNotBlank()) put("attachmentType", attachmentType.take(40))
                put("likesCount", 0L)
                put("commentsCount", 0L)
                put("sharesCount", 0L)
                put("status", "ACTIVE")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePost(postId: String, content: String, category: String): Result<Unit> {
        val path = "posts/$postId"
        return try {
            requireUserId()
            require(content.trim().isNotEmpty()) { "Le contenu ne peut pas être vide." }
            db.collection("posts").document(postId).update(
                mapOf(
                    "content" to content.trim().take(3000),
                    "category" to category.take(80),
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

    suspend fun deletePost(postId: String): Result<Unit> {
        val path = "posts/$postId"
        return try {
            requireUserId()
            db.collection("posts").document(postId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleReaction(post: Post, reactionType: String = "LIKE"): Result<Unit> {
        val path = "posts/${post.id}"
        return try {
            val uid = requireUserId()
            val reactionRef = db.collection("posts").document(post.id).collection("reactions").document(uid)
            val existing = reactionRef.get().await()
            val batch = db.batch()
            if (existing.exists()) {
                batch.delete(reactionRef)
                val nextCount = (post.likesCount - 1L).coerceAtLeast(0L)
                batch.update(
                    db.collection("posts").document(post.id),
                    mapOf("likesCount" to nextCount, "updatedAt" to FieldValue.serverTimestamp())
                )
            } else {
                batch.set(
                    reactionRef,
                    mapOf(
                        "postId" to post.id,
                        "userId" to uid,
                        "type" to reactionType,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    db.collection("posts").document(post.id),
                    mapOf("likesCount" to post.likesCount + 1L, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun incrementShare(post: Post): Result<Unit> {
        val path = "posts/${post.id}"
        return try {
            requireUserId()
            db.collection("posts").document(post.id).update(
                mapOf(
                    "sharesCount" to post.sharesCount + 1L,
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

    suspend fun addComment(
        targetId: String,
        targetType: String,
        parentCommentId: String = "",
        authorName: String,
        authorRole: String,
        content: String,
        currentTargetCount: Long
    ): Result<Unit> {
        val commentRef = db.collection("comments").document()
        return try {
            val uid = requireUserId()
            require(content.trim().isNotEmpty()) { "Le commentaire ne peut pas être vide." }
            val payload = buildMap<String, Any> {
                put("targetId", targetId)
                put("targetType", targetType)
                if (parentCommentId.isNotBlank()) put("parentCommentId", parentCommentId)
                put("authorId", uid)
                put("authorName", authorName.take(120))
                put("authorRole", authorRole.take(40))
                put("content", content.trim().take(1500))
                put("visibility", "PUBLIC")
                put("status", "ACTIVE")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            val batch = db.batch()
            batch.set(commentRef, payload)
            if (targetType == "POST") {
                batch.update(
                    db.collection("posts").document(targetId),
                    mapOf("commentsCount" to currentTargetCount + 1L, "updatedAt" to FieldValue.serverTimestamp())
                )
            } else if (targetType == "DEBATE") {
                batch.update(
                    db.collection("debates").document(targetId),
                    mapOf("argumentsCount" to currentTargetCount + 1L, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        reporterName: String,
        targetType: String,
        targetId: String,
        reason: String,
        details: String
    ): Result<Unit> {
        val docRef = db.collection("reports").document()
        return try {
            val uid = requireUserId()
            val payload = buildMap<String, Any> {
                put("reporterId", uid)
                put("reporterName", reporterName.take(120))
                put("targetType", targetType)
                put("targetId", targetId)
                put("reason", reason.trim().take(120))
                if (details.isNotBlank()) put("details", details.trim().take(600))
                put("status", "PENDING")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }
}
