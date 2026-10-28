package com.drivemate.showcase.feature.auth

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.R
import com.drivemate.showcase.ui.AppMotion
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppSizing
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.LocalReduceMotion
import com.drivemate.showcase.ui.motionDuration
import com.drivemate.showcase.ui.ds.DriveMateTextField
import com.drivemate.showcase.ui.ds.InlineAlert
import com.drivemate.showcase.ui.ds.InlineAlertActionPlacement
import com.drivemate.showcase.ui.ds.InlineAlertKind
import com.drivemate.showcase.ui.dsv2.DmIconButton
import com.drivemate.showcase.ui.dsv2.Dsv2ElevatedCard
import com.drivemate.showcase.ui.dsv2.Dsv2IconContainer
import com.drivemate.showcase.ui.dsv2.Dsv2PremiumPrimaryButton
import com.drivemate.showcase.ui.dsv2.Dsv2Sizing
import com.drivemate.showcase.ui.patterns.DmBrandWordmark
import com.drivemate.showcase.ui.patterns.DmNeuralWheel
import com.drivemate.showcase.ui.patterns.dmAuthGradientBackground
import com.drivemate.showcase.ui.patterns.rememberDmAuthBrandPalette
import com.drivemate.showcase.ui.patterns.rememberDmAuthIsDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// UI tokens are sourced from DriveMate theme files/skill.md (AppSpacing/AppShapes/AppSizing).
@Composable
fun AuthScreen(
    state: AuthUiState,
    onLogin: (String, String) -> Unit,
    onClearError: () -> Unit = {},
    prefillEmail: String? = null
) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    LaunchedEffect(prefillEmail) {
        if (!prefillEmail.isNullOrBlank()) email = prefillEmail
    }

    val loading = state is AuthUiState.Loading
    val error = (state as? AuthUiState.Error)?.error
    val uiState = LoginUiState(
        email = email,
        password = pass,
        isSubmitting = loading,
        errorMessageRes = error?.messageRes,
        emailErrorRes = error?.emailErrorRes,
        passwordErrorRes = error?.passwordErrorRes
    )

    LoginScreen(
        state = uiState,
        onEmailChange = {
            email = it
            onClearError()
        },
        onPasswordChange = {
            pass = it
            onClearError()
        },
        onSignIn = {
            if (!loading) {
                onLogin(email.trim(), pass)
            }
        },
        onDismissError = onClearError
    )
}

data class LoginUiState(
    val email: String,
    val password: String,
    val isSubmitting: Boolean,
    val errorMessageRes: Int?,
    val emailErrorRes: Int?,
    val passwordErrorRes: Int?
) {
    val isEmailValid: Boolean = email.isNotBlank()
    val isPasswordValid: Boolean = password.isNotBlank()
    val canSubmit: Boolean = isEmailValid && isPasswordValid
}

