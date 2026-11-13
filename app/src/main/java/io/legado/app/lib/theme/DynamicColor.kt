package io.legado.app.lib.theme

import android.content.Context
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

/**
 * Material You 动态取色：解析系统 Monet 色调资源（Android 12+），带兜底。
 * 纯函数 [buildPalette] 便于 JVM 单测；[palette] 负责实际资源解析。
 */
object DynamicColor {

    data class Palette(
        @ColorInt val primary: Int,
        @ColorInt val accent: Int,
        @ColorInt val background: Int,
        @ColorInt val bottomBackground: Int
    )

    fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    internal fun buildPalette(
        isNight: Boolean,
        fallback: Palette,
        resolve: (String) -> Int?
    ): Palette {
        fun pick(name: String, fb: Int): Int = resolve(name) ?: fb
        return if (isNight) {
            Palette(
                primary = pick("system_neutral1_800", fallback.primary),
                accent = pick("system_accent1_300", fallback.accent),
                background = pick("system_neutral1_900", fallback.background),
                bottomBackground = pick("system_neutral1_800", fallback.bottomBackground)
            )
        } else {
            Palette(
                primary = pick("system_accent1_50", fallback.primary),
                accent = pick("system_accent1_600", fallback.accent),
                background = pick("system_neutral1_50", fallback.background),
                bottomBackground = pick("system_neutral1_100", fallback.bottomBackground)
            )
        }
    }

    fun palette(context: Context, isNight: Boolean, fallback: Palette): Palette {
        return buildPalette(isNight, fallback) { name ->
            val id = context.resources.getIdentifier(name, "color", "android")
            if (id == 0) null
            else runCatching { ContextCompat.getColor(context, id) }.getOrNull()
        }
    }
}