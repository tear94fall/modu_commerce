package com.example.moducommerce.core.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.moducommerce.MainActivity
import com.example.moducommerce.R

/** 알림 채널과 캠페인 알림 만들기. 서버는 android channel id `promotions` 로 보낸다. */
object PushNotifications {

    const val CHANNEL_PROMOTIONS = "promotions"

    /** 앱 시작 때. 이미 있으면 이름만 갱신된다. */
    fun ensureChannels(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_PROMOTIONS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.push_channel_promotions))
            .setDescription(context.getString(R.string.push_channel_promotions_desc))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** 앱이 앞에 있을 때 받은 캠페인 알림(백그라운드면 시스템이 같은 모양으로 띄운다). 권한이 없으면 조용히 넘어간다. */
    fun show(context: Context, title: String, body: String, image: Bitmap?, link: PushLink?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // 알림마다 다른 id·requestCode: 같은 PendingIntent 가 재사용돼 이전 알림의 경로로 열리지 않게.
        val id = (System.currentTimeMillis() and 0x7fffffff).toInt()
        val builder = NotificationCompat.Builder(context, CHANNEL_PROMOTIONS)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_red))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context, id, link))
        if (image != null) {
            builder.setLargeIcon(image)
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(image).bigLargeIcon(null as Bitmap?).setSummaryText(body))
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }
        manager.notify(id, builder.build())
    }

    private fun contentIntent(context: Context, requestCode: Int, link: PushLink?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (link != null) {
            intent.putExtra(PushLink.EXTRA_PATH, link.path)
            link.campaignId?.let { intent.putExtra(PushLink.EXTRA_CAMPAIGN_ID, it.toString()) }
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
