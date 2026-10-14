package org.dpdns.sebastianfiser.filetransferer.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import acdroidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

import org.dpdns.sebastianfiser.filetransferer.files.PickedFile
import org.dpdns.sebastianfiser.filetransferer.files.queryFile

@Composable
fun Mainscreen() {
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDcuments()
    ) {
        uris: List<Uri> ->
        uris.forEach {
            val picked = queryFile(context.contentResolver, uri)
            Log.d("Mainscreen", "picked: $picked")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = ALignment.CenterHorizontally
    ) {
        Button(onClick = { picker.launch(arrayOf("*/*")) }) {
            Text("select files")
        }
    }
}
