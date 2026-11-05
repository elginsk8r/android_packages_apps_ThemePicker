/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.iconpack.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import com.android.customization.picker.iconpack.shared.model.IconPackModel
import com.android.customization.picker.themes.data.repository.ThemeComponentRepository
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import lineageos.content.pm.ThemeUtils
import org.xmlpull.v1.XmlPullParser

/** Finds installed icon packs and loads previews of them. */
@Singleton
class IconPackRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @BackgroundDispatcher private val bgDispatcher: CoroutineDispatcher,
    themeComponentRepository: ThemeComponentRepository,
) {
    private val packageManager: PackageManager = context.packageManager

    /** Installed icon packs, updated as packages are installed and removed. */
    val iconPacks: Flow<List<IconPackModel>> =
        themeComponentRepository.packageChanges.map {
            withContext(bgDispatcher) { getIconPacks() }
        }

    /** @return a few app icons as the icon pack draws them. */
    suspend fun getPreviewIcons(packageName: String): List<Drawable> =
        withContext(bgDispatcher) {
            try {
                loadPreviewIcons(
                    context.createPackageContext(packageName, 0).resources,
                    packageName,
                )
            } catch (e: PackageManager.NameNotFoundException) {
                emptyList()
            }
        }

    private fun getIconPacks(): List<IconPackModel> {
        val themes =
            packageManager
                .getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
                .filter { ThemeUtils.isThemeApk(it) && hasIconPack(it.packageName) }
        return (themes + getLegacyIconPacks().values)
            .distinctBy { it.packageName }
            .map { appInfo ->
                IconPackModel(
                    packageName = appInfo.packageName,
                    name =
                        ThemeUtils.getThemeName(appInfo)
                            ?: appInfo.loadLabel(packageManager).toString(),
                    icon = appInfo.loadIcon(packageManager),
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun getLegacyIconPacks(): Map<String, ApplicationInfo> =
        ThemeUtils.getLegacyIconPackIntents()
            .flatMap {
                packageManager.queryIntentActivities(it, PackageManager.ResolveInfoFlags.of(0))
            }
            .mapNotNull { it.activityInfo?.applicationInfo }
            .associateBy { it.packageName }

    /** @return whether a theme package ships an icon pack. */
    fun hasIconPack(packageName: String): Boolean {
        val res =
            try {
                context.createPackageContext(packageName, 0).resources
            } catch (e: PackageManager.NameNotFoundException) {
                return false
            }
        return APPFILTER_ASSETS.any { path ->
            try {
                res.assets.open(path).close()
                true
            } catch (e: IOException) {
                false
            }
        } ||
            res.getIdentifier("appfilter", "xml", packageName) != 0 ||
            res.getIdentifier("theme_iconpack", "array", packageName) != 0 ||
            res.getIdentifier("icon_pack", "array", packageName) != 0
    }

    /** @return icons the theme's icon pack maps to a few of the installed apps. */
    private fun loadPreviewIcons(res: Resources, packageName: String): List<Drawable> {
        val iconMap = readIconMap(res, packageName)
        if (iconMap.isEmpty()) return emptyList()

        val launcherIntent =
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager
            .queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
            .asSequence()
            .map { it.activityInfo }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            .mapNotNull { activity ->
                val component = ComponentName(activity.packageName, activity.name)
                val drawable =
                    iconMap[component.flattenToString().lowercase(Locale.ROOT)]
                        ?: iconMap[activity.packageName.lowercase(Locale.ROOT)]
                        ?: return@mapNotNull null
                loadIconPackDrawable(res, packageName, drawable)
            }
            .take(PREVIEW_ICON_COUNT)
            .toList()
    }

    /** Reads the component -> drawable mapping of an icon pack's appfilter. */
    private fun readIconMap(res: Resources, packageName: String): Map<String, String> {
        val stream =
            APPFILTER_ASSETS.firstNotNullOfOrNull { path ->
                try {
                    res.assets.open(path)
                } catch (e: IOException) {
                    null
                }
            }
        val parser: XmlPullParser =
            stream?.let { Xml.newPullParser().apply { setInput(it, "UTF-8") } }
                ?: res.getIdentifier("appfilter", "xml", packageName)
                    .takeIf { it != 0 }
                    ?.let { res.getXml(it) }
                ?: return emptyMap()

        val iconMap = mutableMapOf<String, String>()
        try {
            var type = parser.eventType
            while (type != XmlPullParser.END_DOCUMENT) {
                if (type == XmlPullParser.START_TAG && parser.name.equals("item", true)) {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (
                        component != null &&
                            drawable != null &&
                            component.startsWith("ComponentInfo{") &&
                            component.endsWith("}")
                    ) {
                        val name = component.substring(14, component.length - 1)
                        val key =
                            if ("/" in name) {
                                ComponentName.unflattenFromString(name)?.flattenToString()
                            } else {
                                name
                            }
                        key?.let { iconMap[it.lowercase(Locale.ROOT)] = drawable }
                    }
                }
                type = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read the appfilter of $packageName", e)
        } finally {
            stream?.close()
            (parser as? XmlResourceParser)?.close()
        }
        return iconMap
    }

    private fun loadIconPackDrawable(
        res: Resources,
        packageName: String,
        name: String,
    ): Drawable? {
        // Icon packs embedded in cm-13.0 themes keep their drawables in the assets
        val legacyDirs =
            try {
                res.assets.list(ThemeUtils.LEGACY_ICONS_PATH)
            } catch (e: IOException) {
                null
            }
        if (!legacyDirs.isNullOrEmpty()) {
            for (dir in legacyDirs.filter { it.startsWith("drawable") }.sortedDescending()) {
                try {
                    res.assets.open("${ThemeUtils.LEGACY_ICONS_PATH}/$dir/$name.png").use {
                        BitmapFactory.decodeStream(it)?.let { bitmap ->
                            return BitmapDrawable(context.resources, bitmap)
                        }
                    }
                } catch (e: IOException) {
                    // Not in this directory
                }
            }
            return null
        }
        val resId = res.getIdentifier(name, "drawable", packageName)
        return if (resId != 0) {
            try {
                res.getDrawable(resId, null)
            } catch (e: Resources.NotFoundException) {
                null
            }
        } else {
            null
        }
    }

    companion object {
        private const val TAG = "IconPackRepository"
        private const val PREVIEW_ICON_COUNT = 4
        private val APPFILTER_ASSETS =
            listOf(ThemeUtils.APPFILTER_ASSET, ThemeUtils.LEGACY_APPFILTER_ASSET)
    }
}
