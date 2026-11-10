package com.actionanand.vizhitivi

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uiMode = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val isTv = uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StarterScreen(isTv = isTv)
                }
            }
        }
    }
}

@Composable
private fun StarterScreen(isTv: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(if (isTv) 48.dp else 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(text = stringResource(R.string.tagline), textAlign = TextAlign.Center)
        Text(
            text = stringResource(if (isTv) R.string.tv_starter else R.string.mobile_starter),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp)
        )
    }
}
