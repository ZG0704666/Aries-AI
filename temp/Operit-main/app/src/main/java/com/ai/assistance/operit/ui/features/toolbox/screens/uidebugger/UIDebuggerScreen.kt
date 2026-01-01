package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.services.UIDebuggerService
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun UIDebuggerScreen(
    navController: NavController,
    viewModel: UIDebuggerViewModel = UIDebuggerViewModel.getInstance()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()





    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    CustomScaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (Settings.canDrawOverlays(context)) {
                        val intent = Intent(context, UIDebuggerService::class.java)
                        context.startService(intent)
                    } else {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
            ) {
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = "启动悬浮窗"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "UI调试工具",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "点击右下角的按钮启动悬浮窗进行UI调试",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}







 