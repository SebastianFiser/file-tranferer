package org.dpdns.sebastianfiser.filetransferer.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dpdns.sebastianfiser.filetransferer.R

@Composable
private fun BigButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(56.dp)
    ) {
        Text(text, fontSize = 18.sp)
    }
}

@Composable
fun StateContent(
    s:TransferUiState,
    onPick: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onSendMore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val title = when (s) {
            is TransferUiState.Idle -> stringResource(R.string.state_idle_title)
            is TransferUiState.Searching -> stringResource(R.string.state_searching_title)
            is TransferUiState.NotFound -> stringResource(R.string.state_notfound_title)
            is TransferUiState.Connecting -> stringResource(R.string.state_connecting_title)
            is TransferUiState.Connected -> stringResource(R.string.state_connected_title, s.deviceName)
            is TransferUiState.Transferring -> stringResource(R.string.state_transferring_title, s.fileIndex + 1, s.fileCount)
            is TransferUiState.Done -> stringResource(R.string.state_done_title)
            is TransferUiState.Error -> stringResource(s.message.messageRes())
            is TransferUiState.WaitingForConfirmation -> stringResource(R.string.state_waiting_for_confirmation_title)
            is TransferUiState.Rejected -> stringResource(R.string.state_rejected_title)
            is TransferUiState.Timeout -> stringResource(R.string.state_timeout_title)
            is TransferUiState.Cancelled -> stringResource(R.string.state_cancelled_title)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)

        when (s) {
            is TransferUiState.Searching -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.state_searching_hint), textAlign = TextAlign.Center)
            }
            is TransferUiState.NotFound -> {
                Text(stringResource(R.string.state_notfound_hint), textAlign = TextAlign.Center)
                BigButton(stringResource(R.string.action_retry), onRetry)
            }
            is TransferUiState.Connected -> BigButton(stringResource(R.string.action_pick_file), onPick)
            is TransferUiState.Transferring -> {
                val fraction = if (s.totalBytes > 0 ) s.bytesSent.toFloat() / s.totalBytes else 0f
                LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
                Text("${(fraction * 100).toInt()} %")
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
            is TransferUiState.Done -> {
                BigButton(stringResource(R.string.action_send_more), onSendMore)
            }
            is TransferUiState.Error -> {
                BigButton(stringResource(R.string.action_retry), onRetry)
            }
            is TransferUiState.WaitingForConfirmation -> {
                CircularProgressIndicator()
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
            is TransferUiState.Rejected -> {
                BigButton(stringResource(R.string.action_retry), onRetry)
            }
            is TransferUiState.Timeout -> {
                BigButton(stringResource(R.string.action_retry), onRetry)
            }
            is TransferUiState.Cancelled -> 
                BigButton(stringResource(R.string.action_send_more), onSendMore)
            else -> {}
        }
    }
}