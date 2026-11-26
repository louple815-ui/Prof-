package com.example

import com.example.data.model.LegacySchoolCompatibility
import com.example.data.model.MembershipStatus
import com.example.data.model.ParentLink
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolMembership
import com.example.data.model.SchoolRole
import com.example.data.model.SchoolStudent
import com.example.data.model.SchoolSubject
import com.example.data.model.SyncStatus
import com.example.data.model.TeacherAssignment
import com.example.data.model.UserPrivateInfo
import com.example.data.model.UserPublicProfile
import com.example.data.model.UserRole
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1SchoolFoundationTest {

    @Test
    fun `historical platform UserRole values remain intact and distinct from SchoolRole`() {
        assertEquals(
            listOf("ENSEIGNANT", "RESPONSABLE_ETABLISSEMENT", "MODERATEUR", "ADMINISTRATEUR"),
            UserRole.entries.map { it.name }
        )
        assertEquals(
            listOf("ENSEIGNANT", "ELEVE", "PARENT", "RESPONSABLE_ETABLISSEMENT", "ADMINISTRATION"),
            SchoolRole.entries.map { it.name }
        )
        assertEquals(
            listOf("PENDING", "ACTIVE", "REJECTED", "SUSPENDED"),
            MembershipStatus.entries.map { it.name }
        )
        assertFalse(SchoolRole.ENSEIGNANT.canManageSchool())
        assertFalse(SchoolRole.ELEVE.canManageSchool())
        assertFalse(SchoolRole.PARENT.canManageSchool())
        assertTrue(SchoolRole.RESPONSABLE_ETABLISSEMENT.canManageSchool())
        assertTrue(SchoolRole.ADMINISTRATION.canManageSchool())
    }

    @Test
    fun `firestore pojo classes expose public no-arg constructors for document deserialization`() {
        val modelClasses = listOf(
            UserPublicProfile::class.java,
            UserPrivateInfo::class.java,
            School::class.java,
            SchoolMembership::class.java,
            SchoolClass::class.java,
            SchoolSubject::class.java,
            TeacherAssignment::class.java,
            SchoolStudent::class.java,
            ParentLink::class.java
        )
        modelClasses.forEach { clazz ->
            val instance = clazz.getDeclaredConstructor().newInstance()
            assertNotNull("Expected no-arg instance for ${clazz.simpleName}", instance)
        }
    }

    @Test
    fun `legacy user profile without new fields deserializes cleanly and bridges to establishment membership`() {
        val ts = Timestamp(1_760_000_000L, 0)
        val legacyRawDoc = mapOf<String, Any?>(
            "userId" to "uid_legacy_01",
            "firstName" to "Issa",
            "lastName" to "Kaboré",
            "displayName" to "Issa Kaboré",
            "role" to "ENSEIGNANT",
            "country" to "Burkina Faso",
            "city" to "Bobo-Dioulasso",
            "region" to "Hauts-Bassins",
            "establishmentName" to "Lycée Ouezzin Coulibaly",
            "establishmentId" to "est_loc_01",
            "verified" to true,
            "suspended" to false,
            "discoverable" to true,
            "createdAt" to ts,
            "updatedAt" to ts,
            "unknownFutureField" to "ignored_safely"
        )

        val profile = LegacySchoolCompatibility.deserializeLegacyUserProfile("uid_legacy_01", legacyRawDoc)
        assertEquals("uid_legacy_01", profile.userId)
        assertEquals("Lycée Ouezzin Coulibaly", profile.establishmentName)
        assertEquals("est_loc_01", profile.establishmentId)
        assertEquals(UserRole.ENSEIGNANT, profile.parsedRole())

        val bridged = LegacySchoolCompatibility.fromLegacyProfile(profile)
        assertNotNull(bridged)
        assertEquals("est_loc_01_uid_legacy_01", bridged!!.id)
        assertEquals("est_loc_01", bridged.schoolId)
        assertEquals("Lycée Ouezzin Coulibaly", bridged.schoolName)
        assertEquals(SchoolRole.ENSEIGNANT, bridged.parsedSchoolRole())
        assertEquals(MembershipStatus.ACTIVE, bridged.parsedStatus())
        assertTrue(bridged.legacyBridge)
    }

    @Test
    fun `legacy profile without establishment returns null bridge safely`() {
        val emptyProfile = UserPublicProfile(userId = "uid_only", displayName = "Test")
        assertNull(LegacySchoolCompatibility.fromLegacyProfile(emptyProfile))
    }

    @Test
    fun `client join request payload forces PENDING status and blocks self-elevation to school management`() {
        val forgedRequest = SchoolMembership(
            schoolId = "school_ouaga_01",
            schoolName = "Lycée Bogodogo",
            userId = "attacker_uid",
            userDisplayName = "Enseignant Test",
            schoolRole = SchoolRole.RESPONSABLE_ETABLISSEMENT.name,
            status = MembershipStatus.ACTIVE.name
        )

        val safePayload = forgedRequest.toClientJoinRequestMap(requestedByUid = "real_user_uid")
        assertEquals("school_ouaga_01", safePayload["schoolId"])
        assertEquals("real_user_uid", safePayload["userId"])
        assertEquals("real_user_uid", safePayload["createdBy"])
        assertEquals(MembershipStatus.PENDING.name, safePayload["status"])
        assertEquals(SchoolRole.ENSEIGNANT.name, safePayload["schoolRole"])
        assertTrue(safePayload["createdAt"] is FieldValue)
        assertTrue(safePayload["updatedAt"] is FieldValue)
    }

    @Test
    fun `new multi-school entities serialize and deserialize from raw maps with numeric coercion`() {
        val ts = Timestamp(1_764_000_000L, 0)

        val school = School(
            id = "school_1",
            linkedEstablishmentId = "est_1",
            name = "Lycée Philippe Zinda Kaboré",
            code = "LPZK-BF",
            city = "Ouagadougou",
            academicYear = "2026-2027",
            periods = listOf("Trimestre 1", "Trimestre 2", "Trimestre 3"),
            ownerId = "dir_1",
            createdBy = "dir_1"
        )
        val schoolMap = school.toFirestoreMap(includeServerTimestamps = false) + mapOf(
            "createdAt" to ts,
            "updatedAt" to ts
        )
        val restoredSchool = School.fromMap("school_1", schoolMap)
        assertEquals("school_1", restoredSchool.id)
        assertEquals("Lycée Philippe Zinda Kaboré", restoredSchool.name)
        assertEquals(3, restoredSchool.periods.size)
        assertFalse(schoolMap.containsKey("id"))

        val membership = SchoolMembership.fromMap(
            "school_1_prof_1",
            mapOf(
                "schoolId" to "school_1",
                "schoolName" to "LPZK",
                "userId" to "prof_1",
                "userDisplayName" to "Amina Sawadogo",
                "schoolRole" to "ENSEIGNANT",
                "status" to "ACTIVE",
                "assignedClassIds" to listOf("cls_tle_d"),
                "assignedSubjectIds" to listOf("sub_math"),
                "createdAt" to ts
            )
        )
        assertTrue(membership.isActiveMembership())
        assertEquals(SchoolRole.ENSEIGNANT, membership.parsedSchoolRole())

        val schoolClass = SchoolClass.fromMap(
            "cls_tle_d",
            mapOf("schoolId" to "school_1", "name" to "Terminale D", "level" to "Lycée", "capacity" to 55, "studentsCount" to 48)
        )
        assertEquals(55L, schoolClass.capacity)
        assertEquals(48L, schoolClass.studentsCount)

        val subject = SchoolSubject.fromMap(
            "sub_math",
            mapOf("schoolId" to "school_1", "name" to "Mathématiques", "coefficient" to 5, "classIds" to listOf("cls_tle_d"))
        )
        assertEquals(5.0, subject.coefficient, 0.001)

        val assignment = TeacherAssignment.fromMap(
            "assign_1",
            mapOf(
                "schoolId" to "school_1",
                "teacherId" to "prof_1",
                "classId" to "cls_tle_d",
                "subjectId" to "sub_math",
                "validFromMillis" to 1760000000000L
            )
        )
        assertEquals("prof_1", assignment.teacherId)
        assertNull(assignment.validToMillis)

        val student = SchoolStudent.fromMap(
            "stu_1",
            mapOf(
                "schoolId" to "school_1",
                "firstName" to "Moussa",
                "lastName" to "Traoré",
                "displayName" to "Moussa Traoré",
                "classId" to "cls_tle_d"
            )
        )
        assertEquals("Moussa Traoré", student.displayName)

        val parentLink = ParentLink.fromMap(
            "lnk_1",
            mapOf(
                "schoolId" to "school_1",
                "studentId" to "stu_1",
                "parentUserId" to "par_1",
                "status" to "ACTIVE",
                "verifiedBy" to "dir_1"
            )
        )
        assertEquals(MembershipStatus.ACTIVE, parentLink.parsedStatus())
        assertEquals("EN ATTENTE DE SYNCHRONISATION", SyncStatus.PENDING_SYNC.labelFr)
    }
}
