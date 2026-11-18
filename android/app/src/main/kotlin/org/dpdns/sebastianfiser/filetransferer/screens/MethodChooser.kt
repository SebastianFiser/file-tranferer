package org.dpdns.sebastianfiser.filetransferer.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import org.dpdns.sebastianfiser.filetransferer.R

enum class SendMethod { Wifi, Cable }

@Composable
fun MethodChooser(
    onChoose: (SendMethod) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.choose_method_title),
            style = MaterialTheme.typography.headlineMedium
        ) 
        Spacer(modifier = Modifier.height(32.dp))
        MethodCard(
            title = stringResource(R.string.method_wifi),
            description = stringResource(R.string.method_wifi_desc),
            onClick = { onChoose(SendMethod.Wifi) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        MethodCard(
            title = stringResource(R.string.method_cable),
            description = stringResource(R.string.method_cable_desc),
            onClick = { onChoose(SendMethod.Cable) }
        )

    }
}

@Composable
private fun MethodCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                title, style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                description, style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
