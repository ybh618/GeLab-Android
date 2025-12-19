package com.gelabzero.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gelabzero.app.accessibility.AccessibilityPermissionUtils
import com.gelabzero.app.accessibility.AgentAccessibilityService
import com.gelabzero.app.agent.AgentConfig
import com.gelabzero.app.agent.AgentStatus
import com.gelabzero.app.ui.theme.GelabTheme

class MainActivity : ComponentActivity() {
    private val overlayAllowed = mutableStateOf(false)
    private val accessibilityEnabled = mutableStateOf(false)
    private val notificationAllowed = mutableStateOf(true)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        notificationAllowed.value = isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GelabTheme {
                MainScreen(
                    overlayAllowed = overlayAllowed,
                    accessibilityEnabled = accessibilityEnabled,
                    notificationAllowed = notificationAllowed,
                    requestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayAllowed.value = AccessibilityPermissionUtils.canDrawOverlays(this)
        accessibilityEnabled.value = AccessibilityPermissionUtils.isAccessibilityServiceEnabled(
            this,
            AgentAccessibilityService::class.java,
        )
        notificationAllowed.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

@Composable
private fun MainScreen(
    overlayAllowed: MutableState<Boolean>,
    accessibilityEnabled: MutableState<Boolean>,
    notificationAllowed: MutableState<Boolean>,
    requestNotificationPermission: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val controller = (context.applicationContext as AgentApp).agentController
    val storedConfig by controller.configFlow.collectAsStateWithLifecycle()
    val status by controller.statusFlow.collectAsStateWithLifecycle()

    var apiBase by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf(AgentConfig.DEFAULT_MODEL) }
    var instruction by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(storedConfig) {
        apiBase = storedConfig.apiBase
        apiKey = storedConfig.apiKey
        model = storedConfig.model
        instruction = storedConfig.instruction
    }

    val overlayStatus = if (overlayAllowed.value) {
        stringResource(R.string.permission_granted)
    } else {
        stringResource(R.string.permission_missing)
    }
    val accessibilityStatus = if (accessibilityEnabled.value) {
        stringResource(R.string.accessibility_enabled)
    } else {
        stringResource(R.string.accessibility_disabled)
    }
    val notificationStatus = if (notificationAllowed.value) {
        stringResource(R.string.permission_granted)
    } else {
        stringResource(R.string.permission_missing)
    }
    @Composable
    fun localizeError(reason: String): String {
        val trimmed = reason.trim()
        if (trimmed.isBlank()) {
            return stringResource(R.string.error_unknown)
        }
        if (trimmed.startsWith("API error ")) {
            val code = trimmed.removePrefix("API error ").trimStart().takeWhile { it.isDigit() }
            return when (code) {
                "400" -> stringResource(R.string.error_api_bad_request)
                "401" -> stringResource(R.string.error_api_unauthorized)
                "403" -> stringResource(R.string.error_api_forbidden)
                "404" -> stringResource(R.string.error_api_not_found)
                "409" -> stringResource(R.string.error_api_conflict)
                "429" -> stringResource(R.string.error_api_rate_limited)
                "500" -> stringResource(R.string.error_api_server_error)
                "502" -> stringResource(R.string.error_api_bad_gateway)
                "503" -> stringResource(R.string.error_api_unavailable)
                "504" -> stringResource(R.string.error_api_gateway_timeout)
                else -> stringResource(R.string.error_api_error_with_code, code.ifBlank { "?" })
            }
        }
        val lowered = trimmed.lowercase()
        return when {
            trimmed == "Missing config" -> stringResource(R.string.error_missing_config)
            trimmed == "API failure" -> stringResource(R.string.error_api_failure)
            trimmed == "Aborted" -> stringResource(R.string.error_aborted)
            trimmed == "Missing choices in response" ->
                stringResource(R.string.error_response_missing_choices)
            trimmed == "Empty choices in response" ->
                stringResource(R.string.error_response_empty_choices)
            trimmed == "Invalid choice item" ->
                stringResource(R.string.error_response_invalid_choice)
            trimmed == "No content in response" ->
                stringResource(R.string.error_response_no_content)
            lowered.contains("timeout") || lowered.contains("timed out") ->
                stringResource(R.string.error_network_timeout)
            lowered.contains("unable to resolve host") ->
                stringResource(R.string.error_network_dns)
            lowered.contains("failed to connect") ->
                stringResource(R.string.error_network_connection_failed)
            lowered.contains("canceled") || lowered.contains("cancelled") ->
                stringResource(R.string.error_request_canceled)
            else -> trimmed
        }
    }

    val statusText = when (val current = status) {
        is AgentStatus.Idle -> stringResource(R.string.status_idle)
        is AgentStatus.Running -> stringResource(R.string.status_running)
        is AgentStatus.WaitingForUser -> stringResource(R.string.status_waiting)
        is AgentStatus.Failed -> {
            val localizedReason = localizeError(current.reason)
            stringResource(R.string.status_error, localizedReason)
        }
        is AgentStatus.Completed -> {
            val summary = current.summary.trim()
            if (summary.isBlank()) {
                stringResource(R.string.status_done)
            } else {
                stringResource(R.string.status_done_with_summary, summary)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.title_agent_control),
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = apiBase,
            onValueChange = { apiBase = it },
            label = { Text(stringResource(R.string.label_api_base)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text(stringResource(R.string.label_api_key)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text(stringResource(R.string.label_model)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = instruction,
            onValueChange = { instruction = it },
            label = { Text(stringResource(R.string.label_instruction)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.overlay_permission, overlayStatus),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = stringResource(R.string.accessibility_service, accessibilityStatus),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = stringResource(R.string.notification_permission, notificationStatus),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = stringResource(R.string.status_label, statusText),
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(
            onClick = {
                if (!overlayAllowed.value) {
                    AccessibilityPermissionUtils.openOverlaySettings(context)
                    return@Button
                }
                if (!accessibilityEnabled.value) {
                    AccessibilityPermissionUtils.openAccessibilitySettings(context)
                    return@Button
                }
                if (!notificationAllowed.value) {
                    requestNotificationPermission()
                    return@Button
                }

                controller.start(
                    AgentConfig(
                        apiBase = apiBase.trim(),
                        apiKey = apiKey.trim(),
                        model = model.trim(),
                        instruction = instruction.trim(),
                    )
                )
                activity?.moveTaskToBack(true)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.button_start))
        }

        Button(
            onClick = { controller.stop() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.button_stop))
        }

        if (!overlayAllowed.value || !accessibilityEnabled.value || !notificationAllowed.value) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.warning_permissions),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
