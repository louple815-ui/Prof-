package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Rôle contextuel d'un utilisateur au sein d'un établissement donné.
 * Strictement distinct du rôle global de plateforme [UserRole].
 */
enum class SchoolRole(val labelFr: String, val labelEn: String) {
    ENSEIGNANT("Enseignant(e)", "Teacher"),
    ELEVE("Élève", "Student"),
    PARENT("Parent / Tuteur", "Parent / Guardian"),
    RESPONSABLE_ETABLISSEMENT("Responsable d'établissement", "School Leader"),
    ADMINISTRATION("Personnel d'administration", "Administration Staff");

    fun canManageSchool(): Boolean =
        this == RESPONSABLE_ETABLISSEMENT || this == ADMINISTRATION

    companion object {
        fun fromRawOrNull(raw: String?): SchoolRole? =
            entries.firstOrNull { it.name == raw?.trim() }

        /**
         * Retourne [ENSEIGNANT] par défaut uniquement sur lecture tolérante
         * d'un document existant, sans jamais promouvoir vers un rôle de direction.
         */
        fun fromRawSafe(raw: String?): SchoolRole =
            fromRawOrNull(raw) ?: ENSEIGNANT
    }
}

/**
 * Cycle de vie d'une adhésion à un établissement ou d'un rattachement contrôlé.
 */
enum class MembershipStatus(val labelFr: String, val labelEn: String) {
    PENDING("En attente", "Pending"),
    ACTIVE("Actif", "Active"),
    REJECTED("Refusé", "Rejected"),
    SUSPENDED("Suspendu", "Suspended");

    companion object {
        fun fromRawOrNull(raw: String?): MembershipStatus? =
            entries.firstOrNull { it.name == raw?.trim() }

        fun fromRawSafe(raw: String?): MembershipStatus =
            fromRawOrNull(raw) ?: PENDING
    }
}

/**
 * État de synchronisation local/serveur utilisé pour le futur cache hors ligne Room.
 */
enum class SyncStatus(val labelFr: String, val labelEn: String) {
    SYNCED("Synchronisé", "Synced"),
    PENDING_SYNC("EN ATTENTE DE SYNCHRONISATION", "PENDING SYNC"),
    SYNC_ERROR("Échec de synchronisation", "Sync error")
}

/**
 * Établissement scolaire multi-tenant (`schools/{schoolId}`).
 * Distinct de la vitrine communautaire `establishments/{establishmentId}`,
 * tout en pouvant référencer `linkedEstablishmentId` pour la compatibilité.
 */
@Keep
@IgnoreExtraProperties
data class School(
    @DocumentId val id: String = "",
    val linkedEstablishmentId: String = "",
    val name: String = "",
    val code: String = "",
    val country: String = "Burkina Faso",
    val city: String = "",
    val region: String = "Centre",
    val establishmentType: String = "Lycée",
    val academicYear: String = "",
    val periods: List<String> = emptyList(),
    val ownerId: String = "",
    val createdBy: String = "",
    val status: String = "ACTIVE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toFirestoreMap(includeServerTimestamps: Boolean = true): Map<String, Any> = buildMap {
        if (linkedEstablishmentId.isNotBlank()) put("linkedEstablishmentId", linkedEstablishmentId.trim())
        put("name", name.trim())
        if (code.isNotBlank()) put("code", code.trim())
        put("country", country.trim().ifBlank { "Burkina Faso" })
        put("city", city.trim())
        put("region", region.trim().ifBlank { "Centre" })
        put("establishmentType", establishmentType.trim().ifBlank { "Lycée" })
        put("academicYear", academicYear.trim())
        put("periods", periods.filter { it.isNotBlank() })
        put("ownerId", ownerId.trim())
        put("createdBy", createdBy.ifBlank { ownerId }.trim())
        put("status", status.ifBlank { "ACTIVE" })
        if (includeServerTimestamps) {
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
    }

    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): School = School(
            id = id,
            linkedEstablishmentId = data["linkedEstablishmentId"] as? String ?: "",
            name = data["name"] as? String ?: "",
            code = data["code"] as? String ?: "",
            country = data["country"] as? String ?: "Burkina Faso",
            city = data["city"] as? String ?: "",
            region = data["region"] as? String ?: "Centre",
            establishmentType = data["establishmentType"] as? String ?: "Lycée",
            academicYear = data["academicYear"] as? String ?: "",
            periods = (data["periods"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            ownerId = data["ownerId"] as? String ?: "",
            createdBy = data["createdBy"] as? String ?: "",
            status = data["status"] as? String ?: "ACTIVE",
            createdAt = data["createdAt"] as? Timestamp,
            updatedAt = data["updatedAt"] as? Timestamp
        )
    }
}

