package com.example.moducommerce

import android.app.Application
import com.example.moducommerce.core.push.PushNotifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CommerceApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 푸시 채널은 알림이 오기 전에 있어야 한다(백그라운드 FCM 알림도 이 채널로 온다).
        PushNotifications.ensureChannels(this)
    }
}
