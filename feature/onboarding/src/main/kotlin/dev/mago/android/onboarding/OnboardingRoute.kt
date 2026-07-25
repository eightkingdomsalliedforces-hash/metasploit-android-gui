package dev.mago.android.onboarding

import androidx.compose.runtime.Composable
import dev.mago.android.installation.InstallationState

@Composable
fun OnboardingRoute(
    state: InstallationState,
    onRetry: () -> Unit,
    onOpenTermux: () -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onShowDetails: () -> Unit,
) {
    OnboardingScreen(
        state = state,
        onRetry = onRetry,
        onOpenTermux = onOpenTermux,
        onRequestTermuxPermission = onRequestTermuxPermission,
        onShowDetails = onShowDetails,
    )
}
