package com.example.hisab.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.hisab.R
import com.example.hisab.features.admin.presentation.AdminScreen
import com.example.hisab.features.auth.presentation.LoginScreen
import com.example.hisab.features.groups.presentation.create.CreateHisabScreen
import com.example.hisab.features.groups.presentation.dashboard.DashboardScreen
import com.example.hisab.features.profile.presentation.ProfileScreen
import com.example.hisab.features.settings.presentation.SettingsScreen
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.features.auth.presentation.SessionViewModel
import com.example.hisab.util.SPLASH_PREFS_NAME
import com.example.hisab.util.SPLASH_THEME_KEY
import com.example.hisab.util.ThemeMode
import com.example.hisab.util.ThemePreferencesManager
import com.example.hisab.util.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var themePreferencesManager: ThemePreferencesManager

    @Inject
    lateinit var tokenManager: TokenManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        val savedTheme = getSharedPreferences(SPLASH_PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getString(SPLASH_THEME_KEY, null)
        val systemIsDark = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val splashIsDark = when (savedTheme) {
            "dark"  -> true
            "light" -> false
            else    -> systemIsDark
        }
        setTheme(if (splashIsDark) R.style.Theme_Hisab_Splash_Dark else R.style.Theme_Hisab_Splash_Light)

        super.onCreate(savedInstanceState)
        installSplashScreen()
        
        setContent {
            val themeMode by themePreferencesManager.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
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
                    HisabNavHost(startDest = if (tokenManager.getUserId() != null) "dashboard" else "login")
                }
            }
        }
    }
}

@Composable
fun HisabNavHost(startDest: String) {
    val navController = rememberNavController()
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val isAdmin by sessionViewModel.isAdmin.collectAsState()
    val requiresSignIn by sessionViewModel.requiresSignIn.collectAsState()

    LaunchedEffect(requiresSignIn) {
        if (requiresSignIn && navController.currentDestination?.route != "login") {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        composable("login") {
            LoginScreen(
                sessionExpired = requiresSignIn,
                onNavigateToDashboard = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                 }
            )
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
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onLoggedOut = {
                    navController.navigate("login") {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

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
