package com.selfguard.ui.blocked

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class BlockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        val kind = intent?.getStringExtra(EXTRA_RULE_KIND).orEmpty()
        val label = intent?.getStringExtra(EXTRA_RULE_LABEL).orEmpty()
        val match = intent?.getStringExtra(EXTRA_RULE_MATCH).orEmpty()
        val host = intent?.getStringExtra(EXTRA_RULE_HOST)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goHome()
                }
            },
        )

        setContent {
            SelfGuardBlockedTheme {
                BlockedScreen(
                    kind = kind,
                    label = label,
                    match = match,
                    host = host,
                    onHome = { goHome() },
                )
            }
        }
    }

    private fun goHome() {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_RULE_KIND = "rule_kind"
        const val EXTRA_RULE_LABEL = "rule_label"
        const val EXTRA_RULE_MATCH = "rule_match"
        const val EXTRA_RULE_HOST = "rule_host"
    }
}

@Composable
private fun SelfGuardBlockedTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun BlockedScreen(
    kind: String,
    label: String,
    match: String,
    host: String?,
    onHome: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF10130F)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                tint = Color(0xFF8CD68F),
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = "Blocked",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )
            if (label.isNotBlank()) {
                Text(
                    text = when (kind) {
                        "DOMAIN" -> "$label is on your blocklist"
                        "APP" -> "$label is on your app blocklist"
                        "SHIELD" -> label
                        else -> "Keyword \"$match\" matched"
                    },
                    color = Color(0xFFC8D6C4),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (!host.isNullOrBlank() && host != label) {
                Text(
                    text = host,
                    color = Color(0xFF8CD68F),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onHome,
                modifier = Modifier.height(52.dp),
            ) {
                Icon(Icons.Default.Home, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Go back to the home screen")
            }
            Text(
                text = "This attempt is recorded.",
                color = Color(0xFF6E7A69),
                fontSize = 12.sp,
            )
        }
    }
}
