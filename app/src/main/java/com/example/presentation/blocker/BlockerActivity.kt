package com.example.presentation.blocker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.presentation.components.AppIcon
import com.example.presentation.components.UnlockAppDialog
import com.example.ui.theme.EarnTimeTheme

/**
 * Full-screen lock shown whenever a controlled app is opened without a paid access window.
 * It is also the fastest place to buy one, so the only way past it is to spend credits.
 */
class BlockerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        renderFromIntent(intent)
    }

    /** Launched with SINGLE_TOP, so repeat blocks arrive here rather than as new instances. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        renderFromIntent(intent)
    }

    private fun renderFromIntent(intent: Intent?) {
        val appName = intent?.getStringExtra(EXTRA_APP_NAME) ?: "This app"
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME)
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: REASON_LOCKED

        setContent {
            EarnTimeTheme {
                BlockerScreen(
                    appName = appName,
                    packageName = packageName,
                    reason = reason,
                    onUnlocked = { finishAndOpen(packageName) },
                    onGoHome = { goHome() }
                )
            }
        }
    }

    /** Hand the user back to the app they just paid for. */
    private fun finishAndOpen(packageName: String?) {
        val launchIntent = packageName?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back must not drop the user into the app behind the lock.
        goHome()
    }

    companion object {
        const val EXTRA_APP_NAME = "APP_NAME"
        const val EXTRA_PACKAGE_NAME = "PACKAGE_NAME"
        const val EXTRA_REASON = "REASON"

        /** Controlled app with no paid access window open. */
        const val REASON_LOCKED = "LOCKED"

        /** A paid window existed but its minutes ran out. */
        const val REASON_TIME_UP = "TIME_UP"

        /** Mode is ALWAYS_BLOCKED; credits cannot buy access. */
        const val REASON_ALWAYS_BLOCKED = "ALWAYS_BLOCKED"
    }
}

@Composable
private fun BlockerScreen(
    appName: String,
    packageName: String?,
    reason: String,
    onUnlocked: () -> Unit,
    onGoHome: () -> Unit,
    viewModel: BlockerViewModel = viewModel(factory = BlockerViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showUnlockDialog by remember { mutableStateOf(false) }

    val canBuyAccess = reason != BlockerActivity.REASON_ALWAYS_BLOCKED && packageName != null

    LaunchedEffect(uiState.unlockedMinutes) {
        if (uiState.unlockedMinutes != null) onUnlocked()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        packageName?.let {
            AppIcon(packageName = it, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(text = "🔒", style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "$appName is locked",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (reason) {
                BlockerActivity.REASON_ALWAYS_BLOCKED ->
                    "You set $appName to always blocked. Change its mode in EarnTime if you really need it."
                BlockerActivity.REASON_TIME_UP ->
                    "Your paid time on $appName has run out. Spend more credits to keep going."
                else ->
                    "$appName stays locked until you spend reward credits on it."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Balance: ${uiState.balance} credits",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        uiState.errorMessage?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (canBuyAccess) {
            Button(
                onClick = { showUnlockDialog = true },
                enabled = !uiState.isUnlocking,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (uiState.isUnlocking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Unlock with credits")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedButton(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) {
            Text("Go Home")
        }
    }

    if (showUnlockDialog && packageName != null) {
        UnlockAppDialog(
            appName = appName,
            balance = uiState.balance,
            isUnlocking = uiState.isUnlocking,
            errorMessage = uiState.errorMessage,
            onDismiss = {
                showUnlockDialog = false
                viewModel.dismissError()
            },
            onConfirm = { minutes -> viewModel.unlock(packageName, minutes) }
        )
    }
}
