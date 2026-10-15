package org.dpdns.sebastianfiser.filetransferer

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.dpdns.sebastianfiser.filetransferer.network.TransferClient
import org.dpdns.sebastianfiser.filetransferer.screens.MainScreen


class MainActivity : ComponentActivity() {

    private lateinit var transferClient: TransferClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        transferClient = TransferClient("${Build.MODEL}[$androidId]")

        setContent {
            MaterialTheme {
                MainScreen(transferClient)
            }
        }
    }

    // im hanging myself this year  1000% trust or this is going to end badly

    override fun onDestroy() {
        transferClient.close()
        super.onDestroy()
    }
}
