package com.gelabzero.app.agent

import org.json.JSONArray
import org.json.JSONObject

object AgentProtocol {
    val systemPrompt: String = """
        You are a mobile automation agent.
        Always respond with a single JSON object and no extra text.
        Coordinates can be absolute pixels or normalized (0-1000) or (0-1).
        HotKey supports BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS.

        Types:
        {"type":"action","actions":[{"type":"tap","x":120,"y":300}]}
        {"type":"action","actions":[{"type":"doubletap","x":120,"y":300}]}
        {"type":"action","actions":[{"type":"longpress","x":120,"y":300,"durationMs":800}]}
        {"type":"action","actions":[{"type":"input","text":"hello"}]}
        {"type":"action","actions":[{"type":"input","text":"hello","x":200,"y":640}]}
        {"type":"action","actions":[{"type":"swipe","startX":100,"startY":800,"endX":100,"endY":200,"durationMs":400}]}
        {"type":"action","actions":[{"type":"scroll","startX":100,"startY":800,"endX":100,"endY":200,"durationMs":400}]}
        {"type":"action","actions":[{"type":"awake","app":"WeChat"}]}
        {"type":"action","actions":[{"type":"awake","package":"com.tencent.mm"}]}
        {"type":"action","actions":[{"type":"hotkey","key":"RECENTS"}]}
        {"type":"action","actions":[{"type":"back"}]}
        {"type":"action","actions":[{"type":"home"}]}
        {"type":"action","actions":[{"type":"wait","durationMs":500}]}
        {"type":"action","actions":[{"type":"info","prompt":"Need a verification code"}]}
        {"type":"ask","prompt":"Which account should I use?"}
        {"type":"complete","summary":"Finished"}
        {"type":"abort","reason":"Login required"}
    """.trimIndent()

    fun parse(raw: String): AgentCommand {
        val jsonText = extractJson(raw) ?: return AgentCommand.Error("Missing JSON response")
        return try {
            val json = JSONObject(jsonText)
            when (json.optString("type")) {
                "action" -> AgentCommand.Actions(parseActions(json.optJSONArray("actions")))
                "ask" -> AgentCommand.Ask(json.optString("prompt", ""))
                "done", "complete" -> AgentCommand.Done(
                    json.optString("summary", json.optString("return", json.optString("value", "")))
                )
                "abort", "error" -> AgentCommand.Error(
                    json.optString("reason", json.optString("message", json.optString("value", "")))
                )
                else -> AgentCommand.Error("Unknown response type")
            }
        } catch (e: Exception) {
            AgentCommand.Error("Invalid JSON: ${e.message}")
        }
    }

    private fun parseActions(array: JSONArray?): List<AgentAction> {
        if (array == null) return emptyList()
        val actions = mutableListOf<AgentAction>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val rawType = item.optString("type", item.optString("action_type", item.optString("action", "")))
            val type = rawType.trim().lowercase().replace("_", "")
            when (type) {
                "tap", "click" -> actions.add(
                    AgentAction.Tap(
                        x = readPointX(item),
                        y = readPointY(item),
                    )
                )
                "doubletap", "doubleclick" -> actions.add(
                    AgentAction.DoubleTap(
                        x = readPointX(item),
                        y = readPointY(item),
                    )
                )
                "longpress", "longtap" -> actions.add(
                    AgentAction.LongPress(
                        x = readPointX(item),
                        y = readPointY(item),
                        durationMs = readDurationMs(item, 800L),
                    )
                )
                "swipe", "scroll", "slide" -> actions.add(
                    AgentAction.Swipe(
                        startX = readPointX(item, "startX", "point1"),
                        startY = readPointY(item, "startY", "point1"),
                        endX = readPointX(item, "endX", "point2"),
                        endY = readPointY(item, "endY", "point2"),
                        durationMs = readDurationMs(item, 400L),
                    )
                )
                "input", "type", "inputtext" -> actions.add(
                    AgentAction.InputText(
                        text = item.optString("text", item.optString("value", "")),
                        x = readOptionalPointX(item),
                        y = readOptionalPointY(item),
                    )
                )
                "wait" -> actions.add(AgentAction.Wait(readDurationMs(item, 500L)))
                "awake" -> actions.add(
                    AgentAction.Awake(
                        appName = item.optString("app")
                            .ifBlank { item.optString("value") }
                            .ifBlank { null },
                        packageName = item.optString("package").ifBlank { null },
                    )
                )
                "hotkey" -> parseHotKey(item.optString("key", item.optString("value", "")))?.let { key ->
                    actions.add(AgentAction.PressHotKey(key))
                }
                "back" -> actions.add(AgentAction.Back)
                "home" -> actions.add(AgentAction.Home)
                "info", "pop", "ask" -> actions.add(
                    AgentAction.Info(
                        item.optString("prompt", item.optString("value", ""))
                    )
                )
                "complete" -> actions.add(
                    AgentAction.Complete(
                        item.optString("summary", item.optString("return", item.optString("value", "")))
                    )
                )
                "abort" -> actions.add(
                    AgentAction.Abort(
                        item.optString("reason", item.optString("value", ""))
                    )
                )
            }
        }
        return actions
    }

    private fun extractJson(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return trimmed.substring(start, end + 1)
    }

    private fun parseHotKey(raw: String): HotKey? {
        return when (raw.trim().uppercase()) {
            "BACK" -> HotKey.BACK
            "HOME" -> HotKey.HOME
            "RECENTS" -> HotKey.RECENTS
            "NOTIFICATIONS" -> HotKey.NOTIFICATIONS
            "QUICK_SETTINGS", "QUICKSETTINGS" -> HotKey.QUICK_SETTINGS
            else -> null
        }
    }

    private fun readDurationMs(item: JSONObject, fallbackMs: Long): Long {
        return when {
            item.has("durationMs") -> item.optLong("durationMs", fallbackMs)
            item.has("duration") -> (item.optDouble("duration", 0.5) * 1000).toLong()
            item.has("seconds") -> (item.optDouble("seconds", 0.5) * 1000).toLong()
            item.has("value") -> (item.optDouble("value", 0.5) * 1000).toLong()
            else -> fallbackMs
        }
    }

    private fun readOptionalPointX(item: JSONObject): Float? {
        return if (item.has("x") || item.has("point")) readPointX(item) else null
    }

    private fun readOptionalPointY(item: JSONObject): Float? {
        return if (item.has("y") || item.has("point")) readPointY(item) else null
    }

    private fun readPointX(item: JSONObject, primaryKey: String = "x", fallbackPointKey: String = "point"): Float {
        if (item.has(primaryKey)) {
            return item.optDouble(primaryKey).toFloat()
        }
        val point = item.optJSONArray(fallbackPointKey)
        return point?.optDouble(0)?.toFloat() ?: 0f
    }

    private fun readPointY(item: JSONObject, primaryKey: String = "y", fallbackPointKey: String = "point"): Float {
        if (item.has(primaryKey)) {
            return item.optDouble(primaryKey).toFloat()
        }
        val point = item.optJSONArray(fallbackPointKey)
        return point?.optDouble(1)?.toFloat() ?: 0f
    }
}
