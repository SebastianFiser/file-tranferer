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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.activity.viewModels

import org.dpdns.sebastianfiser.filetransferer.screens.MainViewModel
import org.dpdns.sebastianfiser.filetransferer.network.TransferClient
import org.dpdns.sebastianfiser.filetransferer.screens.MainScreen


class MainActivity : ComponentActivity() {

    private lateinit var transferClient: TransferClient
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        transferClient = TransferClient("${Build.MODEL}[$androidId]")

        setContent {
            MaterialTheme {
                MainScreen(transferClient)
            }
        }

        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri: Uri? = IntentCompat.getParcelableExtra(
                    intent, Intent.EXTRA_STREAM, Uri::class.java
                )
                listOfNotNull(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list: ArrayList<Uri>? = IntentCompat.getParcelableArrayListExtra(
                    intent, Intent.EXTRA_STREAM, Uri::class.java
                )
                list ?: emptyList()
            }
            else -> {
                emptyList()
            }
        }
        if (uris.isNotEmpty()) {
            viewModel.onSharedFiles(uris)
        }
    }

    // im hanging myself this year  1000% trust or this is going to end badly

    override fun onDestroy() {
        transferClient.close()
        super.onDestroy()
    }
}
