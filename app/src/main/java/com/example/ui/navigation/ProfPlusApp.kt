package com.example.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.LocalAppCopy
import com.example.core.NotificationHelper
import com.example.core.ProfPlusContainer
import com.example.core.UiState
import com.example.core.authStateFlow
import com.example.core.copyFor
import com.example.core.signOutUser
import com.example.data.model.UserPublicProfile
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.CompleteProfileScreen
import com.example.ui.components.OfflineStatusBanner
import com.example.ui.components.ProfPlusEmptyState
import com.example.ui.components.ProfPlusWordmark
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.DebatesScreen
import com.example.ui.screens.EstablishmentsScreen
import com.example.ui.screens.GroupsScreen
import com.example.ui.screens.HomeFeedScreen
import com.example.ui.screens.MessagingScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ResourcesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.splash.SplashSequence
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.ProfPlusTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AiAssistantViewModel
import com.example.ui.viewmodel.CommunicationViewModel
import com.example.ui.viewmodel.CommunityViewModel
import com.example.ui.viewmodel.DebateViewModel
import com.example.ui.viewmodel.FeedViewModel
import com.example.ui.viewmodel.SessionViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

object ProfPlusRoutes {
    const val ROUTE_HOME = "home"
    const val ROUTE_DEBATES = "debates"
    const val ROUTE_GROUPS = "groups"
    const val ROUTE_RESOURCES = "resources"
    const val ROUTE_PROFILE = "profile"
    const val ROUTE_MESSAGES = "messages"
    const val ROUTE_NOTIFICATIONS = "notifications"
    const val ROUTE_ESTABLISHMENTS = "establishments"
    const val ROUTE_AI_ASSISTANT = "ai_assistant"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_ADMIN = "admin"
}

private data class PrimaryTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

