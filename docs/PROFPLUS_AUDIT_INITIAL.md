# AUDIT INITIAL — PROF+ (PHASE 0)

**Date d'audit :** 26 novembre 2026  
**Statut :** Audit complet de la base de départ avant toute modification de code (Phase 0).

---

## 1. Base auditée et sauvegarde de référence

- **Archive cible mentionnée (`Prof--main (3).zip`)** : le fichier ZIP physique `Prof--main (3).zip` n'est pas présent dans les dossiers du conteneur (`/app`, `/app/applet`, `/tmp`, `/root`, `/home`).
- **Base réelle disponible et auditée** : le projet actif dans `/app/applet` correspond directement à l'application Android `prof+` décrite dans l'audit de référence (`com.example`, 12 écrans Compose, 11 repositories dont Storage & AI, `UserRole` historique, `isAdmin()`, Firebase BoM, Room, Cloud Functions et tests).
- **Sauvegarde Phase 0 effectuée avant modification** : `/tmp/profplus-phase0-baseline.tar.gz` (31 Mo, hors caches Gradle/build/node_modules) conserve l'état exact de départ.

---

## 2. Arborescence inventoriée

### Racine & CI/CD
- `build.gradle.kts`, `settings.gradle.kts` (`rootProject.name = "prof+"`), `gradle.properties`, `gradle/libs.versions.toml`
- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (Gradle `9.3.1`)
- `.github/workflows/prof-plus-android.yml`
- `metadata.json`, `.gitignore`, `.env.example` (ne contient aucun secret), `debug.keystore.base64`

### Firebase & Backend
- `app/google-services.json` (`project_id`: `gen-lang-client-0467158321`, `package_name`: `com.aistudio.profplus.bfkdqz`, `storage_bucket`: `gen-lang-client-0467158321.firebasestorage.app`)
- `app/src/main/res/values/firebase_applet_config.xml` (`firestore_database_id`: `ai-studio-android-prof-ba263d8f-d3ab-4a71-b325-29ccd86045ee`, `recaptcha_site_key`)
- `firebase.json` (cible uniquement la base nommée + port émulateur Firestore `8085`)
- `firebase-blueprint.json`, `firestore.rules` (868 lignes), `storage.rules` (74 lignes), `firestore.test.js` (1080 lignes)
- `functions/package.json`, `functions/index.js`, `functions/handlers.js`, `functions/functions.test.js`

### Code source Android (`app/src/main/java/com/example/`)
- **Point d'entrée** : `MainActivity.kt` (App Check via `configureAppCheck(this, intent)`, `enableEdgeToEdge()`, `setContent { ProfPlusApp() }`)
- **Core (`core/`)** : `AppCheckConfig.kt`, `AppStrings.kt`, `AuthManager.kt`, `FirestoreErrorHandler.kt`, `MediaUtils.kt`, `NetworkMonitor.kt`, `NotificationHelper.kt`, `PreferencesManager.kt`, `ProfPlusContainer.kt`, `UiState.kt`
- **Local Room (`data/local/`)** : `ProfPlusDatabase.kt` (`profplus_offline_cache.db`, version `1`, table `downloaded_resources`)
- **Modèles (`data/model/`)** : `Constants.kt`, `Models.kt`
- **Repositories (`data/repository/`)** : `AdminRepository.kt`, `AiAssistantRepository.kt`, `DebateRepository.kt`, `EstablishmentRepository.kt`, `FeedRepository.kt`, `GroupRepository.kt`, `MessagingRepository.kt`, `NotificationRepository.kt`, `ResourceRepository.kt`, `StorageRepository.kt`, `UserRepository.kt`
- **UI & Navigation (`ui/`)** :
  - `auth/AuthScreens.kt` (`AuthScreen`, `CompleteProfileScreen`)
  - `splash/SplashScreens.kt` (`SplashSequence`, `ProfPlusSplashScreen`, `OnadjaProductionSplashScreen`)
  - `navigation/ProfPlusApp.kt` (`ProfPlusRoutes` : 11 routes)
  - `components/ProfPlusComponents.kt`, `theme/{Color,Theme,Type}.kt`
  - `screens/` : `HomeFeedScreen.kt`, `DebatesScreen.kt`, `GroupsScreen.kt`, `ResourcesScreen.kt`, `ProfileScreen.kt`, `MessagingScreen.kt`, `NotificationsScreen.kt`, `EstablishmentsScreen.kt`, `AiAssistantScreen.kt`, `SettingsScreen.kt`, `AdminDashboardScreen.kt`
  - `viewmodel/` : `SessionViewModel.kt`, `FeedViewModel.kt`, `DebateViewModel.kt`, `CommunityViewModel.kt`, `CommunicationViewModel.kt`, `AiAssistantViewModel.kt`, `AdminViewModel.kt`

