package org.dpdns.sebastianfiser.filetransferer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            //viewModel in the future
            // Theme in the future
            StartContent()
        }
    }
}

@Composable
fun StartContent() {
    Text(text = "Hello world")
}
