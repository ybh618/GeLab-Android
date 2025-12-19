package com.gelabzero.app.agent

import android.content.Context
import com.gelabzero.app.accessibility.AccessibilityActionExecutor
import com.gelabzero.app.data.AgentPreferences
import com.gelabzero.app.overlay.OverlayPromptManager
import com.gelabzero.app.notifications.CompletionNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AgentController(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = AgentPreferences(context)
    private val overlayPromptManager = OverlayPromptManager(context.applicationContext)
    private val accessibilityExecutor = AccessibilityActionExecutor()
    private val agentLoop = AgentLoop(
        api = AgentApi(),
        overlayPromptManager = overlayPromptManager,
        actionExecutor = accessibilityExecutor,
        scope = appScope,
    )
    private val completionNotifier = CompletionNotifier(context.applicationContext)

    val statusFlow = MutableStateFlow<AgentStatus>(AgentStatus.Idle)
    val configFlow: StateFlow<AgentConfig> = preferences.configFlow
        .stateIn(appScope, SharingStarted.Eagerly, AgentConfig.empty())

    init {
        appScope.launch {
            statusFlow.collect { status ->
                if (status is AgentStatus.Completed) {
                    completionNotifier.notifyTaskCompleted(status.summary)
                }
            }
        }
    }

    fun start(config: AgentConfig) {
        statusFlow.value = AgentStatus.Running
        appScope.launch {
            preferences.updateConfig(config)
        }
        agentLoop.start(config, statusFlow)
    }

    fun stop() {
        agentLoop.stop()
        statusFlow.value = AgentStatus.Idle
    }
}
