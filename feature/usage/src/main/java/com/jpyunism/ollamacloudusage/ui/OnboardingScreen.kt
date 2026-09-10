package com.jpyunism.ollamacloudusage.ui

import com.jpyunism.ollamacloudusage.core.ui.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jpyunism.ollamacloudusage.OnboardingViewModel
import kotlinx.coroutines.launch

/**
 * Pantalla raiz del onboarding guiado (issue #61, spec 01).
 *
 * Layout:
 *  - TopAppBar con titulo y accion "Saltar" (siempre visible).
 *  - Indicador de progreso (paso 1/3, 2/3, 3/3).
 *  - HorizontalPager con las 3 pantallas.
 *  - Botones de navegacion (Volver / Siguiente / Continuar).
 *
 * Cuando el VM marca `completed=true`, esta pantalla NO se vuelve a mostrar
 * (gate en MainActivity).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    vm: OnboardingViewModel = viewModel(
        factory = OnboardingViewModel.factory(LocalContext.current),
    ),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { STEP_COUNT })
    val scope = rememberCoroutineScope()

    // Si ya estaba completado al entrar (rotacion tras cerrar el flujo),
    // avisamos al Main y salimos.
    LaunchedEffect(state.completed) {
        if (state.completed) onFinished()
    }

    // Sincroniza el pager con el step del VM cuando el usuario usa los
    // botones "Siguiente"/"Volver" en lugar de swipe.
    LaunchedEffect(state.step) {
        val target = state.step.ordinal
        if (target != pagerState.currentPage && target in 0 until STEP_COUNT) {
            pagerState.animateScrollToPage(target)
        }
    }
    // Sincroniza el step del VM cuando el usuario hace swipe en el pager.
    LaunchedEffect(pagerState.currentPage) {
        val newStep = OnboardingViewModel.Step.entries.getOrElse(pagerState.currentPage) {
            OnboardingViewModel.Step.Welcome
        }
        if (newStep != state.step) vm.goToStep(newStep)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.onboarding_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TextButton(onClick = { vm.skipAndComplete() }) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                },
            )
        },
        bottomBar = {
            OnboardingBottomBar(
                step = state.step,
                method = state.method,
                validation = state.validation,
                onBack = {
                    if (state.step == OnboardingViewModel.Step.Welcome) return@OnboardingBottomBar
                    scope.launch {
                        val prev = state.step.ordinal - 1
                        if (prev >= 0) {
                            pagerState.animateScrollToPage(prev)
                            vm.back()
                        }
                    }
                },
                onNext = {
                    scope.launch {
                        val next = state.step.ordinal + 1
                        if (next < STEP_COUNT) {
                            pagerState.animateScrollToPage(next)
                            vm.next()
                        }
                    }
                },
                onContinue = {
                    when (state.method) {
                        OnboardingViewModel.Method.ApiKey -> vm.saveApiKeyAndComplete()
                        OnboardingViewModel.Method.WebView ->
                            // En WebView, el "Continuar" se habilita solo si
                            // ya se capturo cookie (el WebView la guarda al
                            // detectarla via saveCookieFromWebView). Aqui
                            // cerramos sin credencial: si no hay cookie,
                            // simplemente saltamos para no bloquear al user.
                            vm.skipAndComplete()
                        OnboardingViewModel.Method.None -> vm.skipAndComplete()
                    }
                },
                onValidate = { vm.validateApiKey() },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            Column(Modifier.fillMaxSize()) {
                ProgressHeader(currentStep = state.step.ordinal, totalSteps = STEP_COUNT)
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = false, // el VM controla la navegacion
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (OnboardingViewModel.Step.entries[page]) {
                        OnboardingViewModel.Step.Welcome -> OnboardingWelcomeStep()
                        OnboardingViewModel.Step.Method -> OnboardingMethodStep(
                            method = state.method,
                            apiKeyInput = state.apiKeyInput,
                            validation = state.validation,
                            onPickWebView = { vm.pickMethod(OnboardingViewModel.Method.WebView) },
                            onPickApiKey = { vm.pickMethod(OnboardingViewModel.Method.ApiKey) },
                            onApiKeyChange = { vm.updateApiKeyInput(it) },
                            onValidate = { vm.validateApiKey() },
                        )
                        OnboardingViewModel.Step.Validate -> OnboardingValidateStep(
                            method = state.method,
                            apiKeyInput = state.apiKeyInput,
                            validation = state.validation,
                            onValidate = { vm.validateApiKey() },
                        )
                    }
                }
            }
        }
    }
}

private const val STEP_COUNT = 3

@Composable
private fun ProgressHeader(currentStep: Int, totalSteps: Int) {
    LinearProgressIndicator(
        progress = { (currentStep + 1).toFloat() / totalSteps },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
    )
}

@Composable
private fun OnboardingBottomBar(
    step: OnboardingViewModel.Step,
    method: OnboardingViewModel.Method,
    validation: OnboardingViewModel.ValidationState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onContinue: () -> Unit,
    onValidate: () -> Unit,
) {
    val showBack = step != OnboardingViewModel.Step.Welcome
    val canContinue = when (step) {
        OnboardingViewModel.Step.Welcome -> true
        OnboardingViewModel.Step.Method -> method != OnboardingViewModel.Method.None
        OnboardingViewModel.Step.Validate -> when (method) {
            OnboardingViewModel.Method.ApiKey ->
                validation is OnboardingViewModel.ValidationState.Success ||
                    validation is OnboardingViewModel.ValidationState.Failed
            OnboardingViewModel.Method.WebView -> true
            OnboardingViewModel.Method.None -> false
        }
    }
    val primaryLabel = when (step) {
        OnboardingViewModel.Step.Welcome -> R.string.onboarding_next
        OnboardingViewModel.Step.Method -> R.string.onboarding_next
        OnboardingViewModel.Step.Validate -> R.string.onboarding_continue
    }
    val primaryAction = when (step) {
        OnboardingViewModel.Step.Validate -> onContinue
        else -> onNext
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.onboarding_back))
            }
        }
        Button(
            onClick = primaryAction,
            enabled = canContinue,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(primaryLabel))
        }
    }
}
