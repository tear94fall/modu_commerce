package com.example.moducommerce.core.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 화면 아래에서 올라오는 패널. Material3 `ModalBottomSheet` 는 별도 다이얼로그 창을 쓰는데, 3버튼 내비게이션 바
 * 기기(Flip3)에서 창이 바 높이만큼 아래로 밀려 맨 아래 버튼이 눌리지 않았다. 액티비티 창 안에 그리면 인셋이 맞는다.
 * 스크림을 누르거나 뒤로 가기를 하면 [onDismiss].
 */
@Composable
fun BottomPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline))
                    }
                    content()
                }
            }
        }
    }
}
