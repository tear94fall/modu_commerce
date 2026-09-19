package com.example.moducommerce.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.moducommerce.R
import com.example.moducommerce.core.model.ProductSummary
import com.example.moducommerce.core.ui.theme.BrandRed
import com.example.moducommerce.core.ui.theme.PriceStrike
import com.example.moducommerce.core.ui.theme.ProductSurface
import com.example.moducommerce.core.util.formatPrice

/** 브랜드 레드 배경에 흰 글자·아이콘. 모든 화면의 상단바가 이것을 쓴다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommerceTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        actions = actions,
        expandedHeight = 56.dp,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** 화면 전체가 실패했을 때. 부분 실패는 스낵바를 쓴다. */
@Composable
fun ErrorBox(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(text = message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) TextButton(onClick = onRetry) { Text("다시 시도") }
        }
    }
}

@Composable
fun EmptyBox(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 로고 + 앱 이름 + 한 줄 소개. 스플래시와 로그인 화면이 같은 그림을 쓴다. */
@Composable
fun BrandingHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painter = painterResource(R.drawable.modu_logo), contentDescription = null, modifier = Modifier.size(140.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 상품 사진. 주소가 없거나 못 불러오면 옅은 바탕에 로고를 그린다. */
@Composable
fun ProductImage(url: String?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Box(modifier = modifier.background(ProductSurface), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Image(painter = painterResource(R.drawable.modu_logo), contentDescription = null, modifier = Modifier.fillMaxSize(0.4f))
        } else {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(R.drawable.modu_logo),
            )
        }
    }
}

/** 할인율 + 판매가, 정가는 취소선. 목록과 상세가 같이 쓴다. */
@Composable
fun PriceRow(price: Long, listPrice: Long?, discountRate: Int, large: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (discountRate > 0) {
            Text(
                text = stringResource(R.string.discount_format, discountRate),
                color = BrandRed,
                fontWeight = FontWeight.Bold,
                style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = stringResource(R.string.price_format, formatPrice(price)),
            fontWeight = FontWeight.Bold,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
        )
        if (discountRate > 0 && listPrice != null) {
            Text(
                text = stringResource(R.string.price_format, formatPrice(listPrice)),
                color = PriceStrike,
                textDecoration = TextDecoration.LineThrough,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** 격자·가로줄에 쓰는 상품 카드. 하트로 찜을 바로 토글한다. */
@Composable
fun ProductCard(
    product: ProductSummary,
    onClick: () -> Unit,
    onToggleWish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
            ProductImage(url = product.imageUrl, modifier = Modifier.fillMaxSize())
            if (product.soldOut) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = stringResource(R.string.sold_out), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = onToggleWish, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    imageVector = if (product.wished) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.detail_wish),
                    tint = if (product.wished) BrandRed else Color.White,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = product.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        PriceRow(price = product.price, listPrice = product.listPrice, discountRate = product.discountRate)
        Spacer(modifier = Modifier.width(0.dp))
    }
}
