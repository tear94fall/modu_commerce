package com.example.moducommerce.core.util

import java.text.NumberFormat
import java.util.Locale

/** 89000 → "89,000". 화면은 뒤에 "원" 을 붙인다. */
fun formatPrice(amount: Long): String = NumberFormat.getNumberInstance(Locale.KOREA).format(amount)
