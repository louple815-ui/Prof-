package com.example.data.repository

import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.Debate
import com.example.data.model.Poll
import com.example.data.model.Vote
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

class DebateRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Utilisateur non connecté.")

    fun observeDebates(): Flow<List<Debate>> = flow {
        val path = "debates"
        emitAll(
            db.collection("debates")
                .whereEqualTo("visibility", "PUBLIC")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Debate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.status != "REMOVED" }
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

    fun observePolls(): Flow<List<Poll>> = flow {
        val path = "polls"
        emitAll(
            db.collection("polls")
                .whereEqualTo("visibility", "PUBLIC")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Poll::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeUserVotes(userId: String): Flow<List<Vote>> = flow {
        val path = "votes"
        emitAll(
            db.collection("votes")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Vote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createDebateWithOptionalPoll(
        authorName: String,
        authorVerified: Boolean,
        title: String,
        question: String,
        category: String,
        pollQuestion: String?,
        pollOptions: List<String>
    ): Result<Unit> {
        val debateRef = db.collection("debates").document()
        return try {
            val uid = requireUserId()
            require(title.trim().length >= 3) { "Titre trop court (min 3 caractères)." }
            require(question.trim().length >= 5) { "Question trop courte (min 5 caractères)." }

            val cleanedOptions = pollOptions.map { it.trim() }.filter { it.isNotBlank() }.take(6)
            val hasPoll = !pollQuestion.isNullOrBlank() && cleanedOptions.size >= 2
            val pollRef = if (hasPoll) db.collection("polls").document() else null

            val debatePayload = buildMap<String, Any> {
                put("authorId", uid)
                put("authorName", authorName.take(120))
                put("authorVerified", authorVerified)
                put("title", title.trim().take(180))
                put("question", question.trim().take(2000))
                put("category", category.take(80))
                put("visibility", "PUBLIC")
                put("status", "OPEN")
                put("argumentsCount", 0L)
                if (pollRef != null) put("pollId", pollRef.id)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            val batch = db.batch()
            batch.set(debateRef, debatePayload)
            if (pollRef != null) {
                batch.set(
                    pollRef,
                    mapOf(
                        "debateId" to debateRef.id,
                        "authorId" to uid,
                        "question" to pollQuestion!!.trim().take(300),
                        "options" to cleanedOptions,
                        "optionCounts" to List(cleanedOptions.size) { 0L },
                        "totalVotes" to 0L,
                        "visibility" to "PUBLIC",
                        "status" to "OPEN",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, debateRef.path)
            Result.failure(e)
        }
    }

    suspend fun castPollVote(poll: Poll, optionIndex: Int): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Non connecté"))
        val voteId = "${poll.id}_$uid"
        val voteRef = db.collection("votes").document(voteId)
        val pollRef = db.collection("polls").document(poll.id)
        return try {
            require(optionIndex in poll.options.indices) { "Option invalide." }
            db.runTransaction { tx ->
                val existingVote = tx.get(voteRef)
                if (existingVote.exists()) {
                    throw IllegalStateException("Vous avez déjà voté pour ce sondage.")
                }
                val freshPoll = tx.get(pollRef)
                @Suppress("UNCHECKED_CAST")
                val rawCounts = (freshPoll.get("optionCounts") as? List<Number>)?.map { it.toLong() }
                    ?: poll.optionCounts
                val updatedCounts = rawCounts.toMutableList()
                while (updatedCounts.size < poll.options.size) updatedCounts.add(0L)
                updatedCounts[optionIndex] = updatedCounts[optionIndex] + 1L
                val freshTotal = freshPoll.getLong("totalVotes") ?: poll.totalVotes

                tx.set(
                    voteRef,
                    mapOf(
                        "pollId" to poll.id,
                        "debateId" to poll.debateId,
                        "userId" to uid,
                        "optionIndex" to optionIndex.toLong(),
                        "visibility" to "PUBLIC",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    pollRef,
                    mapOf(
                        "optionCounts" to updatedCounts,
                        "totalVotes" to freshTotal + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, pollRef.path)
            Result.failure(e)
        }
    }
}
