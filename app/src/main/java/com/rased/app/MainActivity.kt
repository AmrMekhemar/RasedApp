package com.rased.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.rased.app.navigation.RasedApp

class MainActivity : ComponentActivity() {
    private var sharedUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedUri = sharedFileUri(intent)
        setContent { RasedApp(sharedUri) { sharedUri = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUri = sharedFileUri(intent)
    }

    private fun sharedFileUri(intent: Intent?): Uri? = intent?.takeIf {
        it.action == Intent.ACTION_SEND
    }?.let { it.getParcelableExtra(Intent.EXTRA_STREAM) ?: it.clipData?.getItemAt(0)?.uri }
}
