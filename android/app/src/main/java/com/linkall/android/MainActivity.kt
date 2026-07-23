package com.linkall.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.linkall.android.data.local.AppSettings
import com.linkall.android.data.local.SecureStorage
import com.linkall.android.ui.MainScreen
import com.linkall.android.ui.components.LoadingView
import com.linkall.android.ui.navigation.rememberNavigationState
import com.linkall.android.ui.screen.onboarding.OnboardingScreen
import com.linkall.android.ui.screen.onboarding.OnboardingViewModel
import com.linkall.android.ui.theme.LinkAllTheme
import com.linkall.android.util.I18nHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

/**
 * 单 Activity 入口：承载 Compose 三页签 UI
 * 启动时应用用户选择的语言
 *
 * 首次启动时根据引导完成状态与登录状态决定显示 OnboardingScreen 还是主界面：
 * - 引导未完成 → 完整四步引导
 * - 引导已完成但未登录 → 仅显示登录步骤
 * - 引导已完成且已登录 → 直接进入主界面
 */
class MainActivity : ComponentActivity() {

    private val settings: AppSettings by inject()
    private val storage: SecureStorage by inject()

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
                    // 使用 null 作为初始值，等待 DataStore 异步加载真实值
                    val onboardingCompleted by settings.onboardingCompleted.collectAsState(initial = null as Boolean?)
                    // 登录状态：直接读取 SecureStorage（同步）
                    var loggedIn by remember { mutableStateOf(storage.getToken() != null) }

                    when (onboardingCompleted) {
                        // 等待引导状态加载完成，避免闪烁
                        null -> LoadingView(Modifier.fillMaxSize())
                        // 已完成引导且已登录：直接进入主界面
                        true -> if (loggedIn) {
                            val nav = rememberNavigationState()
                            MainScreen(nav)
                        } else {
                            // 已完成引导但未登录：仅显示登录步骤
                            val vm: OnboardingViewModel = koinViewModel()
                            OnboardingScreen(
                                vm = vm,
                                alreadyCompleted = true,
                                onComplete = { loggedIn = true }
                            )
                        }
                        // 引导未完成：完整四步流程
                        false -> {
                            val vm: OnboardingViewModel = koinViewModel()
                            OnboardingScreen(
                                vm = vm,
                                alreadyCompleted = false,
                                onComplete = { loggedIn = true }
                            )
                        }
                    }
                }
            }
        }
    }
}
