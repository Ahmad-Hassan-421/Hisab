package com.example.hisab.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.hisab.R
import com.example.hisab.features.admin.presentation.AdminScreen
import com.example.hisab.features.auth.presentation.LoginScreen
import com.example.hisab.features.auth.presentation.AccountRecoveryScreen
import com.example.hisab.features.auth.presentation.VerifyRecoveryEmailScreen
import com.example.hisab.features.settings.presentation.AboutHisabScreen
import com.example.hisab.features.settings.presentation.HisabFaqScreen
import com.example.hisab.features.privacy.presentation.PrivacyDataScreen
import com.example.hisab.features.privacy.presentation.PrivacyGate
import com.example.hisab.features.privacy.presentation.PrivacyPolicyScreen
import com.example.hisab.features.privacy.presentation.PrivacyReviewScreen
import com.example.hisab.features.privacy.presentation.PrivacyViewModel
import com.example.hisab.features.groups.presentation.create.CreateHisabScreen
import com.example.hisab.features.groups.presentation.dashboard.DashboardScreen
import com.example.hisab.features.profile.presentation.ProfileScreen
import com.example.hisab.features.settings.presentation.SettingsScreen
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.ui.components.FeedbackKind
import com.example.hisab.ui.components.HisabFeedbackHost
import com.example.hisab.ui.components.LocalHisabFeedback
import com.example.hisab.ui.components.rememberHisabFeedback
import com.example.hisab.features.auth.presentation.SessionViewModel
import com.example.hisab.util.ThemeMode
import com.example.hisab.util.ThemePreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

