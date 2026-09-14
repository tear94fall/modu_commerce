package com.example.moducommerce

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 초기 화면. 모두의 채팅과 같이 창 배경으로 로고만 보여 주고 바로 다음 화면으로 넘긴다.
 * 저장된 토큰이 있으면 상품 목록으로, 없으면 로그인 화면으로 보낸다.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val next = when (StartDestination.of(TokenStore(this).access())) {
            StartDestination.PRODUCTS -> ProductListActivity::class.java
            StartDestination.LOGIN -> LoginActivity::class.java
        }
        startActivity(Intent(this, next))
        finish()
    }
}
