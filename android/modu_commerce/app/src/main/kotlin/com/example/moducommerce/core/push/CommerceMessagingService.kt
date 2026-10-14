package com.example.moducommerce.core.push

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named

/**
 * FCM 수신. 앱이 앞에 있을 때만 [onMessageReceived] 가 불린다(notification 메시지). 백그라운드면 시스템이 알림을 띄우고,
 * 누르면 data(path, campaignId)가 런처 액티비티 인텐트 엑스트라로 온다.
 */
@AndroidEntryPoint
class CommerceMessagingService : FirebaseMessagingService() {

    @Inject lateinit var registrar: PushRegistrar

    @Inject @Named("plain") lateinit var httpClient: OkHttpClient

    override fun onNewToken(token: String) {
        registrar.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: return
        val body = message.notification?.body ?: data["body"].orEmpty()
        val imageUrl = message.notification?.imageUrl?.toString() ?: data["imageUrl"]
        val link = PushLink.from(data[PushLink.EXTRA_PATH], data[PushLink.EXTRA_CAMPAIGN_ID])
        // 이 콜백은 백그라운드 스레드이고 몇 초의 여유가 있다. 사진을 못 받으면 글자만 띄운다.
        val image = imageUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let(::downloadBitmap)
        PushNotifications.show(this, title, body, image, link)
    }

    private fun downloadBitmap(url: String): Bitmap? = try {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) return null
            val bytes = res.body?.bytes() ?: return null
            decodeSampled(bytes)
        }
    } catch (e: Exception) {
        Log.w(TAG, "image download failed: ${e.javaClass.simpleName}")
        null
    }

    /** 큰 사진은 알림에 필요한 만큼(가로 약 1024px)만 풀어 메모리를 아낀다. */
    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_IMAGE_WIDTH) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        private const val TAG = "CommerceMessaging"
        private const val MAX_IMAGE_WIDTH = 1024
    }
}