### Ressources & Tests
- `app/src/main/AndroidManifest.xml` (`allowBackup="false"`, permissions `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`)
- `app/src/main/res/values/strings.xml` (`app_name = "prof+"`, `production_signature = "ONADJA PRODUCTION"`) et `values-en/strings.xml`
- Polices (`inter.ttf`, `playfair_display.ttf`, `plus_jakarta_sans.ttf`), drawables (`img_profplus_logo.jpg`, `img_hero_banner.jpg`, `img_academic_collab.jpg`), icônes adaptatives complètes
- `app/src/test/java/com/example/` : `ExampleUnitTest.kt`, `ExampleRobolectricTest.kt`, `ProfPlusFeatureTest.kt`, `ProfPlusUiAndResponsiveTest.kt`
- `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt`

---

## 3. Configuration technique & dépendances vérifiées

| Élément | Valeur auditée |
|---|---|
| Gradle Wrapper | `9.3.1` |
| AGP | `9.1.1` |
| Kotlin / Compose Plugin | `2.4.21` |
| KSP | `2.3.5` |
| `compileSdk` / `targetSdk` / `minSdk` | `36` / `36` / `24` |
| `namespace` / `applicationId` | `com.example` / `com.aistudio.profplus.bfkdqz` |
| Firebase BoM | `35.2.0` (`firebase-ai`, `firebase-firestore`, `firebase-auth`, `firebase-storage`, `firebase-appcheck-recaptcha`, `firebase-appcheck-debug`) |
| Google Services Plugin | `4.6.0` |
| Room | `2.7.0` (avec KSP) |
| Jetpack Navigation Compose | `2.8.9` |
| Credential Manager / GoogleId | `1.5.0` / `1.1.1` |
| Robolectric | `4.16.1` |

**Note de conformité produit sur le Splash 2 :** `OnadjaProductionSplashScreen` affiche actuellement `"PRÉSENTE"`, `"ONADJA"`, `"PRODUCTION"`. Lors des phases UI, vérifier si l'affichage exact `"Onadja production"` (casse mixte ou signature) nécessite une harmonisation visuelle sans perdre le style existant.

---

## 4. État des rôles, de l'IA et des données Firebase

### Rôles & profils existants (`Models.kt`)
- `UserRole` : `ENSEIGNANT`, `RESPONSABLE_ETABLISSEMENT`, `MODERATEUR`, `ADMINISTRATEUR`.
- `UserPublicProfile` contient les champs historiques `establishmentName: String`, `establishmentId: String`, `role: String`, `verified`, `suspended`, `discoverable`, `photoUrl`, `photoStoragePath`, `photoData` (conservé en lecture seule de compatibilité).
- `UserRepository.createInitialProfile(...)` restreint la création client à `ENSEIGNANT` ou `RESPONSABLE_ETABLISSEMENT` (`verified = false`, `suspended = false`).

### Assistant IA (`AiAssistantRepository.kt`)
- Moteur : **Firebase AI Logic** (`Firebase.ai.generativeModel(...)`) — aucune clé API cliente.
- Modèle principal : `gemini-4-hydrogen`, repli automatique sur HTTP 404 vers `gemini-3.8-flash`.
- `ThinkingConfig` : `ThinkingLevel.LOW`.
- Protection : prompt système en français, limite `4000` caractères, encadrement anti-injection `<demande_utilisateur>...</demande_utilisateur>`, délai `60_000L`.
- Modes actuels : `PREP_COURS`, `SUGGESTIONS`, `RESUME`, `EXPLICATION`, `ORGANISATION`, `DEBAT`, `REFORMULATION`.
- **Écarts identifiés pour l'évolution scolaire** : ajouter mention explicite des limites IA + validation humaine obligatoire pour notes/bulletins/décisions, masquage des PII élèves, et gestion dédiée du mode hors ligne.

### Collections Firestore & chemins Storage existants
- **Collections racine Firestore** :
  - `users/{userId}` (+ sous-collections `private/{docId}` et `bookmarks/{bookmarkId}`)
  - `posts/{postId}` (+ sous-collections `reactions/{userId}` et `shares/{shareId}`)
  - `comments/{commentId}`
  - `debates/{debateId}`, `polls/{pollId}`, `votes/{voteId}`
  - `groups/{groupId}`, `group_members/{memberDocId}` (`{groupId}_{userId}`)
  - `resources/{resourceId}` (+ sous-collection `downloads/{downloadId}`)
  - `establishments/{establishmentId}`
  - `conversations/{conversationId}`, `messages/{messageId}`
  - `notifications/{notificationId}` (création client interdite : serveur uniquement)
  - `reports/{reportId}`, `verification_requests/{requestId}`, `admin_logs/{logId}`
  - `admins/{userId}`, `moderators/{userId}`, `app_categories/{categoryId}`