/**
 * Appartenance d'un utilisateur à un établissement (`schoolMemberships/{schoolId}_{userId}`).
 * Permet à un compte personnel d'appartenir à plusieurs établissements avec des rôles distincts.
 */
@Keep
@IgnoreExtraProperties
data class SchoolMembership(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val userId: String = "",
    val userDisplayName: String = "",
    val schoolRole: String = SchoolRole.ENSEIGNANT.name,
    val status: String = MembershipStatus.PENDING.name,
    val assignedClassIds: List<String> = emptyList(),
    val assignedSubjectIds: List<String> = emptyList(),
    val reviewedBy: String = "",
    val reviewNote: String = "",
    val createdBy: String = "",
    val legacyBridge: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val reviewedAt: Timestamp? = null
) {
    fun parsedSchoolRole(): SchoolRole = SchoolRole.fromRawSafe(schoolRole)
    fun parsedStatus(): MembershipStatus = MembershipStatus.fromRawSafe(status)
    fun isActiveMembership(): Boolean = parsedStatus() == MembershipStatus.ACTIVE

    fun toClientJoinRequestMap(requestedByUid: String): Map<String, Any> {
        require(schoolId.isNotBlank()) { "schoolId requis pour une demande d'adhésion." }
        require(requestedByUid.isNotBlank()) { "UID authentifié requis." }
        // Sécurité : une demande créée par le client est toujours PENDING et limitée aux rôles non privilégiés
        val requested = SchoolRole.fromRawOrNull(schoolRole)
        val safeRequestedRole = when (requested) {
            SchoolRole.ENSEIGNANT, SchoolRole.ELEVE, SchoolRole.PARENT -> requested.name
            else -> SchoolRole.ENSEIGNANT.name
        }
        return buildMap {
            put("schoolId", schoolId.trim())
            if (schoolName.isNotBlank()) put("schoolName", schoolName.trim())
            put("userId", requestedByUid)
            put("userDisplayName", userDisplayName.trim())
            put("schoolRole", safeRequestedRole)
            put("status", MembershipStatus.PENDING.name)
            put("assignedClassIds", emptyList<String>())
            put("assignedSubjectIds", emptyList<String>())
            put("createdBy", requestedByUid)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
    }

    companion object {
        fun documentId(schoolId: String, userId: String): String =
            "${schoolId.trim()}_${userId.trim()}"

        fun fromMap(id: String, data: Map<String, Any?>): SchoolMembership = SchoolMembership(
            id = id,
            schoolId = data["schoolId"] as? String ?: "",
            schoolName = data["schoolName"] as? String ?: "",
            userId = data["userId"] as? String ?: "",
            userDisplayName = data["userDisplayName"] as? String ?: "",
            schoolRole = data["schoolRole"] as? String ?: SchoolRole.ENSEIGNANT.name,
            status = data["status"] as? String ?: MembershipStatus.PENDING.name,
            assignedClassIds = (data["assignedClassIds"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            assignedSubjectIds = (data["assignedSubjectIds"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            reviewedBy = data["reviewedBy"] as? String ?: "",
            reviewNote = data["reviewNote"] as? String ?: "",
            createdBy = data["createdBy"] as? String ?: "",
            legacyBridge = data["legacyBridge"] as? Boolean ?: false,
            createdAt = data["createdAt"] as? Timestamp,
            updatedAt = data["updatedAt"] as? Timestamp,
            reviewedAt = data["reviewedAt"] as? Timestamp
        )
    }
}

/**
 * Classe d'un établissement (`schoolClasses/{classId}`).
 */
@Keep
@IgnoreExtraProperties
data class SchoolClass(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val name: String = "",
    val level: String = "",
    val academicYear: String = "",
    val capacity: Long = 0L,
    val studentsCount: Long = 0L,
    val status: String = "ACTIVE",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): SchoolClass = SchoolClass(
            id = id,
            schoolId = data["schoolId"] as? String ?: "",
            name = data["name"] as? String ?: "",
            level = data["level"] as? String ?: "",
            academicYear = data["academicYear"] as? String ?: "",
            capacity = (data["capacity"] as? Number)?.toLong() ?: 0L,
            studentsCount = (data["studentsCount"] as? Number)?.toLong() ?: 0L,
            status = data["status"] as? String ?: "ACTIVE",
            createdBy = data["createdBy"] as? String ?: "",
            createdAt = data["createdAt"] as? Timestamp,
            updatedAt = data["updatedAt"] as? Timestamp
        )
    }
}

/**
 * Matière enseignée dans un établissement (`schoolSubjects/{subjectId}`).
 */
@Keep
@IgnoreExtraProperties
data class SchoolSubject(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val name: String = "",
    val code: String = "",
    val coefficient: Double = 1.0,
    val classIds: List<String> = emptyList(),
    val status: String = "ACTIVE",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): SchoolSubject = SchoolSubject(
            id = id,
            schoolId = data["schoolId"] as? String ?: "",
            name = data["name"] as? String ?: "",
            code = data["code"] as? String ?: "",
            coefficient = (data["coefficient"] as? Number)?.toDouble() ?: 1.0,
            classIds = (data["classIds"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            status = data["status"] as? String ?: "ACTIVE",
            createdBy = data["createdBy"] as? String ?: "",
            createdAt = data["createdAt"] as? Timestamp,
            updatedAt = data["updatedAt"] as? Timestamp
        )
    }
}

/**
 * Affectation enseignant <-> établissement <-> classe <-> matière (`teacherAssignments/{assignmentId}`).
 */
@Keep
@IgnoreExtraProperties
data class TeacherAssignment(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val classId: String = "",
    val className: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val academicYear: String = "",
    val validFromMillis: Long = 0L,
    val validToMillis: Long? = null,
    val status: String = "ACTIVE",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): TeacherAssignment = TeacherAssignment(
            id = id,
            schoolId = data["schoolId"] as? String ?: "",
            teacherId = data["teacherId"] as? String ?: "",
            teacherName = data["teacherName"] as? String ?: "",
            classId = data["classId"] as? String ?: "",
            className = data["className"] as? String ?: "",
            subjectId = data["subjectId"] as? String ?: "",
            subjectName = data["subjectName"] as? String ?: "",
            academicYear = data["academicYear"] as? String ?: "",
            validFromMillis = (data["validFromMillis"] as? Number)?.toLong() ?: 0L,
            validToMillis = (data["validToMillis"] as? Number)?.toLong(),
            status = data["status"] as? String ?: "ACTIVE",
            createdBy = data["createdBy"] as? String ?: "",
            createdAt = data["createdAt"] as? Timestamp,
            updatedAt = data["updatedAt"] as? Timestamp
        )
    }
}

/**
 * Dossier élève cloisonné par établissement (`schoolStudents/{studentId}`).
 * Privé par défaut, jamais exposé automatiquement sur le fil communautaire.
 */
@Keep
@IgnoreExtraProperties
data class SchoolStudent(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val linkedUserId: String = "",
    val matricule: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String = "",
    val classId: String = "",
    val className: String = "",
    val academicYear: String = "",
    val status: String = "ACTIVE",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): SchoolStudent = SchoolStudent(
            id = id,
            schoolId = data["schoolId"] as? String ?: "",
            linkedUserId = data["linkedUserId"] as? String ?: "",
            matricule = data["matricule"] as? String ?: "",
            firstName = data["firstName"] as? String ?: "",
            lastName = data["lastName"] as? String ?: "",
            displayName = data["displayName"] as? String ?: "",
            classId = data["classId"] as? String ?: "",
            className = data["className"] as? String ?: "",
            academicYear = data["academicYear"] as? String ?: "",
            status = data["status"] as? String ?: "ACTIVE",
            createdBy = data["createdBy"] as? String ?: "",
            createdAt = data["createdAt"] as? Timestamp,
            updatedAt = data["updatedAt"] as? Timestamp
        )
    }
}

