# JOURNAL DES ÉVOLUTIONS — PROF+

## Phase 0 — Sauvegarde et Audit Initial (26 novembre 2026)
- Sauvegarde complète de référence créée dans `/tmp/profplus-phase0-baseline.tar.gz`.
- Audit initial livré dans `docs/PROFPLUS_AUDIT_INITIAL.md`.
- Tests de référence exécutés avant modification : Cloud Functions (`6/6`), Android Unit/Robolectric (`19/19`), Firestore Rules Emulator (`10/10`).

## Phase 1 — Fondation Compatible (26 novembre 2026)
- **Ajouté** : `app/src/main/java/com/example/data/model/SchoolModels.kt`
  - `SchoolRole` (`ENSEIGNANT`, `ELEVE`, `PARENT`, `RESPONSABLE_ETABLISSEMENT`, `ADMINISTRATION`), strictement séparé de `UserRole`.
  - `MembershipStatus` (`PENDING`, `ACTIVE`, `REJECTED`, `SUSPENDED`) et `SyncStatus` (`SYNCED`, `PENDING_SYNC`, `SYNC_ERROR`).
  - Modèles scolaires minimaux compatibles Firestore (`@Keep`, `@IgnoreExtraProperties`, constructeurs sans argument, `@DocumentId`, `@ServerTimestamp`) : `School`, `SchoolMembership`, `SchoolClass`, `SchoolSubject`, `TeacherAssignment`, `SchoolStudent`, `ParentLink`.
  - `LegacySchoolCompatibility` pour lire les anciens documents `UserPublicProfile` et générer un pont d'adhésion transitoire à partir de `establishmentId`/`establishmentName` sans altérer `Models.kt`.
- **Ajouté** : `app/src/test/java/com/example/Phase1SchoolFoundationTest.kt` (tests de compatibilité, constructeurs sans argument, désérialisation anciens/nouveaux documents, protection contre l'auto-élévation de rôle dans les demandes d'adhésion).
- **Préservé sans modification** : `Models.kt`, `UserRole`, `firestore.rules`, `storage.rules`, `functions/`, tous les écrans UI et repositories existants. Aucun déploiement ni migration exécuté.
