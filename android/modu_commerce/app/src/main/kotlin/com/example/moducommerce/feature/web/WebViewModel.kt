package com.example.moducommerce.feature.web

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 브리지를 Hilt 로 만들어 WebScreen 에 건넨다. */
@HiltViewModel
class WebViewModel @Inject constructor(val bridge: ModuAppBridge) : ViewModel()
