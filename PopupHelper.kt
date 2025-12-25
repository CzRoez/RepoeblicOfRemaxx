package com.AnichinMoe

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

object PopupHelper {

    private const val PREFS_NAME = "RpCpuXPrefs"
    private const val KEY_SHOWN_SUPPORT_POPUP = "shown_support_popup_once"

    /**
     * Show popup ONLY ONCE after plugin installation
     */
    fun showIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // already shown → stop
        if (prefs.getBoolean(KEY_SHOWN_SUPPORT_POPUP, false)) return

        // mark as shown immediately (anti double call)
        prefs.edit().putBoolean(KEY_SHOWN_SUPPORT_POPUP, true).apply()

        Handler(Looper.getMainLooper()).post {
            val activity = context as? Activity ?: return@post
            showDialog(activity)
        }
    }

    private fun showDialog(activity: Activity) {

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground(activity)
            setPadding(
                dp(24, activity),
                dp(22, activity),
                dp(24, activity),
                dp(22, activity)
            )
        }

        // 🔴 top accent line
        root.addView(
            LinearLayout(activity).apply {
                setBackgroundColor(Color.parseColor("#B22222"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(4, activity)
                )
            }
        )

        val title = TextView(activity).apply {
            text = "🔰 RpCpuX 🔰\nThanks for Supporting CloudZtream Remaxx 🔥"
            textSize = 19f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(16, activity), 0, dp(12, activity))
        }

        val message = TextView(activity).apply {
            text =
                "If you Enjoy This Extension, Consider Supporting CloudZtream Builds.\n\n" +
                "Your Support 🔥 Helps Keep Development Active and Improving !"
            textSize = 14.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            lineSpacingExtra = dp(4, activity).toFloat()
        }

        root.addView(title)
        root.addView(message)

        val dialog = AlertDialog.Builder(activity)
            .setView(root)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun cardBackground(context: Context): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor("#1c1c2b"))
            cornerRadius = dp(18, context).toFloat()
            setStroke(dp(3, context), Color.parseColor("#B22222"))
        }

    private fun dp(value: Int, context: Context): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
}