@Composable
fun ProfPlusApp() {
    val context = LocalContext.current
    val container = remember(context) { ProfPlusContainer(context) }
    val scope = rememberCoroutineScope()

    val language by container.preferencesManager.languageFlow.collectAsStateWithLifecycle(initialValue = "fr")
    val darkMode by container.preferencesManager.darkModeFlow.collectAsStateWithLifecycle(initialValue = false)
    val localAlerts by container.preferencesManager.localAlertsFlow.collectAsStateWithLifecycle(initialValue = true)
    val isOnline by container.networkMonitor.isOnlineFlow.collectAsStateWithLifecycle(initialValue = true)
    val firebaseUser by remember { Firebase.auth.authStateFlow() }.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    var splashFinished by rememberSaveable { mutableStateOf(false) }

    CompositionLocalProvider(LocalAppCopy provides copyFor(language)) {
        ProfPlusTheme(darkTheme = darkMode) {
            if (!splashFinished) {
                SplashSequence(onFinished = { splashFinished = true })
            } else if (firebaseUser == null) {
                AuthScreen(
                    currentLanguage = language,
                    onLanguageToggle = { nextLang ->
                        scope.launch { container.preferencesManager.setLanguage(nextLang) }
                    },
                    onAuthSuccess = {}
                )
            } else {
                AuthenticatedProfPlusShell(
                    firebaseUser = firebaseUser!!,
                    container = container,
                    language = language,
                    darkMode = darkMode,
                    localAlerts = localAlerts,
                    isOnline = isOnline
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedProfPlusShell(
    firebaseUser: FirebaseUser,
    container: ProfPlusContainer,
    language: String,
    darkMode: Boolean,
    localAlerts: Boolean,
    isOnline: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val copy = LocalAppCopy.current

    val vmFactory = remember(firebaseUser.uid, container) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when (modelClass) {
                    SessionViewModel::class.java -> SessionViewModel(
                        container.userRepository,
                        container.notificationRepository,
                        firebaseUser.uid
                    ) as T
                    FeedViewModel::class.java -> FeedViewModel(
                        container.feedRepository,
                        container.notificationRepository
                    ) as T
                    DebateViewModel::class.java -> DebateViewModel(
                        container.debateRepository,
                        container.feedRepository,
                        container.notificationRepository,
                        firebaseUser.uid
                    ) as T
                    CommunityViewModel::class.java -> CommunityViewModel(
                        container.groupRepository,
                        container.resourceRepository,
                        container.feedRepository,
                        firebaseUser.uid
                    ) as T
                    CommunicationViewModel::class.java -> CommunicationViewModel(
                        container.messagingRepository,
                        container.establishmentRepository,
                        firebaseUser.uid
                    ) as T
                    AiAssistantViewModel::class.java -> AiAssistantViewModel(
                        container.aiAssistantRepository
                    ) as T
                    AdminViewModel::class.java -> AdminViewModel(
                        container.adminRepository
                    ) as T
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }

    val sessionViewModel: SessionViewModel = viewModel(key = "session_${firebaseUser.uid}", factory = vmFactory)
    val profileState by sessionViewModel.profileState.collectAsStateWithLifecycle()
    val sessionError by sessionViewModel.actionMessage.collectAsStateWithLifecycle()
    val isBusy by sessionViewModel.isBusy.collectAsStateWithLifecycle()

    when (val state = profileState) {
        is UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NavyPrimary)
                    Spacer(Modifier.padding(6.dp))
                    Text("Chargement de votre espace prof+...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        is UiState.Error -> {
            ProfPlusEmptyState(
                title = "Erreur de chargement du profil",
                subtitle = state.message
            )
        }
        is UiState.Success -> {
            val profile = state.data
            if (profile == null) {
                CompleteProfileScreen(
                    initialEmail = firebaseUser.email.orEmpty(),
                    initialDisplayName = firebaseUser.displayName.orEmpty(),
                    preferredLanguage = language,
                    isBusy = isBusy,
                    errorMessage = sessionError,
                    onSubmit = { fn, ln, role, ctry, cty, reg, job, subj, lvl, est, bio, photo ->
                        sessionViewModel.createInitialProfile(
                            fn, ln, role, ctry, cty, reg, job, subj, lvl, est, bio, photo, language
                        )
                    },
                    onSignOut = {
                        signOutUser(credentialManager, scope)
                    }
                )
            } else if (profile.suspended) {
                SuspendedAccountNotice(
                    onSignOut = { signOutUser(credentialManager, scope) }
                )
            } else {
                MainAppScaffold(
                    firebaseUser = firebaseUser,
                    currentUser = profile,
                    container = container,
                    vmFactory = vmFactory,
                    sessionViewModel = sessionViewModel,
                    language = language,
                    darkMode = darkMode,
                    localAlerts = localAlerts,
                    isOnline = isOnline,
                    onSignOut = { signOutUser(credentialManager, scope) },
                    onLanguageChange = { nextLang ->
                        scope.launch { container.preferencesManager.setLanguage(nextLang) }
                    },
                    onDarkModeChange = { enabled ->
                        scope.launch { container.preferencesManager.setDarkMode(enabled) }
                    },
                    onLocalAlertsChange = { enabled ->
                        scope.launch { container.preferencesManager.setLocalAlerts(enabled) }
                    }
                )
            }
        }
    }
}

@Composable
private fun SuspendedAccountNotice(onSignOut: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Compte suspendu", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
            Text(
                "Votre compte prof+ a été temporairement suspendu par l'équipe de modération ONADJA PRODUCTION.",
                style = MaterialTheme.typography.bodyMedium
            )
            androidx.compose.material3.Button(onClick = onSignOut) {
                Text("Se déconnecter")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppScaffold(
    firebaseUser: FirebaseUser,
    currentUser: UserPublicProfile,
    container: ProfPlusContainer,
    vmFactory: ViewModelProvider.Factory,
    sessionViewModel: SessionViewModel,
    language: String,
    darkMode: Boolean,
    localAlerts: Boolean,
    isOnline: Boolean,
    onSignOut: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onLocalAlertsChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val copy = LocalAppCopy.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ProfPlusRoutes.ROUTE_HOME

    val feedViewModel: FeedViewModel = viewModel(key = "feed_${firebaseUser.uid}", factory = vmFactory)
    val debateViewModel: DebateViewModel = viewModel(key = "debate_${firebaseUser.uid}", factory = vmFactory)
    val communityViewModel: CommunityViewModel = viewModel(key = "comm_${firebaseUser.uid}", factory = vmFactory)
    val communicationViewModel: CommunicationViewModel = viewModel(key = "msg_${firebaseUser.uid}", factory = vmFactory)
    val aiViewModel: AiAssistantViewModel = viewModel(key = "ai_${firebaseUser.uid}", factory = vmFactory)
    val adminViewModel: AdminViewModel = viewModel(key = "admin_${firebaseUser.uid}", factory = vmFactory)

    val privateInfo by sessionViewModel.privateInfo.collectAsStateWithLifecycle()
    val bookmarks by sessionViewModel.bookmarks.collectAsStateWithLifecycle()
    val notifications by sessionViewModel.notifications.collectAsStateWithLifecycle()
    val directory by sessionViewModel.directory.collectAsStateWithLifecycle()
    val privileges by sessionViewModel.privileges.collectAsStateWithLifecycle()

    val sessionMsg by sessionViewModel.actionMessage.collectAsStateWithLifecycle()
    val feedMsg by feedViewModel.feedback.collectAsStateWithLifecycle()
    val debateMsg by debateViewModel.feedback.collectAsStateWithLifecycle()
    val commMsg by communityViewModel.feedback.collectAsStateWithLifecycle()
    val msgFeedback by communicationViewModel.feedback.collectAsStateWithLifecycle()
    val adminMsg by adminViewModel.feedback.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sessionMsg) {
        sessionMsg?.let { snackbarHostState.showSnackbar(it); sessionViewModel.clearMessage() }
    }
    LaunchedEffect(feedMsg) {
        feedMsg?.let { snackbarHostState.showSnackbar(it); feedViewModel.clearFeedback() }
    }
    LaunchedEffect(debateMsg) {
        debateMsg?.let { snackbarHostState.showSnackbar(it); debateViewModel.clearFeedback() }
    }
    LaunchedEffect(commMsg) {
        commMsg?.let { snackbarHostState.showSnackbar(it); communityViewModel.clearFeedback() }
    }
    LaunchedEffect(msgFeedback) {
        msgFeedback?.let { snackbarHostState.showSnackbar(it); communicationViewModel.clearFeedback() }
    }
    LaunchedEffect(adminMsg) {
        adminMsg?.let { snackbarHostState.showSnackbar(it); adminViewModel.clearFeedback() }
    }

    // Trigger Android local alert when a new unread notification arrives
    var lastNotifiedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notifications, localAlerts) {
        val newestUnread = notifications.firstOrNull { !it.read }
        if (localAlerts && newestUnread != null && newestUnread.id != lastNotifiedId) {
            lastNotifiedId = newestUnread.id
            NotificationHelper.showLocalNotification(
                context = context,
                id = newestUnread.id.hashCode(),
                title = newestUnread.title.ifBlank { "prof+" },
                body = newestUnread.body
            )
        }
    }

    var showCreatePostDialog by remember { mutableStateOf(false) }
    var showCreateDebateDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showCreateResourceDialog by remember { mutableStateOf(false) }

    val primaryTabs = remember(copy) {
        listOf(
            PrimaryTabItem(ProfPlusRoutes.ROUTE_HOME, copy.navHome, Icons.Filled.Home, "nav_home"),
            PrimaryTabItem(ProfPlusRoutes.ROUTE_DEBATES, copy.navDebates, Icons.Filled.Forum, "nav_debates"),
            PrimaryTabItem(ProfPlusRoutes.ROUTE_GROUPS, copy.navGroups, Icons.Filled.Groups, "nav_groups"),
            PrimaryTabItem(ProfPlusRoutes.ROUTE_RESOURCES, copy.navResources, Icons.AutoMirrored.Filled.MenuBook, "nav_resources"),
            PrimaryTabItem(ProfPlusRoutes.ROUTE_PROFILE, copy.navProfile, Icons.Filled.Person, "nav_profile")
        )
    }

    val isPrimaryRoute = primaryTabs.any { it.route == currentRoute }
    val unreadCount = remember(notifications) { notifications.count { !it.read } }
    val blockedIds = privateInfo?.blockedUserIds.orEmpty()
    val (isAdmin, isMod) = privileges

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavRail && isPrimaryRoute) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(Modifier.padding(top = 12.dp))
                    primaryTabs.forEach { tab ->
                        NavigationRailItem(
                            selected = currentRoute == tab.route,
                            onClick = { navigateTopTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (isPrimaryRoute) {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = NavyPrimary,
                                titleContentColor = Color.White,
                                actionIconContentColor = Color.White
                            ),
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ProfPlusWordmark(profColor = Color.White, plusColor = GoldAccent, fontSize = 22.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = Color.White.copy(alpha = 0.14f),
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = if (currentUser.country == "Burkina Faso") "BF" else "AF",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GoldAccent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { navController.navigate(ProfPlusRoutes.ROUTE_AI_ASSISTANT) },
                                    modifier = Modifier.testTag("topbar_ai_btn")
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = copy.aiAssistant, tint = GoldAccent)
                                }
                                IconButton(
                                    onClick = { navController.navigate(ProfPlusRoutes.ROUTE_ESTABLISHMENTS) },
                                    modifier = Modifier.testTag("topbar_establishments_btn")
                                ) {
                                    Icon(Icons.Filled.AccountBalance, contentDescription = copy.establishments)
                                }
                                IconButton(
                                    onClick = { navController.navigate(ProfPlusRoutes.ROUTE_MESSAGES) },
                                    modifier = Modifier.testTag("topbar_messages_btn")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = copy.messages)
                                }
                                IconButton(
                                    onClick = { navController.navigate(ProfPlusRoutes.ROUTE_NOTIFICATIONS) },
                                    modifier = Modifier.testTag("topbar_notifications_btn")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (unreadCount > 0) {
                                                Badge(containerColor = GoldAccent, contentColor = NavyPrimary) {
                                                    Text("$unreadCount")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Notifications, contentDescription = copy.notifications)
                                    }
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    if (!useNavRail && isPrimaryRoute) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            primaryTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = { navigateTopTab(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    when (currentRoute) {
                        ProfPlusRoutes.ROUTE_HOME -> {
                            FloatingActionButton(
                                onClick = { showCreatePostDialog = true },
                                containerColor = NavyPrimary,
                                contentColor = Color.White,
                                modifier = Modifier.testTag("fab_create_post")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Nouvelle publication")
                            }
                        }
                        ProfPlusRoutes.ROUTE_DEBATES -> {
                            FloatingActionButton(
                                onClick = { showCreateDebateDialog = true },
                                containerColor = NavyPrimary,
                                contentColor = Color.White,
                                modifier = Modifier.testTag("fab_create_debate")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Nouveau débat")
                            }
                        }
                        ProfPlusRoutes.ROUTE_GROUPS -> {
                            FloatingActionButton(
                                onClick = { showCreateGroupDialog = true },
                                containerColor = NavyPrimary,
                                contentColor = Color.White,
                                modifier = Modifier.testTag("fab_create_group")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Nouveau groupe")
                            }
                        }
                        ProfPlusRoutes.ROUTE_RESOURCES -> {
                            FloatingActionButton(
                                onClick = { showCreateResourceDialog = true },
                                containerColor = NavyPrimary,
                                contentColor = Color.White,
                                modifier = Modifier.testTag("fab_create_resource")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Nouvelle ressource")
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (!isOnline) {
                        OfflineStatusBanner(copy.offlineBanner)
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ProfPlusRoutes.ROUTE_HOME,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(ProfPlusRoutes.ROUTE_HOME) {
                            HomeFeedScreen(
                                currentUser = currentUser,
                                blockedUserIds = blockedIds,
                                bookmarks = bookmarks,
                                feedViewModel = feedViewModel,
                                showCreateDialog = showCreatePostDialog,
                                onDismissCreateDialog = { showCreatePostDialog = false },
                                onToggleBookmark = { post, currentlyBookmarked ->
                                    sessionViewModel.toggleBookmark(
                                        targetId = post.id,
                                        targetType = "POST",
                                        title = post.authorName,
                                        subtitle = post.content.take(120),
                                        isBookmarked = currentlyBookmarked
                                    )
                                }
                            )
                        }
                        composable(ProfPlusRoutes.ROUTE_DEBATES) {
                            DebatesScreen(
                                currentUser = currentUser,
                                debateViewModel = debateViewModel,
                                showCreateDialog = showCreateDebateDialog,
                                onDismissCreateDialog = { showCreateDebateDialog = false }
                            )
                        }
                        composable(ProfPlusRoutes.ROUTE_GROUPS) {
                            GroupsScreen(
                                currentUser = currentUser,
                                communityViewModel = communityViewModel,
                                showCreateDialog = showCreateGroupDialog,
                                onDismissCreateDialog = { showCreateGroupDialog = false }
                            )
                        }
                        composable(ProfPlusRoutes.ROUTE_RESOURCES) {
                            ResourcesScreen(
                                currentUser = currentUser,
                                communityViewModel = communityViewModel,
                                showCreateDialog = showCreateResourceDialog,
                                onDismissCreateDialog = { showCreateResourceDialog = false }
                            )
                        }
                        composable(ProfPlusRoutes.ROUTE_PROFILE) {
                            ProfileScreen(
                                currentUser = currentUser,
                                email = firebaseUser.email.orEmpty(),
                                isAdminOrMod = isAdmin || isMod,
                                unreadNotificationsCount = unreadCount,
                                sessionViewModel = sessionViewModel,
                                onOpenMessages = { navController.navigate(ProfPlusRoutes.ROUTE_MESSAGES) },
                                onOpenNotifications = { navController.navigate(ProfPlusRoutes.ROUTE_NOTIFICATIONS) },
                                onOpenEstablishments = { navController.navigate(ProfPlusRoutes.ROUTE_ESTABLISHMENTS) },
                                onOpenAiAssistant = { navController.navigate(ProfPlusRoutes.ROUTE_AI_ASSISTANT) },
                                onOpenSettings = { navController.navigate(ProfPlusRoutes.ROUTE_SETTINGS) },
                                onOpenAdmin = { navController.navigate(ProfPlusRoutes.ROUTE_ADMIN) }
                            )
                        }
                        composable(ProfPlusRoutes.ROUTE_MESSAGES) {
                            MessagingScreen(
                                currentUser = currentUser,
                                blockedUserIds = blockedIds,
                                directory = directory,
                                communicationViewModel = communicationViewModel,
                                sessionViewModel = sessionViewModel,
                                feedViewModel = feedViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ProfPlusRoutes.ROUTE_NOTIFICATIONS) {
                            NotificationsScreen(
                                sessionViewModel = sessionViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ProfPlusRoutes.ROUTE_ESTABLISHMENTS) {
                            EstablishmentsScreen(
                                currentUser = currentUser,
                                communicationViewModel = communicationViewModel,
                                sessionViewModel = sessionViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ProfPlusRoutes.ROUTE_AI_ASSISTANT) {
                            AiAssistantScreen(
                                aiViewModel = aiViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ProfPlusRoutes.ROUTE_SETTINGS) {
                            SettingsScreen(
                                currentLanguage = language,
                                darkMode = darkMode,
                                localAlertsEnabled = localAlerts,
                                email = firebaseUser.email.orEmpty(),
                                isEmailVerified = firebaseUser.isEmailVerified,
                                sessionViewModel = sessionViewModel,
                                onLanguageChange = onLanguageChange,
                                onDarkModeChange = onDarkModeChange,
                                onLocalAlertsChange = onLocalAlertsChange,
                                onSignOut = onSignOut,
                                onAccountDeleted = onSignOut,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ProfPlusRoutes.ROUTE_ADMIN) {
                            AdminDashboardScreen(
                                currentUser = currentUser,
                                isAdmin = isAdmin,
                                adminViewModel = adminViewModel,
                                sessionViewModel = sessionViewModel,
                                feedViewModel = feedViewModel,
                                debateViewModel = debateViewModel,
                                communityViewModel = communityViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
