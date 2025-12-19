package com.gelabzero.app.accessibility

import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.gelabzero.app.agent.AgentAction
import com.gelabzero.app.agent.HotKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AccessibilityActionExecutor {
    suspend fun execute(actions: List<AgentAction>): String = withContext(Dispatchers.Main) {
        val service = AgentAccessibilityService.instance
            ?: return@withContext "Accessibility service not running"

        val notes = mutableListOf<String>()

        for (action in actions) {
            when (action) {
                is AgentAction.Tap -> performTap(service, action.x, action.y)
                is AgentAction.DoubleTap -> performDoubleTap(service, action.x, action.y)
                is AgentAction.LongPress -> performLongPress(service, action.x, action.y, action.durationMs)
                is AgentAction.Swipe -> performSwipe(
                    service,
                    action.startX,
                    action.startY,
                    action.endX,
                    action.endY,
                    action.durationMs,
                )
                is AgentAction.InputText -> {
                    if (action.x != null && action.y != null) {
                        performTap(service, action.x, action.y)
                        delay(200)
                    }
                    setText(service, action.text)
                }
                is AgentAction.Wait -> delay(action.durationMs)
                is AgentAction.Awake -> {
                    val success = launchApp(service, action.appName, action.packageName)
                    if (!success) {
                        notes.add("Awake failed")
                    } else {
                        delay(800)
                    }
                }
                is AgentAction.PressHotKey -> {
                    val success = performHotKey(service, action.key)
                    if (!success) {
                        notes.add("HotKey ${action.key} failed")
                    }
                }
                is AgentAction.Info -> Unit
                is AgentAction.Abort -> Unit
                is AgentAction.Complete -> Unit
                AgentAction.Back -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                AgentAction.Home -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            }
        }

        return@withContext if (notes.isEmpty()) "Actions executed" else notes.joinToString("; ")
    }

    private suspend fun performTap(
        service: AgentAccessibilityService,
        x: Float,
        y: Float,
    ) {
        val (screenX, screenY) = toScreenPoint(service, x, y)
        val path = Path().apply {
            moveTo(screenX, screenY)
            lineTo(screenX, screenY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        dispatchGesture(service, GestureDescription.Builder().addStroke(stroke).build())
    }

    private suspend fun performSwipe(
        service: AgentAccessibilityService,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
    ) {
        val (screenStartX, screenStartY) = toScreenPoint(service, startX, startY)
        val (screenEndX, screenEndY) = toScreenPoint(service, endX, endY)
        val path = Path().apply {
            moveTo(screenStartX, screenStartY)
            lineTo(screenEndX, screenEndY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        dispatchGesture(service, GestureDescription.Builder().addStroke(stroke).build())
    }

    private suspend fun performLongPress(
        service: AgentAccessibilityService,
        x: Float,
        y: Float,
        durationMs: Long,
    ) {
        val (screenX, screenY) = toScreenPoint(service, x, y)
        val path = Path().apply {
            moveTo(screenX, screenY)
            lineTo(screenX, screenY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        dispatchGesture(service, GestureDescription.Builder().addStroke(stroke).build())
    }

    private suspend fun performDoubleTap(
        service: AgentAccessibilityService,
        x: Float,
        y: Float,
    ) {
        performTap(service, x, y)
        delay(120)
        performTap(service, x, y)
    }

    private suspend fun dispatchGesture(
        service: AgentAccessibilityService,
        gesture: GestureDescription,
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(Unit)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        service.dispatchGesture(gesture, callback, null)
    }

    private fun setText(service: AgentAccessibilityService, text: String) {
        val node = service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (node != null && node.isEditable) {
            try {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            } finally {
                node.recycle()
            }
        }
    }

    private fun performHotKey(service: AgentAccessibilityService, key: HotKey): Boolean {
        val action = when (key) {
            HotKey.BACK -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            HotKey.HOME -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
            HotKey.RECENTS -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
            HotKey.NOTIFICATIONS -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            HotKey.QUICK_SETTINGS -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        }
        return service.performGlobalAction(action)
    }

    private fun launchApp(
        service: AgentAccessibilityService,
        appName: String?,
        packageName: String?,
    ): Boolean {
        val resolvedPackage = packageName ?: resolvePackageName(service.packageManager, appName)
            ?: return false
        val intent = service.packageManager.getLaunchIntentForPackage(resolvedPackage)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
        return true
    }

    private fun resolvePackageName(packageManager: PackageManager, appName: String?): String? {
        if (appName.isNullOrBlank()) return null
        val target = appName.trim().lowercase()
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in apps) {
            val label = packageManager.getApplicationLabel(app).toString().lowercase()
            if (label == target || label.contains(target)) {
                return app.packageName
            }
            if (app.packageName.lowercase().contains(target)) {
                return app.packageName
            }
        }
        return null
    }

    private fun toScreenPoint(
        service: AgentAccessibilityService,
        x: Float,
        y: Float,
    ): Pair<Float, Float> {
        val metrics = service.resources.displayMetrics
        val width = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val height = metrics.heightPixels.toFloat().coerceAtLeast(1f)
        return when {
            x in 0f..1f && y in 0f..1f -> Pair(x * width, y * height)
            x in 0f..1000f && y in 0f..1000f -> Pair((x / 1000f) * width, (y / 1000f) * height)
            else -> Pair(x, y)
        }
    }
}
