package com.ragclient.android.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ragclient.android.R
import com.ragclient.android.core.designsystem.AppColors
import kotlinx.coroutines.delay

/**
 * Branded start: an opaque panel with the Thagaval wordmark covers the app,
 * the logo fades in, holds, then the whole panel fades out. The real content
 * composes underneath the whole time, so the session gate resolves while the
 * logo is on screen instead of after it.
 */
@Composable
fun SplashGate(content: @Composable () -> Unit) {
    var showSplash by remember { mutableStateOf(true) }
    val logoAlpha = remember { Animatable(0f) }
    val panelAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(LogoFadeInMillis))
        delay(HoldMillis)
        panelAlpha.animateTo(0f, tween(PanelFadeOutMillis))
        showSplash = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (showSplash) {
            // The background belongs to the overlay, not the root: on a warm
            // start the app underneath is already painted, and a background on
            // the root would sit behind it and leave the logo floating over
            // the chat screen.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(SplashBackground)
                    .graphicsLayer { this.alpha = panelAlpha.value },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.thagaval_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier
                        .fillMaxWidth(0.66f)
                        .widthIn(max = 300.dp)
                        .graphicsLayer { this.alpha = logoAlpha.value },
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

/**
 * Always the light paper surface: the wordmark's lettering is black, so it
 * would vanish on the dark theme's background.
 */
private val SplashBackground = AppColors.Surface

private const val LogoFadeInMillis = 220
private const val HoldMillis = 650L
private const val PanelFadeOutMillis = 260
