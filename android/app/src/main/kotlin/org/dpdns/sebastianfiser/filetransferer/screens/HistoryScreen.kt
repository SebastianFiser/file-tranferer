package org.dpdns.sebastianfiser.filetransferer.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp


import org.dpdns.sebastianfiser.filetransferer.R
import org.dpdns.sebastianfiser.filetransferer.network.HistoryEntry
import org.dpdns.sebastianfiser.filetransferer.protocol.formatBytes
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(entries: List<HistoryEntry>, onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 64.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.nav_history),
                style = MaterialTheme.typography.headlineMedium
            )
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.history_clear))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (entries.isEmpty()) {
            Text(stringResource(R.string.history_empty))
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries) { HistoryItem(it)}
            }
        }
    }
}

@Composable
private fun HistoryItem(e: HistoryEntry) {
    val date = remember(e.timestamp) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(e.timestamp))
    }
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(e.serverName, style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.history_files, e.fileCount, e.fileCount) +
                    " . " + formatBytes(e.totalBytes),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(date, style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(
                    if (e.verified) R.string.history_verified else R.string.history_mismatch
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (e.verified) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )

        }
    }
}