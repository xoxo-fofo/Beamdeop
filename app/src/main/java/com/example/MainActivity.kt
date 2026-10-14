package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.TransferStatus
import com.example.ui.components.TransferProgressDialog
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ReceiveScreen
import com.example.ui.screens.SendScreen
import com.example.ui.theme.BeamCyan
import com.example.ui.theme.BeamGreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingShareIntent(intent)

        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } ?: intent.data

            if (uri != null) {
                viewModel.addSelectedUris(listOf(uri))
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action && type != null) {
            val uris: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (!uris.isNullOrEmpty()) {
                viewModel.addSelectedUris(uris)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val discoveredPeers by viewModel.discoveredPeers.collectAsStateWithLifecycle()
    val isDiscoveringPeers by viewModel.isDiscoveringPeers.collectAsStateWithLifecycle()
    val localIpAddress by viewModel.localIpAddress.collectAsStateWithLifecycle()
    val wifiSsid by viewModel.wifiSsid.collectAsStateWithLifecycle()
    val qrPayloadString by viewModel.qrPayloadString.collectAsStateWithLifecycle()
    val serverRunning by viewModel.serverRunning.collectAsStateWithLifecycle()
    val historyTransfers by viewModel.historyTransfers.collectAsStateWithLifecycle()

    val sendProgress by viewModel.sendProgress.collectAsStateWithLifecycle()
    val receiveProgress by viewModel.receiveProgress.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(BeamCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "BeamDrop",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    if (localIpAddress != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(BeamGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = localIpAddress!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                // Send Tab
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        if (selectedFiles.isNotEmpty()) {
                            BadgedBox(
                                badge = {
                                    Badge { Text(selectedFiles.size.toString()) }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                            }
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    },
                    label = { Text("Send") },
                    modifier = Modifier.testTag("nav_send_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                // Receive Tab
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        if (serverRunning) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = BeamGreen)
                                }
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = "Receive")
                            }
                        } else {
                            Icon(Icons.Default.QrCode2, contentDescription = "Receive")
                        }
                    },
                    label = { Text("Receive") },
                    modifier = Modifier.testTag("nav_receive_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                // History / Transfers Tab
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(Icons.Default.Folder, contentDescription = "Transfers")
                    },
                    label = { Text("Transfers") },
                    modifier = Modifier.testTag("nav_history_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
            ) {
                when (selectedTab) {
                    0 -> SendScreen(
                        viewModel = viewModel,
                        selectedFiles = selectedFiles,
                        discoveredPeers = discoveredPeers,
                        isDiscoveringPeers = isDiscoveringPeers,
                        localIpAddress = localIpAddress,
                        wifiSsid = wifiSsid
                    )
                    1 -> ReceiveScreen(
                        viewModel = viewModel,
                        serverRunning = serverRunning,
                        localIpAddress = localIpAddress,
                        wifiSsid = wifiSsid,
                        qrPayloadString = qrPayloadString
                    )
                    2 -> HistoryScreen(
                        viewModel = viewModel,
                        transfers = historyTransfers
                    )
                }
            }
        }
    }

    // Sender Progress Dialog
    if (sendProgress.status != TransferStatus.IDLE) {
        TransferProgressDialog(
            progress = sendProgress,
            onCancel = { viewModel.cancelActiveSend() },
            onDismiss = { viewModel.resetSendProgress() }
        )
    }

    // Receiver Progress Dialog
    if (receiveProgress.status != TransferStatus.IDLE) {
        TransferProgressDialog(
            progress = receiveProgress,
            onCancel = { viewModel.cancelActiveReceive() },
            onDismiss = { viewModel.resetReceiveProgress() }
        )
    }
}