/**
 * Lien parent <-> élève contrôlé et validé par l'établissement (`parentLinks/{linkId}`).
 */
@Keep
@IgnoreExtraProperties
data class ParentLink(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val studentDisplayName: String = "",
    val parentUserId: String = "",
    val parentDisplayName: String = "",
    val relationship: String = "PARENT",
    val inviteCodeId: String = "",
    val status: String = MembershipStatus.PENDING.name,
    val verifiedBy: String = "",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedStatus(): MembershipStatus = MembershipStatus.fromRawSafe(status)

    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): ParentLink = ParentLink(
            id = id,
            schoolId = data["schoolId"] as? String ?: "",
            studentId = data["studentId"] as? String ?: "",
            studentDisplayName = data["studentDisplayName"] as? String ?: "",
            parentUserId = data["parentUserId"] as? String ?: "",
            parentDisplayName = data["parentDisplayName"] as? String ?: "",
            relationship = data["relationship"] as? String ?: "PARENT",
            inviteCodeId = data["inviteCodeId"] as? String ?: "",
            status = data["status"] as? String ?: MembershipStatus.PENDING.name,
            verifiedBy = data["verifiedBy"] as? String ?: "",
            createdBy = data["createdBy"] as? String ?: "",
            createdAt = data["createdAt"] as? Timestamp,
            updatedAt = data["updatedAt"] as? Timestamp
        )
    }
}

