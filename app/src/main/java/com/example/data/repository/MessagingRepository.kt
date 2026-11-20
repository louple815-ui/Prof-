package com.example.data.repository

import android.net.Uri
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
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

class MessagingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val storageRepository: StorageRepository = StorageRepository()
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Utilisateur non connecté.")

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val path = "conversations"
        emitAll(
            db.collection("conversations")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> = flow {
        val path = "messages"
        emitAll(
            db.collection("messages")
                .whereArrayContains("participantIds", userId)
                .whereEqualTo("conversationId", conversationId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createOrOpenDirectConversation(
        myName: String,
        targetUserId: String,
        targetUserName: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Non connecté"))
        val sortedPair = listOf(uid, targetUserId).sorted()
        val convId = "dm_${sortedPair[0]}_${sortedPair[1]}".take(120)
        val convRef = db.collection("conversations").document(convId)
        return try {
            val existing = convRef.get().await()
            if (!existing.exists()) {
                convRef.set(
                    mapOf(
                        "type" to "DIRECT",
                        "title" to targetUserName.take(120),
                        "participantIds" to sortedPair,
                        "participantSummary" to "$myName, $targetUserName".take(400),
                        "lastMessage" to "",
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "createdBy" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, convRef.path)
            Result.failure(e)
        }
    }

    suspend fun createGroupConversation(
        myName: String,
        title: String,
        participantIds: List<String>,
        participantSummary: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Non connecté"))
        val convRef = db.collection("conversations").document()
        return try {
            val allParticipants = (participantIds + uid).distinct().take(50)
            require(allParticipants.size >= 2) { "Sélectionnez au moins un participant." }
            convRef.set(
                mapOf(
                    "type" to "GROUP",
                    "title" to title.trim().take(120),
                    "participantIds" to allParticipants,
                    "participantSummary" to participantSummary.ifBlank { myName }.take(400),
                    "lastMessage" to "",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(convRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, convRef.path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        conversation: Conversation,
        senderName: String,
        text: String,
        attachmentName: String = "",
        attachmentData: String = ""
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Non connecté"))
        val msgRef = db.collection("messages").document()
        return try {
            require(text.isNotBlank() || attachmentName.isNotBlank() || attachmentData.isNotBlank()) { "Message vide." }
            val uploaded = if (StorageRepository.isLocalUri(attachmentData)) {
                storageRepository.uploadMessageAttachment(conversation.id, uid, Uri.parse(attachmentData))
            } else null

            val resolvedName = uploaded?.fileName ?: attachmentName
            val msgPayload = buildMap<String, Any> {
                put("conversationId", conversation.id)
                put("participantIds", conversation.participantIds)
                put("senderId", uid)
                put("senderName", senderName.take(120))
                put("text", text.trim().take(2000))
                if (resolvedName.isNotBlank()) put("attachmentName", resolvedName.take(160))
                if (uploaded != null) {
                    put("attachmentUrl", uploaded.downloadUrl)
                    put("attachmentStoragePath", uploaded.storagePath)
                    put("attachmentMimeType", uploaded.mimeType.take(100))
                    put("attachmentSizeBytes", uploaded.sizeBytes)
                } else if (StorageRepository.isRemoteHttpsUrl(attachmentData)) {
                    put("attachmentUrl", attachmentData)
                }
                put("createdAt", FieldValue.serverTimestamp())
            }
            val preview = if (text.isNotBlank()) text.trim().take(500) else "Pièce jointe : $resolvedName".take(500)
            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(
                db.collection("conversations").document(conversation.id),
                mapOf(
                    "lastMessage" to preview,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }
}
