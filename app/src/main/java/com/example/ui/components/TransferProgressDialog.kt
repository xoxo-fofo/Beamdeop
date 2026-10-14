package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.TransferDirection
import com.example.model.TransferProgress
import com.example.model.TransferStatus
import com.example.ui.theme.BeamAmber
import com.example.ui.theme.BeamCyan
import com.example.ui.theme.BeamGreen
import com.example.ui.theme.BeamRose
import com.example.util.FileUtils

@Composable
fun TransferProgressDialog(
    progress: TransferProgress,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    if (progress.status == TransferStatus.IDLE) return

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Dialog(
        onDismissRequest = {
            if (progress.status == TransferStatus.COMPLETED || progress.status == TransferStatus.FAILED || progress.status == TransferStatus.CANCELLED) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = progress.status != TransferStatus.TRANSFERRING,
            dismissOnClickOutside = progress.status != TransferStatus.TRANSFERRING
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("transfer_progress_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row with Icon & Peer Name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    when (progress.status) {
                                        TransferStatus.COMPLETED -> BeamGreen.copy(alpha = 0.2f)
                                        TransferStatus.FAILED -> BeamRose.copy(alpha = 0.2f)
                                        TransferStatus.CANCELLED -> BeamAmber.copy(alpha = 0.2f)
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (progress.status) {
                                    TransferStatus.COMPLETED -> Icons.Default.CheckCircle
                                    TransferStatus.FAILED -> Icons.Default.Error
                                    TransferStatus.CANCELLED -> Icons.Default.Cancel
                                    else -> if (progress.direction == TransferDirection.SENT) Icons.Default.Upload else Icons.Default.Download
                                },
                                contentDescription = null,
                                tint = when (progress.status) {
                                    TransferStatus.COMPLETED -> BeamGreen
                                    TransferStatus.FAILED -> BeamRose
                                    TransferStatus.CANCELLED -> BeamAmber
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = when (progress.status) {
                                    TransferStatus.CONNECTING -> "Connecting..."
                                    TransferStatus.TRANSFERRING -> if (progress.direction == TransferDirection.SENT) "Sending Files" else "Receiving Files"
                                    TransferStatus.COMPLETED -> "Transfer Complete!"
                                    TransferStatus.FAILED -> "Transfer Failed"
                                    TransferStatus.CANCELLED -> "Transfer Cancelled"
                                    else -> "P2P Beam"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (progress.peerName.isNotBlank()) {
                                Text(
                                    text = "Peer: ${progress.peerName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (progress.status == TransferStatus.COMPLETED || progress.status == TransferStatus.FAILED || progress.status == TransferStatus.CANCELLED) {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_transfer_dialog")) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Current File & Counter
                if (progress.currentFileName.isNotBlank()) {
                    Text(
                        text = progress.currentFileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "File ${progress.currentFileIndex} of ${progress.totalFiles}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (progress.status == TransferStatus.CONNECTING) {
                    Text(
                        text = "Establishing high-speed P2P socket...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = when (progress.status) {
                        TransferStatus.COMPLETED -> BeamGreen
                        TransferStatus.FAILED -> BeamRose
                        TransferStatus.CANCELLED -> BeamAmber
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Info Row (Percentage & Bytes)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${progress.progressPercent}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${FileUtils.formatFileSize(progress.bytesTransferred)} / ${FileUtils.formatFileSize(progress.totalBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live Speed & ETA Badges (During transfer)
                if (progress.status == TransferStatus.TRANSFERRING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Speed card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = BeamCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = FileUtils.formatSpeed(progress.speedBytesPerSec),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Speed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // ETA card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = BeamAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = FileUtils.formatDuration(progress.etaSeconds),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Est. Time",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Error message display
                if (!progress.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = progress.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = BeamRose
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                if (progress.status == TransferStatus.TRANSFERRING || progress.status == TransferStatus.CONNECTING) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cancel_transfer_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BeamRose
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel Transfer")
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("done_transfer_button")
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
