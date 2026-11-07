package org.dpdns.sebastianfiser.filetransferer.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import org.dpdns.sebastianfiser.filetransferer.R

@Composable
fun SettingsScreen(onForgetServer: () -> Unit ) {
    val context = LocalContext.current
    var forgotten by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 64.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedButton(
            onClick = {
                onForgetServer()
                forgotten = true
            },
            enabled = !forgotten
        ) {
            Text(
                stringResource(
                    if (forgotten) R.string.settings_forgotten
                    else R.string.settings_forget_server
                )
            )
        }
        Text(
            stringResource(R.string.settings_forget_server_hint),
            style = MaterialTheme.typography.bodySmall
        )

        if (Build.VERSION.SDK_INT >= 33) {
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APP_LOCALE_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                )
            }) {
                Text(stringResource(R.string.settings_language))
            }
        }

    }
}