package org.dpdns.sebastianfiser.filetransferer.screens

import androidx.compose.material3.MaterialTheme
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
fun PlaceholderScreen(@StringRes titleRes: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.headlineMedium
        )
    }
}