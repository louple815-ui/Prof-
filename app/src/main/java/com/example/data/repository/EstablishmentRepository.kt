package com.example.data.repository

import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.Establishment
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

class EstablishmentRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Utilisateur non connecté.")

    fun observeEstablishments(): Flow<List<Establishment>> = flow {
        val path = "establishments"
        emitAll(
            db.collection("establishments")
                .whereEqualTo("visibility", "PUBLIC")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Establishment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun createEstablishment(
        directorName: String,
        officialName: String,
        country: String,
        city: String,
        region: String,
        establishmentType: String,
        description: String,
        announcement: String
    ): Result<String> {
        val docRef = db.collection("establishments").document()
        return try {
            val uid = requireUserId()
            require(officialName.trim().length >= 2) { "Nom officiel requis." }
            val payload = buildMap<String, Any> {
                put("officialName", officialName.trim().take(140))
                put("country", country.trim().ifBlank { "Burkina Faso" }.take(80))
                put("city", city.trim().ifBlank { "Ouagadougou" }.take(80))
                put("region", region.trim().ifBlank { "Centre" }.take(80))
                put("establishmentType", establishmentType.take(80))
                if (description.isNotBlank()) put("description", description.trim().take(800))
                put("directorId", uid)
                put("directorName", directorName.take(120))
                if (announcement.isNotBlank()) put("announcement", announcement.trim().take(600))
                put("verified", false)
                put("visibility", "PUBLIC")
                put("membersCount", 1L)
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

    suspend fun updateAnnouncement(establishmentId: String, announcement: String): Result<Unit> {
        val path = "establishments/$establishmentId"
        return try {
            requireUserId()
            db.collection("establishments").document(establishmentId).update(
                mapOf(
                    "announcement" to announcement.trim().take(600),
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
}
