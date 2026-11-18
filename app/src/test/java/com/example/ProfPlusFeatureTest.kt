package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.google.firebase.FirebaseApp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.LocalAppCopy
import com.example.core.copyFor
import com.example.data.local.DownloadedResourceEntity
import com.example.data.local.ProfPlusDatabase
import com.example.data.model.ProfPlusConstants
import com.example.data.model.UserRole
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.CompleteProfileScreen
import com.example.ui.splash.SplashSequence
import com.example.ui.theme.ProfPlusTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfPlusFeatureTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var roomDb: ProfPlusDatabase

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
    fun `roles and bilingual copy match official requirements`() {
        assertEquals(UserRole.ENSEIGNANT, UserRole.fromRaw(null))
        assertEquals(UserRole.RESPONSABLE_ETABLISSEMENT, UserRole.fromRaw("RESPONSABLE_ETABLISSEMENT"))
        assertEquals(UserRole.MODERATEUR, UserRole.fromRaw("MODERATEUR"))
        assertEquals(UserRole.ADMINISTRATEUR, UserRole.fromRaw("ADMINISTRATEUR"))

        assertTrue(ProfPlusConstants.BURKINA_REGIONS.contains("Centre"))
        assertTrue(ProfPlusConstants.BURKINA_REGIONS.contains("Hauts-Bassins"))
        assertEquals(8, ProfPlusConstants.DEBATE_CATEGORIES.size)

        val fr = copyFor("fr")
        val en = copyFor("en")
        assertEquals("Le réseau professionnel des enseignants", fr.slogan)
        assertEquals("The professional network for educators", en.slogan)
    }

    @Test
    fun `room offline resource dao stores reads and deletes resources`() = runTest {
        val dao = roomDb.downloadedResourceDao()
        val entity = DownloadedResourceEntity(
            resourceId = "res_math_3e",
            title = "Théorème de Thalès - 3ème",
            description = "Fiche complète avec exercices corrigés",
            resourceType = "FICHE_PEDAGOGIQUE",
            subject = "Mathématiques",
            level = "Collège",
            fileName = "thales_3e.txt",
            mimeType = "text/plain",
            fileSizeBytes = 256L,
            contentBody = "Contenu pédagogique détaillé...",
            authorName = "Issa Ouédraogo"
        )
        dao.upsert(entity)
        val loaded = dao.getById("res_math_3e")
        assertNotNull(loaded)
        assertEquals("Théorème de Thalès - 3ème", loaded?.title)
        assertEquals(1, dao.observeAll().first().size)

        dao.deleteById("res_math_3e")
        assertNull(dao.getById("res_math_3e"))
    }

    @Test
    fun `splash screen renders official prof plus identity`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ProfPlusTheme {
                SplashSequence(onFinished = {})
            }
        }
        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.onNodeWithText("Le réseau professionnel des enseignants").assertIsDisplayed()
        composeRule.onNodeWithText("BURKINA FASO • AFRIQUE").assertIsDisplayed()

        // Advance to Splash 2 (ONADJA PRODUCTION)
        composeRule.mainClock.advanceTimeBy(1800L)
        composeRule.onNodeWithText("ONADJA").assertIsDisplayed()
        composeRule.onNodeWithText("PRODUCTION").assertIsDisplayed()
        composeRule.onNodeWithText("PRÉSENTE").assertIsDisplayed()
    }

    @Test
    fun `auth screen toggles language and displays google sign in`() {
        var toggledLang = "fr"
        composeRule.setContent {
            CompositionLocalProvider(LocalAppCopy provides copyFor("fr")) {
                ProfPlusTheme {
                    AuthScreen(
                        currentLanguage = "fr",
                        onLanguageToggle = { toggledLang = it },
                        onAuthSuccess = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("language_toggle_button").performClick()
        assertEquals("en", toggledLang)
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `complete profile screen submits valid teacher profile`() {
        var submittedRole: UserRole? = null
        var submittedFirstName = ""
        composeRule.setContent {
            CompositionLocalProvider(LocalAppCopy provides copyFor("fr")) {
                ProfPlusTheme {
                    CompleteProfileScreen(
                        initialEmail = "prof@example.bf",
                        initialDisplayName = "",
                        preferredLanguage = "fr",
                        isBusy = false,
                        errorMessage = null,
                        onSubmit = { fn, _, role, _, _, _, _, _, _, _, _, _ ->
                            submittedFirstName = fn
                            submittedRole = role
                        },
                        onSignOut = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("role_director_chip").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_first_name_input").performScrollTo().performTextInput("Aminata")
        composeRule.onNodeWithTag("profile_last_name_input").performScrollTo().performTextInput("Sawadogo")
        composeRule.onNodeWithTag("complete_profile_submit_btn").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("Aminata", submittedFirstName)
        assertEquals(UserRole.RESPONSABLE_ETABLISSEMENT, submittedRole)
    }
}
