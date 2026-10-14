package com.example.moducommerce.core.push

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 웹(알림 설정 화면)이 브리지로 묻는 OS 알림 권한.
 * 값은 웹과 같은 문자열: "granted" | "denied" | "default"(아직 물어본 적 없음, Android 13+ 만).
 *
 * 권한 창은 액티비티만 띄울 수 있어서 MainActivity 가 [attach] 로 런처를 걸어 둔다. 결과는 [results] 로 나가고
 * WebScreen 이 `window.ModuWeb.onNotificationPermission(result)` 로 웹에 돌려준다.
 */
@Singleton
class NotificationPermissions @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val main = Handler(Looper.getMainLooper())

    private val _results = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val results: SharedFlow<String> = _results.asSharedFlow()

    @Volatile
    private var launcher: (() -> Unit)? = null

    fun current(): String {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return if (enabled) GRANTED else DENIED
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return when {
            granted && enabled -> GRANTED
            granted -> DENIED // 권한은 있지만 설정에서 앱 알림을 껐다
            prefs.getBoolean(KEY_ASKED, false) -> DENIED
            else -> DEFAULT
        }
    }

    /** 브리지 스레드에서 불려도 된다. 물을 필요가 없으면(13 미만·이미 허용·액티비티 없음) 현재 값을 바로 돌려준다. */
    fun request() {
        main.post {
            val now = current()
            val launch = launcher
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || now == GRANTED || launch == null) {
                _results.tryEmit(now)
            } else {
                launch()
            }
        }
    }

    /** MainActivity 의 권한 요청 콜백. 한 번 물었으니 이후 거부 상태는 "denied" 다. */
    fun onRequestResult() {
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        _results.tryEmit(current())
    }

    fun attach(launch: () -> Unit) {
        launcher = launch
    }

    fun detach(launch: () -> Unit) {
        if (launcher === launch) launcher = null
    }

    /** 이 앱의 시스템 알림 설정 화면. */
    fun openSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        main.post { runCatching { context.startActivity(intent) } }
    }

    companion object {
        const val GRANTED = "granted"
        const val DENIED = "denied"
        const val DEFAULT = "default"
        private const val PREFS = "push"
        private const val KEY_ASKED = "notification-permission-asked"
    }
}
