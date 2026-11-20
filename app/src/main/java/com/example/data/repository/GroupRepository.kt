package com.example.data.repository

import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.Group
import com.example.data.model.GroupMember
import com.example.data.model.Post
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

class GroupRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Utilisateur non connecté.")

    fun observePublicGroups(): Flow<List<Group>> = flow {
        val path = "groups"
        emitAll(
            db.collection("groups")
                .whereEqualTo("privacy", "PUBLIC")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Group::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.membersCount }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeOwnedGroups(userId: String): Flow<List<Group>> = flow {
        val path = "groups"
        emitAll(
            db.collection("groups")
                .whereEqualTo("ownerId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Group::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeMyMemberships(userId: String): Flow<List<GroupMember>> = flow {
        val path = "group_members"
        emitAll(
            db.collection("group_members")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(GroupMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeGroupMembers(groupId: String): Flow<List<GroupMember>> = flow {
        val path = "group_members"
        emitAll(
            db.collection("group_members")
                .whereEqualTo("visibility", "PUBLIC")
                .whereEqualTo("groupId", groupId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(GroupMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeGroupPosts(groupId: String): Flow<List<Post>> = flow {
        val path = "posts"
        emitAll(
            db.collection("posts")
                .whereEqualTo("visibility", "PUBLIC")
                .whereEqualTo("groupId", groupId)
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

    suspend fun createGroup(
        ownerName: String,
        ownerRole: String,
        name: String,
        description: String,
        domain: String,
        subject: String,
        level: String,
        region: String,
        privacy: String,
        rules: String
    ): Result<Unit> {
        val groupRef = db.collection("groups").document()
        return try {
            val uid = requireUserId()
            require(name.trim().length >= 2) { "Nom du groupe requis." }
            val groupPayload = buildMap<String, Any> {
                put("name", name.trim().take(100))
                if (description.isNotBlank()) put("description", description.trim().take(600))
                put("domain", domain.take(60))
                if (subject.isNotBlank()) put("subject", subject.take(80))
                if (level.isNotBlank()) put("level", level.take(80))
                if (region.isNotBlank()) put("region", region.take(80))
                put("privacy", privacy)
                if (rules.isNotBlank()) put("rules", rules.trim().take(1000))
                put("ownerId", uid)
                put("ownerName", ownerName.take(120))
                put("membersCount", 1L)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            val memberRef = db.collection("group_members").document("${groupRef.id}_$uid")
            val memberPayload = mapOf(
                "groupId" to groupRef.id,
                "userId" to uid,
                "userName" to ownerName.take(120),
                "userRole" to ownerRole.take(40),
                "memberRole" to "OWNER",
                "status" to "ACTIVE",
                "visibility" to if (privacy == "PUBLIC") "PUBLIC" else "PRIVATE",
                "joinedAt" to FieldValue.serverTimestamp()
            )
            groupRef.set(groupPayload).await()
            memberRef.set(memberPayload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, groupRef.path)
            Result.failure(e)
        }
    }

    fun observeGroupAllMembersForOwner(groupId: String): Flow<List<GroupMember>> = flow {
        val path = "group_members"
        emitAll(
            db.collection("group_members")
                .whereEqualTo("groupId", groupId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(GroupMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun joinGroup(group: Group, userName: String, userRole: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Non connecté"))
        val memberRef = db.collection("group_members").document("${group.id}_$uid")
        return try {
            val existing = try { memberRef.get().await() } catch (_: Exception) { null }
            if (existing?.exists() == true) {
                return Result.success(Unit)
            }
            val status = if (group.privacy == "PUBLIC") "ACTIVE" else "PENDING"
            val batch = db.batch()
            batch.set(
                memberRef,
                mapOf(
                    "groupId" to group.id,
                    "userId" to uid,
                    "userName" to userName.take(120),
                    "userRole" to userRole.take(40),
                    "memberRole" to "MEMBER",
                    "status" to status,
                    "visibility" to if (group.privacy == "PUBLIC") "PUBLIC" else "PRIVATE",
                    "joinedAt" to FieldValue.serverTimestamp()
                )
            )
            if (status == "ACTIVE") {
                batch.update(
                    db.collection("groups").document(group.id),
                    mapOf("membersCount" to FieldValue.increment(1L), "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, memberRef.path)
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(group: Group): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Non connecté"))
        val memberRef = db.collection("group_members").document("${group.id}_$uid")
        return try {
            val existing = try { memberRef.get().await() } catch (_: Exception) { null }
            val wasActive = existing?.getString("status") == "ACTIVE"
            val batch = db.batch()
            batch.delete(memberRef)
            if (wasActive && group.membersCount > 1L) {
                batch.update(
                    db.collection("groups").document(group.id),
                    mapOf("membersCount" to FieldValue.increment(-1L), "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, memberRef.path)
            Result.failure(e)
        }
    }

    suspend fun approveMemberRequest(groupId: String, memberUserId: String): Result<Unit> {
        val memberRef = db.collection("group_members").document("${groupId}_$memberUserId")
        val groupRef = db.collection("groups").document(groupId)
        return try {
            requireUserId()
            val batch = db.batch()
            batch.update(memberRef, mapOf("status" to "ACTIVE"))
            batch.update(
                groupRef,
                mapOf(
                    "membersCount" to FieldValue.increment(1L),
                    "lastMemberUserId" to memberUserId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, memberRef.path)
            Result.failure(e)
        }
    }

    suspend fun rejectMemberRequest(groupId: String, memberUserId: String): Result<Unit> {
        val memberRef = db.collection("group_members").document("${groupId}_$memberUserId")
        return try {
            requireUserId()
            memberRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, memberRef.path)
            Result.failure(e)
        }
    }
}
