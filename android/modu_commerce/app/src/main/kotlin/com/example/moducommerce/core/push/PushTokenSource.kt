package com.example.moducommerce.core.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** 이 기기의 FCM 토큰. Firebase 를 쓸 수 없으면 null(푸시 기능이 조용히 꺼진다). */
interface PushTokenSource {
    suspend fun currentToken(): String?
}

/**
 * google-services.json 없이 빌드하면 FirebaseApp 이 초기화되지 않는다. 그때 FirebaseMessaging 을 부르면 예외라
 * 먼저 [FirebaseApp.getApps] 로 확인한다.
 */
@Singleton
class FirebasePushTokenSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : PushTokenSource {

    override suspend fun currentToken(): String? {
        if (!isFirebaseReady(context)) return null
        return try {
            suspendCancellableCoroutine { cont ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) Log.w(TAG, "FCM token failed", task.exception)
                    cont.resume(if (task.isSuccessful) task.result?.takeIf { it.isNotBlank() } else null)
                }
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Firebase not available", e)
            null
        }
    }

    companion object {
        private const val TAG = "PushToken"

        fun isFirebaseReady(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()
    }
}
