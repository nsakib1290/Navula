package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.delay

private val STARTUP_QUOTES = listOf(
    "Every great story starts with a single frame — stay curious and keep moving forward.",
    "Small consistent steps today build extraordinary creations tomorrow.",
    "Dream bold, learn deeply, and let every challenge sharpen your craft.",
    "Focus on progress over perfection — great things take patience and passion.",
    "Your imagination is the real cinema; keep creating what inspires you."
)

@Composable
fun StartupSplashOverlay(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    val quote = remember { STARTUP_QUOTES.random() }

    LaunchedEffect(visible) {
        if (visible) {
            delay(2500L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(480)) + scaleOut(
            targetScale = 1.04f,
            animationSpec = tween(480)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clickable(
                    enabled = visible,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .testTag("startup_splash_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_startup_cinema),
                contentDescription = "Navula startup illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0A0B10).copy(alpha = 0.45f),
                                Color(0xFF0A0B10).copy(alpha = 0.78f),
                                Color(0xFF0A0B10).copy(alpha = 0.94f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Navula",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                StatusPillBadge(
                    text = "Streaming app",
                    accentColor = Color(0xFF22D3EE)
                )

                Spacer(modifier = Modifier.height(26.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1A1D2B).copy(alpha = 0.82f))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.14f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"$quote\"",
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = Color(0xFFF4F6FB),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "A project By sakib",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF22D3EE),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("startup_credit_text")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("startup_continue_button")
                ) {
                    Text("Continue")
                }
            }
        }
    }
}
