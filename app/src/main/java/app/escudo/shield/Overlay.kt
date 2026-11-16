package app.escudo.shield

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Tarjeta flotante que el servicio de accesibilidad muestra encima de cualquier
 * app (sin pedir permisos extra). Se usa para los recordatorios, presupuestos y
 * modo foco. Se cierra sola a los 25 segundos.
 */
class Overlay(private val service: AccessibilityService) {
    data class Action(val label: String, val primary: Boolean = false, val onClick: () -> Unit)

    private val wm = service.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var current: View? = null
    private val autoDismiss = Runnable { dismiss() }

    val showing get() = current != null

    fun show(title: String, body: String, actions: List<Action>) = main.post {
        dismiss()
        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#FF5B2EFF"), Color.parseColor("#FF0F8A7E")),
            ).apply { cornerRadius = dp(26).toFloat() }
            elevation = dp(8).toFloat()
        }
        card.addView(TextView(service).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.DEFAULT_BOLD
        })
        card.addView(TextView(service).apply {
            text = body
            setTextColor(Color.parseColor("#EEFFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(6), 0, dp(12))
        })
        val row = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        actions.forEach { a ->
            row.addView(TextView(service).apply {
                text = a.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setTextColor(if (a.primary) Color.parseColor("#FF3A1A9E") else Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    if (a.primary) setColor(Color.WHITE) else setStroke(dp(1), Color.WHITE)
                }
                setOnClickListener { dismiss(); a.onClick() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
        }
        card.addView(row)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            x = 0; y = dp(96)
            horizontalMargin = 0.04f
        }
        try {
            wm.addView(card, lp)
            current = card
            main.postDelayed(autoDismiss, 25_000)
        } catch (e: Exception) {
            current = null
            ShieldState.lastError = "tarjeta: ${e.javaClass.simpleName} ${e.message ?: ""}".take(200)
        }
    }

    fun dismiss() {
        main.removeCallbacks(autoDismiss)
        current?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        current = null
    }

    private fun dp(v: Int) = (v * service.resources.displayMetrics.density).toInt()
}
