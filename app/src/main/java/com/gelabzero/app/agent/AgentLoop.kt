package com.gelabzero.app.agent

import com.gelabzero.app.accessibility.AccessibilityActionExecutor
import com.gelabzero.app.overlay.OverlayPromptManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

class AgentLoop(
    private val api: AgentApi,
    private val overlayPromptManager: OverlayPromptManager,
    private val actionExecutor: AccessibilityActionExecutor,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start(config: AgentConfig, statusFlow: kotlinx.coroutines.flow.MutableStateFlow<AgentStatus>) {
        stop()
        job = scope.launch {
            runLoop(config, statusFlow)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun runLoop(
        config: AgentConfig,
        statusFlow: kotlinx.coroutines.flow.MutableStateFlow<AgentStatus>,
    ) {
        if (config.apiBase.isBlank() || config.apiKey.isBlank() || config.instruction.isBlank()) {
            statusFlow.value = AgentStatus.Failed("Missing config")
            return
        }

        statusFlow.value = AgentStatus.Running

        val messages = mutableListOf(
            ChatMessage("system", AgentProtocol.systemPrompt),
            ChatMessage("user", config.instruction),
        )

        while (coroutineContext.isActive) {
            val reply = try {
                api.chat(config, messages)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                statusFlow.value = AgentStatus.Failed(e.message ?: "API failure")
                return
            }

            messages.add(ChatMessage("assistant", reply))
            when (val command = AgentProtocol.parse(reply)) {
                is AgentCommand.Actions -> {
                    val info = command.steps.filterIsInstance<AgentAction.Info>().firstOrNull()
                    if (info != null) {
                        statusFlow.value = AgentStatus.WaitingForUser
                        val answer = overlayPromptManager.requestInput(info.prompt)
                        statusFlow.value = AgentStatus.Running
                        messages.add(ChatMessage("user", answer))
                        continue
                    }

                    val terminal = command.steps.firstOrNull {
                        it is AgentAction.Complete || it is AgentAction.Abort
                    }
                    if (terminal != null) {
                        when (terminal) {
                            is AgentAction.Complete -> {
                                statusFlow.value = AgentStatus.Completed(terminal.summary)
                            }
                            is AgentAction.Abort -> {
                                statusFlow.value = AgentStatus.Failed(terminal.reason.ifBlank { "Aborted" })
                            }
                            else -> Unit
                        }
                        return
                    }

                    val result = actionExecutor.execute(command.steps)
                    messages.add(ChatMessage("user", result))
                }
                is AgentCommand.Ask -> {
                    statusFlow.value = AgentStatus.WaitingForUser
                    val answer = overlayPromptManager.requestInput(command.prompt)
                    statusFlow.value = AgentStatus.Running
                    messages.add(ChatMessage("user", answer))
                }
                is AgentCommand.Done -> {
                    statusFlow.value = AgentStatus.Completed(command.summary)
                    return
                }
                is AgentCommand.Error -> {
                    statusFlow.value = AgentStatus.Failed(command.message)
                    return
                }
            }
        }
    }
}
