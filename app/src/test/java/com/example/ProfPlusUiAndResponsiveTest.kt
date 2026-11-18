package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.LocalAppCopy
import com.example.core.copyFor
import com.example.data.local.DownloadedResourceEntity
import com.example.data.local.ProfPlusDatabase
import com.example.data.model.UserPublicProfile
import com.example.data.model.UserRole
import com.example.data.repository.AdminRepository
import com.example.data.repository.AiAssistantRepository
import com.example.data.repository.AiTaskMode
import com.example.data.repository.DebateRepository
import com.example.data.repository.FeedRepository
import com.example.data.repository.GroupRepository
import com.example.data.repository.NotificationRepository
import com.example.data.repository.ResourceRepository
import com.example.data.repository.UserRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.DebatesScreen
import com.example.ui.screens.GroupsScreen
import com.example.ui.screens.HomeFeedScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ResourcesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ProfPlusTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AiAssistantViewModel
import com.example.ui.viewmodel.CommunityViewModel
import com.example.ui.viewmodel.DebateViewModel
import com.example.ui.viewmodel.FeedSortMode
import com.example.ui.viewmodel.FeedViewModel
import com.example.ui.viewmodel.SessionViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfPlusUiAndResponsiveTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var roomDb: ProfPlusDatabase
    private val sampleTeacher = UserPublicProfile(
        userId = "teacher_bf_1",
        firstName = "Amina",
        lastName = "Ouédraogo",
        displayName = "Amina Ouédraogo",
        role = UserRole.ENSEIGNANT.name,
        country = "Burkina Faso",
        city = "Ouagadougou",
        region = "Centre",
        subject = "Mathématiques",
        schoolLevel = "Lycée",
        verified = true
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        roomDb = Room.inMemoryDatabaseBuilder(context, ProfPlusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        roomDb.close()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w320dp-h568dp-mdpi")
    fun `compact narrow phone renders auth screen cleanly without crashing`() {
        var lang = "fr"
        composeRule.setContent {
            CompositionLocalProvider(LocalAppCopy provides copyFor(lang)) {
                ProfPlusTheme {
                    AuthScreen(
                        currentLanguage = lang,
                        onLanguageToggle = { lang = it },
                        onAuthSuccess = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("language_toggle_button").assertIsDisplayed().performClick()
        assertEquals("en", lang)
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h1280dp-xhdpi")
    fun `tablet layout renders home feed filter controls and create post dialog validation`() {
        val feedVm = FeedViewModel(
            FeedRepository(Firebase.firestore, Firebase.auth),
            NotificationRepository(Firebase.firestore, Firebase.auth)
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalAppCopy provides copyFor("fr")) {
                ProfPlusTheme {
                    HomeFeedScreen(
                        currentUser = sampleTeacher,
                        blockedUserIds = emptyList(),
                        bookmarks = emptyList(),
                        feedViewModel = feedVm,
                        showCreateDialog = true,
                        onDismissCreateDialog = {},
                        onToggleBookmark = { _, _ -> }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sort_relevance_chip").performClick()
        assertEquals(FeedSortMode.PERTINENCE, feedVm.sortMode.value)

        composeRule.onNodeWithTag("feed_search_input").performTextInput("Algèbre")
        assertEquals("Algèbre", feedVm.searchQuery.value)

        composeRule.onNodeWithTag("publish_post_confirm_btn").assertIsNotEnabled()
        composeRule.onNodeWithTag("create_post_content_input").performTextInput("Fiche exercices 3ème")
        composeRule.onNodeWithTag("publish_post_confirm_btn").assertIsEnabled()
    }

    @Test
    fun `debates and groups creation dialogs validate required fields before submission`() {
        val debateVm = DebateViewModel(
            DebateRepository(Firebase.firestore, Firebase.auth),
            FeedRepository(Firebase.firestore, Firebase.auth),
            NotificationRepository(Firebase.firestore, Firebase.auth),
            sampleTeacher.userId
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalAppCopy provides copyFor("fr")) {
                ProfPlusTheme {
                    DebatesScreen(
                        currentUser = sampleTeacher,
                        debateViewModel = debateVm,
                        showCreateDialog = true,
                        onDismissCreateDialog = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("debate_submit_btn").assertIsNotEnabled()
        composeRule.onNodeWithTag("debate_title_input").performTextInput("Évaluation continue")
        composeRule.onNodeWithTag("debate_question_input").performTextInput("Comment harmoniser les barèmes au lycée ?")
        composeRule.onNodeWithTag("debate_submit_btn").assertIsEnabled()
    }

    @Test
    fun `groups create dialog validates name before enabling creation button`() {
        val communityVm = CommunityViewModel(
            GroupRepository(Firebase.firestore, Firebase.auth),
            ResourceRepository(Firebase.firestore, roomDb.downloadedResourceDao(), Firebase.auth),
            FeedRepository(Firebase.firestore, Firebase.auth),
            sampleTeacher.userId
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalAppCopy provides copyFor("fr")) {
                ProfPlusTheme {
                    GroupsScreen(
                        currentUser = sampleTeacher,
                        communityViewModel = communityVm,
                        showCreateDialog = true,
                        onDismissCreateDialog = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("group_submit_btn").assertIsNotEnabled()
        composeRule.onNodeWithTag("group_name_input").performTextInput("Professeurs de Physique BF")
        composeRule.onNodeWithTag("group_submit_btn").assertIsEnabled()
    }

    @Test
    fun `resources offline tab displays local Room cached resources even when offline`() = runTest {
        roomDb.downloadedResourceDao().upsert(
            DownloadedResourceEntity(
                resourceId = "res_offline_1",
                title = "Annales BEPC Mathématiques",
                description = "Sujets et corrigés 2020-2025",
                resourceType = "CORRIGE",
                subject = "Mathématiques",
                level = "Collège",
                fileName = "annales_bepc.txt",
                mimeType = "text/plain",
                fileSizeBytes = 1024L,
                contentBody = "Exercice 1 : Calcul littéral...",
                authorName = "Issa Kaboré"
            )
        )
        val communityVm = CommunityViewModel(
            GroupRepository(Firebase.firestore, Firebase.auth),
            ResourceRepository(Firebase.firestore, roomDb.downloadedResourceDao(), Firebase.auth),
            FeedRepository(Firebase.firestore, Firebase.auth),
            sampleTeacher.userId
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalAppCopy provides copyFor("fr")) {
                ProfPlusTheme {
                    ResourcesScreen(
                        currentUser = sampleTeacher,
                        communityViewModel = communityVm,
                        showCreateDialog = false,
                        onDismissCreateDialog = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("resources_tab_offline").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Annales BEPC Mathématiques").assertIsDisplayed()
    }

    @Test
    fun `settings screen updates language dark mode and triggers sign out cleanly`() {
        val sessionVm = SessionViewModel(
            UserRepository(Firebase.firestore, Firebase.auth),
            NotificationRepository(Firebase.firestore, Firebase.auth),
            sampleTeacher.userId
        )
        var selectedLang = "fr"
        var signedOut = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppCopy provides copyFor(selectedLang)) {
                ProfPlusTheme {
                    SettingsScreen(
                        currentLanguage = selectedLang,
                        darkMode = false,
                        localAlertsEnabled = true,
                        email = "amina@profplus.bf",
                        isEmailVerified = true,
                        sessionViewModel = sessionVm,
                        onLanguageChange = { selectedLang = it },
                        onDarkModeChange = {},
                        onLocalAlertsChange = {},
                        onSignOut = { signedOut = true },
                        onAccountDeleted = {},
                        onBack = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_lang_en").performClick()
        assertEquals("en", selectedLang)

        composeRule.onNodeWithTag("settings_sign_out_btn").performScrollTo().performClick()
        assertTrue(signedOut)
    }

    @Test
    fun `ai assistant screen switches modes and enables send only with prompt`() {
        val aiVm = AiAssistantViewModel(AiAssistantRepository())
        composeRule.setContent {
            CompositionLocalProvider(LocalAppCopy provides copyFor("fr")) {
                ProfPlusTheme {
                    AiAssistantScreen(aiViewModel = aiVm, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_mode_resume").performClick()
        assertEquals(AiTaskMode.RESUME, aiVm.selectedMode.value)

        composeRule.onNodeWithTag("ai_send_btn").assertIsNotEnabled()
        composeRule.onNodeWithTag("ai_prompt_input").performTextInput("Résume les objectifs du cours sur les fractions.")
        composeRule.onNodeWithTag("ai_send_btn").assertIsEnabled()
    }

    @Test
    fun `admin dashboard restricts verification approvals to administrators only`() {
        val db = Firebase.firestore
        val auth = Firebase.auth
        val adminVm = AdminViewModel(AdminRepository(db, auth))
        val sessionVm = SessionViewModel(UserRepository(db, auth), NotificationRepository(db, auth), sampleTeacher.userId)
        val feedVm = FeedViewModel(FeedRepository(db, auth), NotificationRepository(db, auth))
        val debateVm = DebateViewModel(DebateRepository(db, auth), FeedRepository(db, auth), NotificationRepository(db, auth), sampleTeacher.userId)
        val commVm = CommunityViewModel(
            GroupRepository(db, auth),
            ResourceRepository(db, roomDb.downloadedResourceDao(), auth),
            FeedRepository(db, auth),
            sampleTeacher.userId
        )

        // Render as MODERATEUR (isAdmin = false)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppCopy provides copyFor("fr")) {
                ProfPlusTheme {
                    AdminDashboardScreen(
                        currentUser = sampleTeacher.copy(role = UserRole.MODERATEUR.name),
                        isAdmin = false,
                        adminViewModel = adminVm,
                        sessionViewModel = sessionVm,
                        feedViewModel = feedVm,
                        debateViewModel = debateVm,
                        communityViewModel = commVm,
                        onBack = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_tab_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Accès réservé aux administrateurs").assertIsDisplayed()
        assertFalse(composeRule.onAllNodesWithText("Ajouter une catégorie").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `notifications screen renders empty state cleanly`() {
        val sessionVm = SessionViewModel(
            UserRepository(Firebase.firestore, Firebase.auth),
            NotificationRepository(Firebase.firestore, Firebase.auth),
            sampleTeacher.userId
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalAppCopy provides copyFor("fr")) {
                ProfPlusTheme {
                    NotificationsScreen(sessionViewModel = sessionVm, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Aucune notification").assertIsDisplayed()
    }
}
