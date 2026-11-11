package com.actionanand.vizhitivi

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.actionanand.vizhitivi.core.LiveViewModel
import com.actionanand.vizhitivi.ui.LiveApp
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: LiveViewModel = viewModel()
            val state = vm.state.collectAsStateWithLifecycle().value
            val config = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(state.preferences.locale)) }
            val localized = remember(state.preferences.locale) { createConfigurationContext(config) }
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config) {
                LiveApp(vm, state, BuildConfig.FLAVOR == "tv")
            }
        }
    }
}
