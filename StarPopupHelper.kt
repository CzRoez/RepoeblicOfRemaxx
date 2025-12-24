package com.cncverse

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.app.AlertDialog
import android.graphics.drawable.ColorDrawable

object PopupHelper {
    private const val TAG = "PopupHelper"
    private const val PREFS_NAME = "RpCpuXPrefs"
    private const val KEY_SHOWN_STAR_POPUP = "shown_star_popup_global"

    fun showStarPopupIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (prefs.getBoolean(KEY_SHOWN_STAR_POPUP, false)) {
            return
        }

        prefs.edit().putBoolean(KEY_SHOWN_STAR_POPUP, true).apply()

        Handler(Looper.getMainLooper()).post {
            try {
                val activity = context as? Activity ?: return@post
                showStyledDialog(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing popup: ${e.message}")
            }
        }
    }

    private fun showStyledDialog(activity: Activity) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24, activity), dp(20, activity), dp(24, activity), dp(20, activity))
            setBackgroundColor(Color.parseColor("#1a1a2e"))
        }

        val titleView = TextView(activity).apply {
            text = "⭐ Support CNCVerse!"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16, activity))
        }
        layout.addView(titleView)

        val messageView = TextView(activity).apply {
            text = "If you enjoy this extension, please consider supporting CNCVerse.\n\nYour support helps me to continue development and keep the app maintained! \uD83D\uDE80"
            setTextColor(Color.parseColor("#b0b0b0"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24, activity))
            setLineSpacing(dp(4, activity).toFloat(), 1f)
        }
        layout.addView(messageView)

        val dialog = AlertDialog.Builder(activity)
            .setView(layout)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.show()
    }

    private fun dp(value: Int, context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}