- **Firebase Storage (`storage.rules`)** :
  - `/avatars/{userId}/{fileName}` (max 5 Mo, `image/*`)
  - `/posts/{userId}/images/{fileName}` (max 5 Mo) et `/posts/{userId}/attachments/{fileName}` (max 10 Mo)
  - `/messages/{conversationId}/{userId}/{fileName}` (max 10 Mo, lecture réservée aux `participantIds`)
  - `/resources/{userId}/{fileName}` (max 20 Mo)
- **Cloud Functions (`functions/index.js`, `functions/handlers.js`)** :
  - `onCommentCreated`, `onCommentUpdated`, `onCommentDeleted`, `onPostShareCreated`, `onResourceDownloadCreated`, `onMessageCreated`, `onGroupMemberWritten`.

---

## 5. Résultats des tests de référence (Phase 0 — avant modification)

| Suite | Commande | Résultat |
|---|---|---|
| Cloud Functions JS | `node --test functions/functions.test.js` | **6 / 6 réussis** |
| Android Unit & Robolectric | `gradle :app:testDebugUnitTest --no-daemon` | **19 / 19 réussis** (`ExampleRobolectricTest`: 1, `ExampleUnitTest`: 1, `ProfPlusFeatureTest`: 8, `ProfPlusUiAndResponsiveTest`: 9) |
| Firestore Rules Emulator | `firebase emulators:exec --only firestore "node --test firestore.test.js"` | **10 / 10 réussis** |

*(Remarque technique environnement : le cache Maven local Robolectric du conteneur se trouve sous `/home/appuser/.m2` alors que les commandes s'exécutent en `root` ; un lien `/root/.m2 -> /home/appuser/.m2` permet l'exécution hors ligne de Robolectric sans modifier le projet.)*

---

## 6. Risques identifiés et stratégie d'évolution

1. **Compatibilité ascendante des profils & établissements** :
   - Les comptes existants n'ont que `UserRole` (`ENSEIGNANT`, `RESPONSABLE_ETABLISSEMENT`, `MODERATEUR`, `ADMINISTRATEUR`) et éventuellement `establishmentId` / `establishmentName`.
   - **Décision** : ne jamais altérer les valeurs existantes de `UserRole`, ne pas supprimer `establishmentId` / `establishmentName`, et introduire un modèle scolaire distinct (`SchoolMembership`, `SchoolRole`, `MembershipStatus`) en lecture/écriture additive.
2. **Isolation stricte Communauté vs Dossiers scolaires** :
   - Les collections sociales (`posts`, `groups`, `debates`, `resources`) restent inchangées et isolées des futures collections scolaires (`schools`, `schoolMemberships`, `schoolClasses`, `schoolSubjects`, `teacherAssignments`, `schoolStudents`, `parentLinks`, etc.).
   - Noter que `establishments/{establishmentId}` existe déjà comme **vitrine publique d'établissement** ; les nouvelles données internes d'administration/pédagogie multiétablissements utiliseront des collections dédiées (`schools/{schoolId}`, `schoolMemberships/{membershipId}`, etc.) sans doublon ni casse de `establishments`.
3. **Privilège administrateur bootstrap (`isAdmin()`)** :
   - `firestore.rules` (ligne 53) et `UserRepository.kt` (ligne 392) incluent un accès bootstrap lié à `louple815@gmail.com` en plus de `admins/{uid}`. À conserver tant que le transfert vers custom claims / `admins/{uid}` serveur n'est pas déployé et vérifié.
4. **Hors ligne & transactions** :
   - Room ne cache aujourd'hui que `downloaded_resources`. Les futurs brouillons hors ligne scolaires devront afficher explicitement l'état `EN ATTENTE DE SYNCHRONISATION` sans jamais marquer une note ou présence comme validée avant confirmation serveur.
5. **App Check hors Play Store** :
   - `AppCheckConfig.kt` utilise `DebugAppCheckProviderFactory` en debug et `RecaptchaAppCheckProviderFactory` en release : vérifier les canaux de distribution avant toute application stricte côté console Firebase.
6. **Signature Release & CI** :
   - `my-upload-key.jks` et les mots de passe ne doivent jamais être stockés dans le code/ZIP. Le packaging `assembleRelease` dépend des secrets d'environnement fournis lors de la livraison finale.
