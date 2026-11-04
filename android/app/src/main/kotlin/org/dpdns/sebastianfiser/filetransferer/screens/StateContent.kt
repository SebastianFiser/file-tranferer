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
import org.dpdns.sebastianfiser.filetransferer.protocol.formatBytes
import org.dpdns.sebastianfiser.filetransferer.protocol.formatEta
import org.dpdns.sebastianfiser.filetransferer.network.VerifyResult
import org.dpdns.sebastianfiser.filetransferer.ui.theme.VerifiedGreen
import org.dpdns.sebastianfiser.filetransferer.ui.theme.WarningAmber


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
    method: SendMethod,
    onPick: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onSendMore: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val title = when (s) {
            is TransferUiState.Done -> 
                if (s.verify == VerifyResult.Verified) stringResource(R.string.state_done_title)
                else stringResource(R.string.state_mismatch_title)
            is TransferUiState.Idle -> stringResource(R.string.state_idle_title)
            is TransferUiState.Searching -> stringResource(R.string.state_searching_title)
            is TransferUiState.NotFound -> stringResource(R.string.state_notfound_title)
            is TransferUiState.Connecting -> stringResource(R.string.state_connecting_title)
            is TransferUiState.Connected -> stringResource(R.string.state_connected_title, s.deviceName)
            is TransferUiState.Transferring -> stringResource(R.string.state_transferring_title, s.fileIndex + 1, s.fileCount)
            is TransferUiState.Error -> stringResource(s.message.messageRes())
            is TransferUiState.WaitingForConfirmation -> stringResource(R.string.state_waiting_for_confirmation_title)
            is TransferUiState.Rejected -> stringResource(R.string.state_rejected_title)
            is TransferUiState.Timeout -> stringResource(R.string.state_timeout_title)
            is TransferUiState.Cancelled -> stringResource(R.string.state_cancelled_title)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)

        when (s) {
            is TransferUiState.Searching -> {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    stringResource(
                        if (method == SendMethod.Cable) R.string.state_searching_hint_cable
                        else R.string.state_searching_hint_wifi
                    ),
                    textAlign = TextAlign.Center
                )
            }
            is TransferUiState.NotFound -> {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.state_notfound_tip_server), textAlign = TextAlign.Center)
                Text(
                    stringResource(
                        if (method == SendMethod.Cable) R.string.state_notfound_tip_cable
                        else R.string.state_notfound_tip_wifi
                    ),
                    textAlign = TextAlign.Center
                )
                Text(stringResource(R.string.state_notfound_tip_firewall), textAlign = TextAlign.Center)
                BigButton(stringResource(R.string.action_retry), onRetry)
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.action_back))
                }
            }
            is TransferUiState.Connected -> BigButton(stringResource(R.string.action_pick_file), onPick)
            is TransferUiState.Transferring -> {
                val fraction = if (s.totalBytes > 0 ) s.bytesSent.toFloat() / s.totalBytes else 0f
                LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
                Text("${(fraction * 100).toInt()} %")
                Text("${formatBytes(s.bytesSent)} / ${formatBytes(s.totalBytes)}")
                if (s.speedBps > 0) {
                    val eta = if (s.etaSeconds >= 0)
                        " . " + stringResource(R.string.transfer_eta, formatEta(s.etaSeconds)) else ""
                    Text(stringResource(R.string.transfer_speed, formatBytes(s.speedBps)) + eta)
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
            is TransferUiState.Done -> {
                val ok = s.verify == VerifyResult.Verified
                Text(
                    text = if (ok) "sucessly transfered" else "something went wrong",
                    fontSize = 48.sp,
                    color = if (ok) VerifiedGreen else WarningAmber
                )
                Text(stringResource(R.string.transfer_done_summary, s.fileCount, formatBytes(s.totalBytes)))
                Text(
                    text = stringResource(
                        if (ok) R.string.state_verified_hint else R.string.state_mismatch_hint
                    ),
                    textAlign = TextAlign.Center
                )
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