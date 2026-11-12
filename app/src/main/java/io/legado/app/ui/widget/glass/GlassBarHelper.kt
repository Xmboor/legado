package io.legado.app.ui.widget.glass

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import com.qmdeve.liquidglass.widget.LiquidGlassView
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import java.util.Collections
import java.util.WeakHashMap

/**
 * 管理页面栏玻璃化共享组件。
 * 参数公式与 MainActivity.setupLiquidGlass 保持一致；按视图实例去重绑定，
 * 避免跨页面相同资源 id 冲突，WeakHashMap 防止内存泄漏。
 */
object GlassBarHelper {

    private val boundViews = Collections.newSetFromMap(WeakHashMap<LiquidGlassView, Boolean>())

    fun bind(
        glassView: LiquidGlassView,
        shellOverlay: View,
        barView: View,
        contentContainer: ViewGroup,
        cornerRadius: Float,
        isTopBar: Boolean
    ) {
        val context = barView.context
        if (AppConfig.isEInkMode || AppConfig.bottomBarEffectMode == "solid") {
            glassView.visibility = View.GONE
            shellOverlay.visibility = View.GONE
            barView.setBackgroundColor(
                if (isTopBar) context.primaryColor else context.bottomBackground
            )
            return
        }
        val effectMode = AppConfig.bottomBarEffectMode
        val glassLevel = when (effectMode) {
            "frosted" -> AppConfig.frostedGlassLevel / 100f
            else -> AppConfig.liquidGlassLevel / 100f
        }
        val frostedMode = effectMode == "frosted"
        glassView.visibility = View.VISIBLE
        shellOverlay.visibility = View.VISIBLE
        barView.setBackgroundColor(Color.TRANSPARENT)
        if (!contentContainer.isLaidOut) {
            contentContainer.doOnPreDraw {
                bind(glassView, shellOverlay, barView, contentContainer, cornerRadius, isTopBar)
            }
            return
        }
        val blurRadius = if (frostedMode) (10f + glassLevel * 24f).dpToPx() else 5f.dpToPx()
        val tintAlpha = if (frostedMode) 0.12f + glassLevel * 0.18f else 0.05f
        val dispersion = if (frostedMode) (0.18f + glassLevel * 0.16f).coerceAtMost(0.42f) else 0f
        val refractionHeight = if (frostedMode) (12f + glassLevel * 10f).dpToPx() else 12f.dpToPx()
        val refractionOffset = if (frostedMode) (36f + glassLevel * 18f).dpToPx() else 20f.dpToPx()
        shellOverlay.background = createShellDrawable(context, glassLevel, cornerRadius)
        if (boundViews.add(glassView)) {
            glassView.bind(contentContainer)
        }
        glassView.setCornerRadius(cornerRadius)
        glassView.setRefractionHeight(refractionHeight)
        glassView.setRefractionOffset(refractionOffset)
        glassView.setDispersion(dispersion)
        glassView.setBlurRadius(blurRadius)
        glassView.setTintAlpha(tintAlpha)
        glassView.setTintColorRed(0.70f)
        glassView.setTintColorGreen(0.79f)
        glassView.setTintColorBlue(0.86f)
        glassView.setDraggableEnabled(false)
        glassView.setElasticEnabled(true)
        glassView.setTouchEffectEnabled(true)
        glassView.isClickable = false
        glassView.isFocusable = false
        glassView.invalidate()
    }

    private fun createShellDrawable(
        context: Context,
        glassLevel: Float,
        cornerRadius: Float
    ): GradientDrawable {
        val baseColor = context.bottomBackground
        val isLight = ColorUtils.isColorLight(baseColor)
        val surfaceColor = if (isLight) {
            ColorUtils.blendColors(baseColor, Color.WHITE, 0.72f)
        } else {
            ColorUtils.blendColors(baseColor, Color.BLACK, 0.24f)
        }
        val startAlpha = (0.32f + glassLevel * 0.44f).coerceIn(0f, 0.86f)
        val centerAlpha = (0.24f + glassLevel * 0.38f).coerceIn(0f, 0.74f)
        val endAlpha = (0.18f + glassLevel * 0.32f).coerceIn(0f, 0.66f)
        val strokeAlpha = (0.22f + glassLevel * 0.22f).coerceIn(0f, 0.58f)
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ColorUtils.withAlpha(surfaceColor, startAlpha),
                ColorUtils.withAlpha(surfaceColor, centerAlpha),
                ColorUtils.withAlpha(surfaceColor, endAlpha)
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            setCornerRadius(cornerRadius)
            setStroke(1.dpToPx(), ColorUtils.withAlpha(surfaceColor, strokeAlpha))
        }
    }
}