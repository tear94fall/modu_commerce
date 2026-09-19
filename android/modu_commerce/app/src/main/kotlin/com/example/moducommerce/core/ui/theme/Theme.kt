package com.example.moducommerce.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 다크 테마는 범위 밖이라 라이트 팔레트 하나로 고정한다(모두의 채팅과 같은 방침). */
private val CommerceLightColors = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1E4),
    onPrimaryContainer = TextPrimary,
    secondary = BrandCoral,
    onSecondary = Color.Black,
    secondaryContainer = ProductSurface,
    onSecondaryContainer = TextPrimary,
    background = Color.White,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = ProductSurface,
    onSurfaceVariant = ModuGrey,
    outline = Divider,
    error = BrandRed,
    onError = Color.White,
)

@Composable
fun CommerceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CommerceLightColors, typography = Typography(), content = content)
}
