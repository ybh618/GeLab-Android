package com.gelabzero.app.agent

data class AgentConfig(
    val apiBase: String,
    val apiKey: String,
    val model: String,
    val instruction: String,
) {
    companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini"
        fun empty() = AgentConfig("", "", DEFAULT_MODEL, "")
    }
}

data class ChatMessage(
    val role: String,
    val content: String,
)

sealed interface AgentCommand {
    data class Actions(val steps: List<AgentAction>) : AgentCommand
    data class Ask(val prompt: String) : AgentCommand
    data class Done(val summary: String) : AgentCommand
    data class Error(val message: String) : AgentCommand
}

sealed interface AgentAction {
    data class Tap(val x: Float, val y: Float) : AgentAction
    data class DoubleTap(val x: Float, val y: Float) : AgentAction
    data class LongPress(
        val x: Float,
        val y: Float,
        val durationMs: Long,
    ) : AgentAction
    data class Swipe(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val durationMs: Long,
    ) : AgentAction

    data class InputText(
        val text: String,
        val x: Float? = null,
        val y: Float? = null,
    ) : AgentAction
    data class Wait(val durationMs: Long) : AgentAction
    data class Awake(
        val appName: String?,
        val packageName: String?,
    ) : AgentAction
    data class PressHotKey(val key: HotKey) : AgentAction
    data class Info(val prompt: String) : AgentAction
    data class Abort(val reason: String) : AgentAction
    data class Complete(val summary: String) : AgentAction
    data object Back : AgentAction
    data object Home : AgentAction
}

enum class HotKey {
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    QUICK_SETTINGS,
}

sealed class AgentStatus {
    data object Idle : AgentStatus()
    data object Running : AgentStatus()
    data object WaitingForUser : AgentStatus()
    data class Failed(val reason: String) : AgentStatus()
    data class Completed(val summary: String) : AgentStatus()
}
