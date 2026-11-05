/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.themes.data.repository

import android.app.WallpaperColors
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.theming.ThemeStyle
import android.graphics.BitmapFactory
import android.util.Log
import android.util.Xml
import androidx.annotation.ColorInt
import com.android.systemui.monet.ColorScheme
import java.io.IOException
import lineageos.content.pm.ThemeUtils
import org.xmlpull.v1.XmlPullParser

/** A seed color for the system palette suggested by a theme package. */
data class ThemeColorModel(
    val packageName: String,
    val themeName: String,
    @ColorInt val color: Int,
    @ThemeStyle.Type val style: Int,
    /** Whether the color comes from the theme's wallpaper rather than defined by the theme. */
    val isFromWallpaper: Boolean,
)

/**
 * Extracts the colors installed theme packages suggest for the system palette, from the first
 * source a theme has of:
 * * its [ThemeUtils.COLORS_ARRAY] and [ThemeUtils.COLOR_STYLE_STRING] resources,
 * * the [ThemeUtils.LEGACY_THEME_COLORS] of the common resources of a cm-13.0 theme,
 * * the colors of its first wallpaper.
 */
class ThemeColorLoader(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    /** @return the colors of all installed themes, by theme name. Call on a background thread. */
    fun load(): List<ThemeColorModel> =
        packageManager
            .getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
            .filter { ThemeUtils.isThemeApk(it) }
            .map { it to (ThemeUtils.getThemeName(it) ?: it.loadLabel(packageManager).toString()) }
            .sortedBy { (_, name) -> name.lowercase() }
            .flatMap { (appInfo, name) -> loadColors(appInfo, name) }

    /** @return the colors of a theme package. Call on a background thread. */
    fun load(packageName: String): List<ThemeColorModel> {
        val appInfo =
            try {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                )
            } catch (e: PackageManager.NameNotFoundException) {
                return emptyList()
            }
        val name = ThemeUtils.getThemeName(appInfo) ?: appInfo.loadLabel(packageManager).toString()
        return loadColors(appInfo, name)
    }

    private fun loadColors(appInfo: ApplicationInfo, themeName: String): List<ThemeColorModel> {
        val res =
            try {
                packageManager.getResourcesForApplication(appInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                return emptyList()
            }
        val packageName = appInfo.packageName
        val definedColors =
            loadDeclaredColors(res, packageName).ifEmpty { loadLegacyColors(res, packageName) }
        val colors = definedColors.ifEmpty { loadWallpaperColors(res) }
        val style = loadStyle(res, packageName)
        return colors.distinct().take(MAX_COLORS_PER_THEME).map {
            ThemeColorModel(packageName, themeName, it, style, definedColors.isEmpty())
        }
    }

    private fun loadDeclaredColors(res: Resources, packageName: String): List<Int> {
        val id = res.getIdentifier(ThemeUtils.COLORS_ARRAY, "array", packageName)
        if (id == Resources.ID_NULL) return emptyList()
        val array = res.obtainTypedArray(id)
        try {
            return (0 until array.length()).mapNotNull { i ->
                array.getColor(i, 0).takeIf { array.hasValue(i) }
            }
        } finally {
            array.recycle()
        }
    }

    @ThemeStyle.Type
    private fun loadStyle(res: Resources, packageName: String): Int {
        val id = res.getIdentifier(ThemeUtils.COLOR_STYLE_STRING, "string", packageName)
        if (id == Resources.ID_NULL) return ThemeStyle.TONAL_SPOT
        val name = res.getString(id)
        return try {
            ThemeStyle.valueOf(name.uppercase())
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Unknown color style $name of $packageName")
            ThemeStyle.TONAL_SPOT
        }
    }

    /** Reads the theme colors defined by the common resources of a cm-13.0 theme. */
    private fun loadLegacyColors(res: Resources, packageName: String): List<Int> {
        val dir =
            "${ThemeUtils.LEGACY_OVERLAYS_PATH}/${ThemeUtils.LEGACY_COMMON_TARGET}/res/values"
        val files =
            try {
                res.assets.list(dir)
            } catch (e: IOException) {
                null
            }
        if (files.isNullOrEmpty()) return emptyList()

        // Color name -> value, a color or a reference to another color
        val values = mutableMapOf<String, String>()
        for (file in files.filter { it.endsWith(".xml") }) {
            try {
                res.assets.open("$dir/$file").use { stream ->
                    val parser = Xml.newPullParser().apply { setInput(stream, "UTF-8") }
                    var type = parser.eventType
                    while (type != XmlPullParser.END_DOCUMENT) {
                        if (type == XmlPullParser.START_TAG && parser.name == "color") {
                            val name = parser.getAttributeValue(null, "name")
                            val value = parser.nextText().trim()
                            if (name != null) values[name] = value
                        }
                        type = parser.next()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to read $dir/$file of $packageName", e)
            }
        }
        return ThemeUtils.LEGACY_THEME_COLORS.mapNotNull { resolveLegacyColor(values, it) }
    }

    @ColorInt
    private fun resolveLegacyColor(values: Map<String, String>, name: String): Int? {
        var value = values[name] ?: return null
        // Follow references to other common colors
        repeat(MAX_REFERENCE_DEPTH) {
            if (!value.startsWith("@")) return parseColor(value)
            value = values[value.substringAfterLast('/')] ?: return null
        }
        return null
    }

    /** @return the seed colors of the first wallpaper of a theme. */
    private fun loadWallpaperColors(res: Resources): List<Int> {
        val wallpaper =
            try {
                res.assets.list(ThemeUtils.WALLPAPER_PATH)?.sorted()?.firstOrNull()
            } catch (e: IOException) {
                null
            } ?: return emptyList()
        return try {
            val options = BitmapFactory.Options().apply { inSampleSize = WALLPAPER_SAMPLE_SIZE }
            val bitmap =
                res.assets.open("${ThemeUtils.WALLPAPER_PATH}/$wallpaper").use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return emptyList()
            try {
                ColorScheme.getSeedColors(WallpaperColors.fromBitmap(bitmap))
            } finally {
                bitmap.recycle()
            }
        } catch (e: IOException) {
            emptyList()
        }
    }

    companion object {
        private const val TAG = "ThemeColorLoader"
        private const val MAX_COLORS_PER_THEME = 4
        private const val MAX_REFERENCE_DEPTH = 8
        private const val WALLPAPER_SAMPLE_SIZE = 8

        /** Parses #RGB, #ARGB, #RRGGBB and #AARRGGBB colors, the formats of color resources. */
        @ColorInt
        private fun parseColor(value: String): Int? {
            if (!value.startsWith("#")) return null
            val hex = value.substring(1)
            val expanded =
                when (hex.length) {
                    3 -> "ff" + hex.map { "$it$it" }.joinToString("")
                    4 -> hex.map { "$it$it" }.joinToString("")
                    6 -> "ff$hex"
                    8 -> hex
                    else -> return null
                }
            return expanded.toLongOrNull(16)?.toInt()
        }
    }
}
