package com.linkall.android.di

import com.linkall.android.data.api.LinkAllApi
import com.linkall.android.data.api.NetworkConfig
import com.linkall.android.data.local.AppSettings
import com.linkall.android.data.local.SecureStorage
import com.linkall.android.data.repo.AnnouncementRepository
import com.linkall.android.data.repo.AuthRepository
import com.linkall.android.data.repo.DeviceRepository
import com.linkall.android.data.repo.InviteRepository
import com.linkall.android.data.repo.OtaRepository
import com.linkall.android.data.repo.SecurityRepository
import com.linkall.android.data.repo.ServerRepository
import com.linkall.android.data.signaling.SignalingClient
import com.linkall.android.ui.screen.controlled.ControlledViewModel
import com.linkall.android.ui.screen.controller.ControllerViewModel
import com.linkall.android.ui.screen.manage.ManageViewModel
import com.linkall.android.webrtc.ControlMessageBuilder
import com.linkall.android.webrtc.WebRtcInitializer
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin 依赖注入模块
 */
val appModule = module {
    single { SecureStorage(androidContext()) }
    single { AppSettings(androidContext()) }

    // 网络层：使用默认服务器，自定义服务器在运行时覆盖
    single<OkHttpClient> {
        NetworkConfig.createOkHttpClient { get<SecureStorage>().getToken() }
    }
    single<LinkAllApi> {
        NetworkConfig.createRetrofit(NetworkConfig.DEFAULT_SERVER, get())
            .create(LinkAllApi::class.java)
    }
    single { SignalingClient(NetworkConfig.json) }

    // Repository
    single { AuthRepository(get(), get()) }
    single { DeviceRepository(get(), get()) }
    single { AnnouncementRepository(get(), get()) }
    single { InviteRepository(get(), get()) }
    single { SecurityRepository(get(), get(), get()) }
    single { ServerRepository(get(), get()) }
    single { OtaRepository(get(), get()) }

    // WebRTC
    single { WebRtcInitializer }
    single { ControlMessageBuilder(NetworkConfig.json) }

    // ViewModel
    viewModel { ControlledViewModel(get(), get()) }
    viewModel { ControllerViewModel(get(), get()) }
    viewModel {
        ManageViewModel(
            authRepo = get(),
            deviceRepo = get(),
            announceRepo = get(),
            inviteRepo = get(),
            securityRepo = get(),
            serverRepo = get(),
            otaRepo = get(),
            settings = get(),
            storage = get()
        )
    }
}
