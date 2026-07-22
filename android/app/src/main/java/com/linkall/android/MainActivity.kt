package com.linkall.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.linkall.android.data.local.AppSettings
import com.linkall.android.ui.MainScreen
import com.linkall.android.ui.navigation.rememberNavigationState
import com.linkall.android.ui.theme.LinkAllTheme
import com.linkall.android.util.I18nHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * 单 Activity 入口：承载 Compose 三页签 UI
 * 启动时应用用户选择的语言
 */
class MainActivity : ComponentActivity() {

    private val settings: AppSettings by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 应用持久化的语言设置
        lifecycleScope.launch {
            val lang = settings.language.first()
            I18nHelper.applyLanguage(this@MainActivity, lang)
        }

        setContent {
            val lang by settings.language.collectAsState(initial = "system")
            androidx.compose.runtime.LaunchedEffect(lang) {
                I18nHelper.setLanguage(lang)
            }
            LinkAllTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val nav = rememberNavigationState()
                    MainScreen(nav)
                }
            }
        }
    }
}
