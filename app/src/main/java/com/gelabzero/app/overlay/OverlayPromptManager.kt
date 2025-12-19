package com.gelabzero.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.gelabzero.app.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OverlayPromptManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    private var overlayView: View? = null

    suspend fun requestInput(prompt: String): String = withContext(Dispatchers.Main) {
        if (!Settings.canDrawOverlays(context)) {
            return@withContext ""
        }

        val deferred = CompletableDeferred<String>()
        showPrompt(prompt) { answer ->
            deferred.complete(answer)
        }
        deferred.await()
    }

    private fun showPrompt(prompt: String, onSubmit: (String) -> Unit) {
        dismiss()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(0xEEFFFFFF.toInt())
        }

        val promptView = TextView(context).apply {
            text = prompt
            textSize = 14f
        }

        val inputView = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            setTextColor(0xFF1C1E26.toInt())
            setBackgroundColor(0xFFF2F3F5.toInt())
            isFocusableInTouchMode = true
            requestFocus()
        }

        val sendButton = Button(context).apply {
            text = context.getString(R.string.overlay_send)
            setOnClickListener {
                val answer = inputView.text?.toString().orEmpty()
                dismiss()
                onSubmit(answer)
            }
        }

        container.addView(promptView)
        container.addView(
            inputView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.addView(sendButton)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 120
        }

        overlayView = container
        windowManager.addView(container, layoutParams)
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
    }

    private fun dismiss() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // Ignore remove errors.
            }
        }
        overlayView = null
    }
}