// Previews omitted: this codebase does not use Compose previews.
@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onDismissError: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val reduceMotion = LocalReduceMotion.current
    val bannerMotion = motionDuration(AppMotion.fast)
    val contentMotion = motionDuration(AppMotion.medium)
    val contentMotionSlow = motionDuration(AppMotion.slow)
    var showContent by remember { mutableStateOf(false) }

    var emailTouched by remember { mutableStateOf(false) }
    var passTouched by remember { mutableStateOf(false) }
    var submitAttempted by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val emailBringIntoView = remember { BringIntoViewRequester() }
    val passwordBringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentErrorMessageRes by rememberUpdatedState(state.errorMessageRes)
    val brandPalette = rememberDmAuthBrandPalette(isDark = rememberDmAuthIsDark())

    val showEmailError = state.emailErrorRes != null || ((emailTouched || submitAttempted) && !state.isEmailValid)
    val showPassError = state.passwordErrorRes != null || ((passTouched || submitAttempted) && !state.isPasswordValid)
    val emailErrorText = state.emailErrorRes?.let { stringResource(it) } ?: run {
        if (showEmailError) stringResource(R.string.auth_error_email_required) else null
    }
    val passErrorText = state.passwordErrorRes?.let { stringResource(it) }
        ?: if (showPassError) stringResource(R.string.auth_error_password_required) else null
    val closeActionText = stringResource(R.string.auth_action_close)
    val closeActionDescription = stringResource(R.string.auth_action_close_message)
    val emailLabel = stringResource(R.string.auth_label_email)
    val passwordLabel = stringResource(R.string.auth_label_password)
    val signInText = stringResource(R.string.auth_action_sign_in)
    val forgotPasswordText = stringResource(R.string.auth_action_forgot_password)
    val forgotPasswordInfoText = stringResource(R.string.auth_contact_hrd_ga_helper)

    LaunchedEffect(Unit) {
        showContent = true
    }

    LaunchedEffect(state.errorMessageRes) {
        val errorSnapshot = state.errorMessageRes
        if (errorSnapshot != null) {
            delay(5000)
            if (currentErrorMessageRes == errorSnapshot) {
                onDismissError()
            }
        }
    }

    val contentEnter = if (reduceMotion) {
        EnterTransition.None
    } else {
        fadeIn(animationSpec = tween(contentMotion)) +
            slideInVertically(
                initialOffsetY = { it / 8 },
                animationSpec = tween(contentMotionSlow, easing = FastOutSlowInEasing)
            )
    }

    // System bars + display cutout, but deliberately NOT ime: the form Column
    // already applies imePadding(), and folding the IME in here too would push
    // it up by the keyboard height twice. Feeding this to the Scaffold rather
    // than padding the content by hand keeps the snackbar host correctly offset
    // above the navigation bar as well.
    val contentInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = contentInsets,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(horizontal = AppSpacing.scale16, vertical = AppSpacing.scale12)
            )
        }
    ) { innerPadding ->
        // The gradient Box is the outermost visual layer and is deliberately
        // un-inset, so it reaches the true window edges behind the system bars.
        // Insets go on the content below instead.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dmAuthGradientBackground(brandPalette)
                .semantics { testTagsAsResourceId = true }
        ) {
            // One inset, not three. This previously stacked `padding(innerPadding)`
            // on top of `systemBarsPadding()` while the root DriveMateRootScaffold
            // had already applied a system-bar inset of its own — Material3's
            // Scaffold never consumes insets for its content, so all three
            // survived and the form sat ~3x the status-bar height too low.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val isTablet = maxWidth >= 700.dp
                val horizontalPadding = when {
                    isTablet -> AppSpacing.scale24
                    isLandscape -> AppSpacing.scale24
                    else -> AppSpacing.scale16
                }
                val verticalPadding = if (isLandscape) AppSpacing.scale12 else AppSpacing.scale16
                val formMaxWidth = if (isTablet) AppSizing.authFormMaxWidthTablet else AppSizing.authFormMaxWidthPhone

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = formMaxWidth)
                        .align(Alignment.Center)
                        .heightIn(min = maxHeight)
                        .verticalScroll(scrollState)
                        .imePadding()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        AnimatedVisibility(
                            visible = state.errorMessageRes != null,
                            enter = fadeIn(animationSpec = tween(bannerMotion)),
                            exit = fadeOut(animationSpec = tween(bannerMotion))
                        ) {
                            state.errorMessageRes?.let { messageRes ->
                                InlineAlert(
                                    kind = InlineAlertKind.Error,
                                    message = stringResource(messageRes),
                                    actionText = closeActionText,
                                    actionIcon = Icons.Outlined.Close,
                                    actionContentDescription = closeActionDescription,
                                    actionPlacement = InlineAlertActionPlacement.Trailing,
                                    onAction = onDismissError,
                                    containerOverride = AppPalette.surface.copy(alpha = 0.92f),
                                    contentOverride = AppPalette.onSurface,
                                    showBorder = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (state.errorMessageRes != null) {
                        Spacer(Modifier.height(AppSpacing.scale24))
                    }

                    AnimatedVisibility(
                        visible = showContent,
                        enter = contentEnter,
                        exit = ExitTransition.None
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AuthMaestroTags.ROOT),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Same mark as the splash, just smaller — same colors,
                            // same rotation — so the two screens read as one system.
                            DmNeuralWheel(
                                palette = brandPalette,
                                diameter = if (isLandscape) 64.dp else 84.dp,
                                reduceMotion = reduceMotion
                            )

                            Spacer(Modifier.height(AppSpacing.scale12))

                            DmBrandWordmark(
                                palette = brandPalette,
                                style = MaterialTheme.typography.headlineMedium
                            )

                            Spacer(Modifier.height(AppSpacing.scale24))

                            Dsv2ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                secondary = true,
                                containerColor = brandPalette.cardContainer,
                                borderColor = brandPalette.cardBorder
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                    DriveMateTextField(
                                        value = state.email,
                                        onValueChange = {
                                            emailTouched = true
                                            onEmailChange(it)
                                        },
                                        label = emailLabel,
                                        singleLine = true,
                                        enabled = !state.isSubmitting,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.None,
                                            keyboardType = KeyboardType.Text,
                                            imeAction = ImeAction.Next
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onNext = {
                                                passwordFocusRequester.requestFocus()
                                                scope.launch { passwordBringIntoView.bringIntoView() }
                                            }
                                        ),
                                        leadingIcon = {
                                            AuthLeadingIcon(
                                                icon = Icons.Outlined.Email
                                            )
                                        },
                                        errorText = emailErrorText,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag(AuthMaestroTags.EMAIL)
                                            .focusRequester(emailFocusRequester)
                                            .bringIntoViewRequester(emailBringIntoView)
                                            .onFocusEvent {
                                                if (it.isFocused) {
                                                    scope.launch { emailBringIntoView.bringIntoView() }
                                                }
                                            }
                                    )

                                    DriveMateTextField(
                                        value = state.password,
                                        onValueChange = {
                                            passTouched = true
                                            onPasswordChange(it)
                                        },
                                        label = passwordLabel,
                                        singleLine = true,
                                        enabled = !state.isSubmitting,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.None,
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                submitAttempted = true
                                                if (!state.isSubmitting) {
                                                    onSignIn()
                                                }
                                            }
                                        ),
                                        leadingIcon = {
                                            AuthLeadingIcon(
                                                icon = Icons.Outlined.Lock
                                            )
                                        },
                                        trailingIcon = {
                                            val description = if (showPassword) {
                                                stringResource(R.string.auth_password_hide)
                                            } else {
                                                stringResource(R.string.auth_password_show)
                                            }
                                            val icon = if (showPassword) {
                                                Icons.Outlined.VisibilityOff
                                            } else {
                                                Icons.Outlined.Visibility
                                            }
                                            DmIconButton(
                                                onClick = { showPassword = !showPassword },
                                                imageVector = icon,
                                                contentDescription = description,
                                                modifier = Modifier.heightIn(min = AppSizing.touchTarget)
                                            )
                                        },
                                        visualTransformation = if (showPassword) {
                                            VisualTransformation.None
                                        } else {
                                            PasswordVisualTransformation()
                                        },
                                        errorText = passErrorText,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag(AuthMaestroTags.PASSWORD)
                                            .focusRequester(passwordFocusRequester)
                                            .bringIntoViewRequester(passwordBringIntoView)
                                            .onFocusEvent {
                                                if (it.isFocused) {
                                                    scope.launch { passwordBringIntoView.bringIntoView() }
                                                }
                                            }
                                    )
                                }
                            }

                            Spacer(Modifier.height(AppSpacing.scale24))

                            Dsv2PremiumPrimaryButton(
                                text = signInText,
                                onClick = {
                                    submitAttempted = true
                                    if (!state.isSubmitting) {
                                        onSignIn()
                                    }
                                },
                                enabled = !state.isSubmitting,
                                loading = state.isSubmitting,
                                spinnerOnly = true,
                                hapticOnClick = true,
                                onHero = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(AuthMaestroTags.SIGN_IN)
                            )

                            Spacer(Modifier.height(AppSpacing.scale12))

                            TextButton(
                                onClick = {
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(
                                            message = forgotPasswordInfoText,
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                },
                                enabled = !state.isSubmitting,
                                modifier = Modifier
                                    .heightIn(min = AppSizing.touchTarget)
                                    .testTag(AuthMaestroTags.FORGOT_PASSWORD),
                                // On the brand gradient, colorScheme.primary is
                                // blue-on-blue and effectively unreadable — this
                                // link now takes its color from the same palette
                                // as the wordmark so it stays legible in both modes.
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = brandPalette.wordmarkPrimary,
                                    disabledContentColor = brandPalette.wordmarkPrimary.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = forgotPasswordText,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthLeadingIcon(icon: ImageVector) {
    Dsv2IconContainer(
        icon = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        size = Dsv2Sizing.iconContainerLg,
        iconSize = Dsv2Sizing.icon
    )
}
