package app.boostz.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.boostz.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Intro à l'ouverture : le « Z » néon apparaît en grandissant, puis laisse place à l'application. */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val appear = remember { Animatable(0f) }
    val glow = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch { appear.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        delay(950)
        glow.animateTo(1.06f, tween(300))
        glow.animateTo(1f, tween(300))
        delay(250)
        done()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Un toucher passe l'intro.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.intro_z),
            contentDescription = "BoostZ",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .graphicsLayer {
                    val s = (0.78f + 0.22f * appear.value) * glow.value
                    scaleX = s
                    scaleY = s
                    alpha = appear.value
                }
        )
    }
}
