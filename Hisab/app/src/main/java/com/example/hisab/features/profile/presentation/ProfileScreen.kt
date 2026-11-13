package com.example.hisab.features.profile.presentation

import com.example.hisab.util.Constants

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import com.example.hisab.ui.components.LoadFailure
import com.example.hisab.ui.components.ActionLabel
import com.example.hisab.ui.components.AlertKind
import com.example.hisab.ui.components.HisabAlert
import com.example.hisab.ui.components.ProfileSkeleton
import com.example.hisab.ui.components.RefreshFailure
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hisab.features.profile.presentation.ProfileViewModel

@Composable
fun ThemeToggleSwitch(
    isDarkMode: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val offsetX by animateFloatAsState(
        targetValue = if (isDarkMode) 32f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "toggleOffset"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isDarkMode) {
            Color(0xFF1E3A5F)
        } else {
            Color(0xFFFFE082)
        },
        animationSpec = tween(durationMillis = 300),
        label = "backgroundColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Box(
            modifier = Modifier
                .width(80.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(backgroundColor)
                .clickable { onToggle(!isDarkMode) }
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LightMode,
                    contentDescription = "Light Mode",
                    tint = if (!isDarkMode) Color(0xFFFFA726) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .padding(start = 4.dp)
                )
                Icon(
                    imageVector = Icons.Default.DarkMode,
                    contentDescription = "Dark Mode",
                    tint = if (isDarkMode) Color(0xFF90CAF9) else Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .offset(x = offsetX.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = null,
                    tint = if (isDarkMode) Color(0xFF1E3A5F) else Color(0xFFFFA726),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onLoggedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbars = remember { SnackbarHostState() }
    val message = uiState.error ?: uiState.successMessage
    LaunchedEffect(message) {
        if (message != null) {
            snackbars.showSnackbar(message)
            viewModel.clearMessages()
        }
    }
    var showLogoutDialog by remember { mutableStateOf(false) }

    
    val cropImageLauncher = rememberLauncherForActivityResult(com.canhub.cropper.CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent
            uriContent?.let(viewModel::uploadProfilePicture)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val options = com.canhub.cropper.CropImageContractOptions(it, com.canhub.cropper.CropImageOptions(
                fixAspectRatio = true,
                aspectRatioX = 1,
                aspectRatioY = 1,
                cropShape = com.canhub.cropper.CropImageView.CropShape.OVAL
            ))
            cropImageLauncher.launch(options)
        }
    }

    if (showLogoutDialog) {
        HisabAlert(
            kind = AlertKind.CONFIRM,
            title = "Log out?",
            message = "You’ll need to sign in again to open your Hisabs.",
            onDismiss = { showLogoutDialog = false },
            confirmLabel = "Log out",
            dismissLabel = "Cancel",
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout()
                onLoggedOut()
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profile",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        val failure = uiState.loadFailure
        if (failure != null && uiState.email.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadFailure("Couldn't load your profile", failure, viewModel::loadUserProfile,
                    retryEnabled = !uiState.isLoading)
            }
            return@Scaffold
        }
        if (uiState.isLoading && uiState.email.isEmpty()) {
            ProfileSkeleton(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (failure != null) RefreshFailure(failure, viewModel::loadUserProfile, !uiState.isLoading)
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.padding(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !uiState.isUploading && !uiState.isSaving) {
                            imagePickerLauncher.launch("image/*")
                        },
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    AsyncImage(
                        model = Constants.profileImageUrl(uiState.profilePicUrl),
                        contentDescription = "Profile Picture",
                        placeholder = androidx.compose.ui.res.painterResource(id = com.example.hisab.R.drawable.ic_default_google_avatar),
                        error = androidx.compose.ui.res.painterResource(id = com.example.hisab.R.drawable.ic_default_google_avatar),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !uiState.isUploading && !uiState.isSaving) {
                            imagePickerLauncher.launch("image/*")
                        },
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile Picture",
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = uiState.name,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "ID: ${uiState.uniqueId}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = uiState.email,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (uiState.isUploading) {
                ActionLabel("Profile picture", "Uploading picture…", busy = true)
            }


            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                ThemeToggleSwitch(
                    isDarkMode = uiState.isDarkMode,
                    onToggle = { isDark ->
                        viewModel.toggleTheme(isDark)
                    },
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.updateProfile() },
                enabled = !uiState.isLoading && !uiState.isSaving && !uiState.isUploading && uiState.email.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                ActionLabel("Save Profile", "Saving profile…", uiState.isSaving)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("App Settings & Updates", fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Log Out", fontWeight = FontWeight.Medium)
            }
        }
    }
}
