package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val labelFr: String, val labelEn: String) {
    ENSEIGNANT("Enseignant(e)", "Teacher"),
    RESPONSABLE_ETABLISSEMENT("Responsable d'établissement", "School Leader"),
    MODERATEUR("Modérateur", "Moderator"),
    ADMINISTRATEUR("Administrateur", "Administrator");

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: ENSEIGNANT
    }
}

@Keep
@IgnoreExtraProperties
data class UserPublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String = "",
    val role: String = UserRole.ENSEIGNANT.name,
    val country: String = "Burkina Faso",
    val city: String = "",
    val region: String = "Centre",
    val jobTitle: String = "",
    val subject: String = "",
    val schoolLevel: String = "",
    val establishmentName: String = "",
    val establishmentId: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val photoStoragePath: String = "",
    val photoData: String = "",
    val verified: Boolean = false,
    val suspended: Boolean = false,
    val discoverable: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedRole(): UserRole = UserRole.fromRaw(role)
    fun resolvedPhotoSource(): String = photoUrl.ifBlank { photoData }
}

@Keep
@IgnoreExtraProperties
data class UserPrivateInfo(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val notifyComments: Boolean = true,
    val notifyReplies: Boolean = true,
    val notifyMentions: Boolean = true,
    val notifyMessages: Boolean = true,
    val notifyInvitations: Boolean = true,
    val notifyAnnouncements: Boolean = true,
    val notifyGroups: Boolean = true,
    val blockedUserIds: List<String> = emptyList(),
    val preferredLanguage: String = "fr",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Bookmark(
    @DocumentId val id: String = "",
    val userId: String = "",
    val targetId: String = "",
    val targetType: String = "POST",
    val title: String = "",
    val subtitle: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Post(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorRole: String = UserRole.ENSEIGNANT.name,
    val authorVerified: Boolean = false,
    val content: String = "",
    val category: String = "Expérience de classe",
    val visibility: String = "PUBLIC",
    val groupId: String = "",
    val imageUrl: String = "",
    val imageStoragePath: String = "",
    val imageData: String = "",
    val attachmentName: String = "",
    val attachmentType: String = "",
    val attachmentUrl: String = "",
    val attachmentStoragePath: String = "",
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val sharesCount: Long = 0L,
    val status: String = "ACTIVE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedImageSource(): String = imageUrl.ifBlank { imageData }
}

@Keep
@IgnoreExtraProperties
data class PostReaction(
    @DocumentId val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val type: String = "LIKE",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Comment(
    @DocumentId val id: String = "",
    val targetId: String = "",
    val targetType: String = "POST",
    val parentCommentId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorRole: String = UserRole.ENSEIGNANT.name,
    val content: String = "",
    val visibility: String = "PUBLIC",
    val status: String = "ACTIVE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Debate(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorVerified: Boolean = false,
    val title: String = "",
    val question: String = "",
    val category: String = "Éducation",
    val visibility: String = "PUBLIC",
    val status: String = "OPEN",
    val argumentsCount: Long = 0L,
    val pollId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Poll(
    @DocumentId val id: String = "",
    val debateId: String = "",
    val authorId: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val optionCounts: List<Long> = emptyList(),
    val totalVotes: Long = 0L,
    val visibility: String = "PUBLIC",
    val status: String = "OPEN",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Vote(
    @DocumentId val id: String = "",
    val pollId: String = "",
    val debateId: String = "",
    val userId: String = "",
    val optionIndex: Long = 0L,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Group(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val domain: String = "Matière",
    val subject: String = "",
    val level: String = "",
    val region: String = "",
    val privacy: String = "PUBLIC",
    val rules: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val membersCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GroupMember(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userRole: String = UserRole.ENSEIGNANT.name,
    val memberRole: String = "MEMBER",
    val status: String = "ACTIVE",
    val visibility: String = "PUBLIC",
    @ServerTimestamp val joinedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ResourceItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val resourceType: String = "FICHE_PEDAGOGIQUE",
    val subject: String = "",
    val level: String = "",
    val fileName: String = "",
    val mimeType: String = "text/plain",
    val fileSizeBytes: Long = 0L,
    val fileUrl: String = "",
    val storagePath: String = "",
    val contentBody: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val visibility: String = "PUBLIC",
    val groupId: String = "",
    val downloadsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Establishment(
    @DocumentId val id: String = "",
    val officialName: String = "",
    val country: String = "Burkina Faso",
    val city: String = "",
    val region: String = "Centre",
    val establishmentType: String = "Lycée",
    val description: String = "",
    val directorId: String = "",
    val directorName: String = "",
    val announcement: String = "",
    val verified: Boolean = false,
    val visibility: String = "PUBLIC",
    val membersCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val type: String = "DIRECT",
    val title: String = "",
    val participantIds: List<String> = emptyList(),
    val participantSummary: String = "",
    val lastMessage: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val participantIds: List<String> = emptyList(),
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val attachmentName: String = "",
    val attachmentUrl: String = "",
    val attachmentStoragePath: String = "",
    val attachmentMimeType: String = "",
    val attachmentSizeBytes: Long = 0L,
    val attachmentData: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val actorId: String = "",
    val actorName: String = "",
    val type: String = "COMMENT",
    val title: String = "",
    val body: String = "",
    val targetId: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReportItem(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val reporterName: String = "",
    val targetType: String = "POST",
    val targetId: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "PENDING",
    val reviewedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VerificationRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val targetType: String = "TEACHER",
    val targetId: String = "",
    val targetName: String = "",
    val documentInfo: String = "",
    val status: String = "PENDING",
    val reviewedBy: String = "",
    val reviewNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminLog(
    @DocumentId val id: String = "",
    val adminId: String = "",
    val adminName: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppCategory(
    @DocumentId val id: String = "",
    val name: String = "",
    val kind: String = "DEBATE",
    val active: Boolean = true,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null
)
