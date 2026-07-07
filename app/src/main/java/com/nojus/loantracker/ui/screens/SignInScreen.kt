package com.nojus.loantracker.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nojus.loantracker.ui.AuthUiState
import com.nojus.loantracker.ui.AuthViewModel
import com.nojus.loantracker.ui.theme.LedgerSerif

@Composable
fun SignInScreen(viewModel: AuthViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AmbientBackdrop()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .entrance(appeared, delayMillis = 0)
                        .size(96.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "€",
                        style = MaterialTheme.typography.displayMedium,
                        fontFamily = LedgerSerif,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "Loan Tracker",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.entrance(appeared, delayMillis = 100)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Clear terms for loans between\npeople who trust each other.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.entrance(appeared, delayMillis = 180)
                )
                Spacer(Modifier.height(44.dp))
                Button(
                    onClick = { activity?.let(viewModel::signIn) },
                    enabled = state !is AuthUiState.Loading && activity != null,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .entrance(appeared, delayMillis = 260)
                        .height(52.dp)
                ) {
                    if (state is AuthUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Continue with Google")
                    }
                }
                if (state is AuthUiState.Error) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = (state as AuthUiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Text(
                text = "Both sides see the same terms — nothing to argue about later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            )
        }
    }
}

/** Soft radial washes of brand color that give the paper background some depth. */
@Composable
private fun AmbientBackdrop() {
    val mint = MaterialTheme.colorScheme.primaryContainer
    val gold = MaterialTheme.colorScheme.tertiaryContainer
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(mint.copy(alpha = 0.55f), mint.copy(alpha = 0f)),
                center = Offset(size.width * 0.15f, size.height * 0.12f),
                radius = size.width * 0.9f
            ),
            center = Offset(size.width * 0.15f, size.height * 0.12f),
            radius = size.width * 0.9f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(gold.copy(alpha = 0.45f), gold.copy(alpha = 0f)),
                center = Offset(size.width * 0.9f, size.height * 0.85f),
                radius = size.width * 0.8f
            ),
            center = Offset(size.width * 0.9f, size.height * 0.85f),
            radius = size.width * 0.8f
        )
    }
}

/** Fade-and-rise entrance, staggered by [delayMillis]. */
@Composable
private fun Modifier.entrance(visible: Boolean, delayMillis: Int): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 450, delayMillis = delayMillis),
        label = "entrance"
    )
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 24.dp.toPx()
    }
}
