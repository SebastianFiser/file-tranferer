package org.dpdns.sebastianfiser.filetransferer.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.dpdns.sebastianfiser.filetransferer.R

@Composable
fun HelpScreen() {
    val steps = listOf(
        R.string.help_step_1,
        R.string.help_step_2,
        R.string.help_step_3,
        R.string.help_step_4
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.nav_help),
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            stringResource(R.string.help_how_title),
            style = MaterialTheme.typography.titleMedium
        )
        steps.forEachIndexed { i, res ->
            Text("${i + 1}. " + stringResource(res))
        }

        Text(
            stringResource(R.string.help_trouble_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(stringResource(R.string.help_trouble_body))
    }
}