/**
 * Pont de compatibilité ascendante pour les profils enseignants historiques
 * contenant encore `establishmentId` / `establishmentName` sans document `schoolMemberships`.
 */
object LegacySchoolCompatibility {
    fun fromLegacyProfile(profile: UserPublicProfile): SchoolMembership? {
        val rawName = profile.establishmentName.trim()
        val rawId = profile.establishmentId.trim()
        if (profile.userId.isBlank() || (rawName.isBlank() && rawId.isBlank())) {
            return null
        }
        val effectiveSchoolId = rawId.ifBlank {
            "legacy_${rawName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(48)}"
        }
        val mappedRole = when (profile.parsedRole()) {
            UserRole.RESPONSABLE_ETABLISSEMENT -> SchoolRole.RESPONSABLE_ETABLISSEMENT.name
            UserRole.ADMINISTRATEUR -> SchoolRole.ADMINISTRATION.name
            UserRole.MODERATEUR, UserRole.ENSEIGNANT -> SchoolRole.ENSEIGNANT.name
        }
        return SchoolMembership(
            id = SchoolMembership.documentId(effectiveSchoolId, profile.userId),
            schoolId = effectiveSchoolId,
            schoolName = rawName.ifBlank { rawId },
            userId = profile.userId,
            userDisplayName = profile.displayName,
            schoolRole = mappedRole,
            status = MembershipStatus.ACTIVE.name,
            createdBy = profile.userId,
            legacyBridge = true,
            createdAt = profile.createdAt,
            updatedAt = profile.updatedAt
        )
    }

    fun deserializeLegacyUserProfile(id: String, data: Map<String, Any?>): UserPublicProfile {
        return UserPublicProfile(
            id = id,
            userId = data["userId"] as? String ?: id,
            firstName = data["firstName"] as? String ?: "",
            lastName = data["lastName"] as? String ?: "",
            displayName = data["displayName"] as? String ?: "",
            role = data["role"] as? String ?: UserRole.ENSEIGNANT.name,
            country = data["country"] as? String ?: "Burkina Faso",
            city = data["city"] as? String ?: "",
            region = data["region"] as? String ?: "Centre",
            jobTitle = data["jobTitle"] as? String ?: "",
            subject = data["subject"] as? String ?: "",
            schoolLevel = data["schoolLevel"] as? String ?: "",
            establishmentName = data["establishmentName"] as? String ?: "",
            establishmentId = data["establishmentId"] as? String ?: "",
            bio = data["bio"] as? String ?: "",
            photoUrl = data["photoUrl"] as? String ?: "",
            photoStoragePath = data["photoStoragePath"] as? String ?: "",
            photoData = data["photoData"] as? String ?: "",
            verified = data["verified"] as? Boolean ?: false,
            suspended = data["suspended"] as? Boolean ?: false,
            discoverable = data["discoverable"] as? Boolean ?: true,
            createdAt = data["createdAt"] as? Timestamp,
            updatedAt = data["updatedAt"] as? Timestamp
        )
    }
}