const val EXTRA_SKIP_SPLASH = "hisab.skipSplash"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        @Volatile var testSkipSplash: Boolean = false
    }
    
    @Inject
    lateinit var themePreferencesManager: ThemePreferencesManager

    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Hisab)

        setContent {
            val themeMode by themePreferencesManager.themeMode.collectAsState(initial = ThemeMode.LIGHT)
            val systemInDarkTheme = isSystemInDarkTheme()
            
            val isDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemInDarkTheme
            }
            
            HisabTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val sessionViewModel: SessionViewModel = hiltViewModel()
                    val privacyViewModel: PrivacyViewModel = hiltViewModel()
                    val startupViewModel: StartupViewModel = hiltViewModel()
                    val boot by startupViewModel.state.collectAsStateWithLifecycle()
                    val accountId by sessionViewModel.currentUserId.collectAsStateWithLifecycle()
                    val restoring by sessionViewModel.restoring.collectAsStateWithLifecycle()
                    val privacy by privacyViewModel.state.collectAsStateWithLifecycle()
                    val animationFinished = boot.animationFinished || testSkipSplash || intent.getBooleanExtra(EXTRA_SKIP_SPLASH, false)
                    val resolved = startupResolved(restoring, accountId, privacy.gate)
                    LaunchedEffect(animationFinished, resolved, accountId, privacy.gate) {
                        if (animationFinished && resolved && boot.destination == null) {
                            startupViewModel.enter(if (accountId == null) "login" else if (privacy.gate == PrivacyGate.Continue) "dashboard" else "session_entry")
                        }
                    }
                    if (boot.destination == null) {
                        SplashScreen(status = if (animationFinished) "Getting your account ready…" else null,
                            onFinished = startupViewModel::finishAnimation)
                    } else {
                        HisabNavHost(startDest = requireNotNull(boot.destination),
                            sessionViewModel = sessionViewModel, privacyViewModel = privacyViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun HisabNavHost(startDest: String, sessionViewModel: SessionViewModel = hiltViewModel(), privacyViewModel: PrivacyViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val isAdmin by sessionViewModel.isAdmin.collectAsState()
    val requiresSignIn by sessionViewModel.requiresSignIn.collectAsState()
    val reviewNeeded by sessionViewModel.privacyReviewNeeded.collectAsState()
    val privacyState by privacyViewModel.state.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    fun openLogin() {
        navController.navigate("login") {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(requiresSignIn) {
        if (requiresSignIn && navController.currentDestination?.route != "login") {
            openLogin()
        }
    }

    LaunchedEffect(route, reviewNeeded, privacyState.gate, requiresSignIn) {
        if (route == null || requiresSignIn) return@LaunchedEffect
        if ((reviewNeeded || privacyState.gate != PrivacyGate.Continue) && route !in privacyAccessibleRoutes) {
            navController.navigate("session_entry") {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        } else if (route == "session_entry" && !reviewNeeded && privacyState.gate == PrivacyGate.Continue) {
            navController.navigate("dashboard") { popUpTo("session_entry") { inclusive = true } }
        }
    }

    val feedback = rememberHisabFeedback()
    CompositionLocalProvider(LocalHisabFeedback provides feedback) {
    Box(Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        composable("login") {
            LoginScreen(
                sessionExpired = requiresSignIn,
                onForgotPassword = { navController.navigate("password_recovery") },
                onHelp = { navController.navigate("faq") },
                onPrivacyPolicy = { navController.navigate("privacy_policy") },
                onNavigateToDashboard = {
                    navController.navigate("session_entry") {
                        popUpTo("login") { inclusive = true }
                    }
                 }
            )
        }

        composable("session_entry") {
            val signOut = { privacyViewModel.signOut(); openLogin() }
            if (privacyState.gate == PrivacyGate.Review) {
                PrivacyReviewScreen(privacyViewModel, onAccount = { navController.navigate("profile") },
                    onData = { navController.navigate("privacy_data") }, onSignOut = signOut)
            } else {
                StartupStatusScreen(checking = privacyState.gate != PrivacyGate.Unavailable, message = privacyState.error,
                    onRetry = { sessionViewModel.checkAutoLogin(); privacyViewModel.checkStatus() },
                    onAccount = { navController.navigate("profile") }, onData = { navController.navigate("privacy_data") }, onSignOut = signOut)
            }
        }

        composable("privacy_policy") {
            PrivacyPolicyScreen(privacyViewModel, onBack = { navController.popBackStack() })
        }

        composable("privacy_data") {
            PrivacyDataScreen(onBack = { navController.popBackStack() },
                onPolicy = { navController.navigate("privacy_policy") })
        }

        composable("password_recovery") {
            AccountRecoveryScreen(onBack = { navController.popBackStack() }, onHelp = { navController.navigate("faq") })
        }

        composable("verify_recovery_email") {
            VerifyRecoveryEmailScreen(onBack = { navController.popBackStack() })
        }

        composable("dashboard") {
            DashboardScreen(
                isAdmin = isAdmin,
                onNavigateToProfile = {
                    navController.navigate("profile")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToNotifications = {
                    navController.navigate("notifications")
                },
                onNavigateToAdmin = {
                    navController.navigate("admin")
                },
                onCreateHisab = {
                    navController.navigate("create_hisab")
                },
                onGroupClick = { groupId ->
                    navController.navigate("group_detail/$groupId")
                }
            )
        }

        composable(
            route = "group_detail/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) {
            com.example.hisab.features.ledger.presentation.GroupDetailScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("notifications") {
            com.example.hisab.features.invitations.presentation.NotificationsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("create_hisab") {
            CreateHisabScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onGroupCreated = { groupId ->
                    feedback.post("Hisab created.", FeedbackKind.SUCCESS)
                    navController.popBackStack()
                    navController.navigate("group_detail/$groupId")
                }
            )
        }

        composable("admin") {
            AdminGuardedContent(isAdmin = isAdmin) {
                AdminScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable("profile") {
            ProfileScreen(
                onVerifyRecoveryEmail = { navController.navigate("verify_recovery_email") },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onLoggedOut = { openLogin() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onAbout = { navController.navigate("about") },
                onFaq = { navController.navigate("faq") },
                onPrivacy = { navController.navigate("privacy_data") },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("about") {
            AboutHisabScreen(onBack = { navController.popBackStack() }, onPolicy = { navController.navigate("privacy_policy") }, onOpenFaq = {
                if (!navController.popBackStack("faq", inclusive = false)) navController.navigate("faq")
            })
        }

        composable("faq") {
            HisabFaqScreen(onBack = { navController.popBackStack() }, onAbout = {
                if (!navController.popBackStack("about", inclusive = false)) navController.navigate("about")
            })
        }
    }
    HisabFeedbackHost(feedback.state, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding())
    }
    }
}

private val privacyAccessibleRoutes = setOf("splash", "login", "session_entry", "profile", "settings", "privacy_policy", "privacy_data",
    "password_recovery", "verify_recovery_email", "about", "faq")

@Composable
fun AdminGuardedContent(
    isAdmin: Boolean,
    content: @Composable () -> Unit
) {
    if (isAdmin) {
        content()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Access denied",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This section is reserved for administrators.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
