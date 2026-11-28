package com.example.hisab.features.settings.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.hisab.BuildConfig
import com.example.hisab.ui.components.DynamicLogo

private const val SUPPORT_URL = "https://github.com/Ahmad-Hassan-421/Hisab/issues"

@Composable
fun AboutHisabScreen(onBack: () -> Unit, onOpenFaq: () -> Unit, onPolicy: () -> Unit) {
    InformationScreen("About Hisab", onBack) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DynamicLogo(size = 84.dp)
                Text("Hisab", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text("Created by Hantalyze", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Hisab helps groups keep track of shared expenses, debts, and repayments in Pakistani rupees. Each Hisab holds its own members, balances, and history so everyone can check what they owe and what they’re owed.")
        OutlinedButton(onClick = onOpenFaq, modifier = Modifier.fillMaxWidth()) { Text("Frequently asked questions") }
        OutlinedButton(onClick = onPolicy, modifier = Modifier.fillMaxWidth()) { Text("Privacy Policy") }
        SupportCard()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HisabFaqScreen(onBack: () -> Unit, onAbout: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<Int?>(null) }
    InformationScreen("Frequently asked questions", onBack) {
        hisabQuestions.forEachIndexed { index, question ->
            val isExpanded = expanded == index
            OutlinedCard(onClick = { expanded = index.takeUnless { isExpanded } }, modifier = Modifier.fillMaxWidth().semantics {
                stateDescription = if (isExpanded) "Expanded" else "Collapsed"
            }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(question.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
                AnimatedVisibility(visible = isExpanded) {
                    Text(question.answer, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        SupportCard()
        TextButton(onClick = onAbout) { Text("About Hisab") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InformationScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title, Modifier.semantics { heading() }) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SupportCard() {
    val links = LocalUriHandler.current
    var failed by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Support", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("Use GitHub Issues for bug reports and general questions. It’s public, so keep passwords, recovery codes, and ledger details private.")
            TextButton(onClick = {
                failed = runCatching { links.openUri(SUPPORT_URL) }.isFailure
            }) { Text("Open Hisab Issues on GitHub") }
            if (failed) Text("Couldn’t open GitHub. You can find Hisab Issues from the project repository.",
                color = MaterialTheme.colorScheme.error)
        }
    }
}

private data class HelpQuestion(val title: String, val answer: String)

private val hisabQuestions = listOf(
    HelpQuestion("What is a Hisab?", "A Hisab is a shared ledger. It records expenses, debts, and repayments for one group and shows its members’ balances in PKR."),
    HelpQuestion("How do I create a Hisab?", "On Home, choose Create Hisab, give it a name, and save it. You can invite members after it opens."),
    HelpQuestion("How do invitations work?", "Open a Hisab and choose Invite member. Enter the other person’s Hisab ID, which they can find in Profile. They can accept or decline in Notifications."),
    HelpQuestion("How do I record an expense or debt?", "Use Add expense to share an expense or assign it to one member. Use Record debt when you owe another member money."),
    HelpQuestion("How do I record money I paid or received?", "Open the Hisab and choose Record a payment. Select I paid someone or Someone paid me, choose the member, and enter the amount. The app limits the amount to the balance owed."),
    HelpQuestion("Can I edit a saved entry?", "You can review an entry in the member’s details. Editing saved entries is currently unavailable."),
    HelpQuestion("How do I reset my password?", "Choose Forgot password? on the login screen. If you verified your recovery email, follow the instructions in the message and enter the code in Hisab. To set up recovery, choose Verify recovery email from your Profile while signed in."),
    HelpQuestion("How do I change my picture?", "Open Profile, tap the edit icon on your picture, choose a photo, and crop it. Hisab saves your selection on this device. You can also reset the picture from Profile."),
    HelpQuestion("How do I recover a deleted Hisab?", "Open Settings, then Privacy and data, and choose Retrieve a Copy of My Data. Unexpired recovery sources are available for two calendar months. A submitted request normally delivers a read-only copy within 3–5 minutes. Delivered copies remain until you delete them or your account."),
    HelpQuestion("What happens when I delete my account?", "In Privacy and data, select your signed-in account and confirm with its password. Your private data and copies are permanently removed. Other members keep their shared financial records with your identity anonymized."),
    HelpQuestion("How does Hisab update?", "Hisab checks for new releases after startup. You can also open Settings and select Check for updates, or tap a version notification. Review the release information, choose Update now, then approve installation in Android. Optional updates can be postponed. Your existing data stays in place."),
    HelpQuestion("What happens when I go offline?", "Hisab may show records that were loaded earlier. An internet connection is required to create or change records and to refresh balances and invitations."),
    HelpQuestion("Who can see shared records?", "Members of a Hisab can see shared ledger entries inside that Hisab. Protect your login and only invite people you want in the group.")
)
