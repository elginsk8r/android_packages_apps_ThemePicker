/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.customization.picker.themes.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ThemeUtils
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.android.customization.picker.iconpack.data.repository.IconPackRepository
import com.android.customization.picker.themes.shared.model.ThemePackModel
import com.android.customization.picker.themes.shared.model.ThemePackPreviewModel
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Finds installed theme packages and works out which theme components each one supports. */
@Singleton
class ThemePackRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @BackgroundDispatcher private val bgDispatcher: CoroutineDispatcher,
    themeComponentRepository: ThemeComponentRepository,
    private val iconPackRepository: IconPackRepository,
) {
    private val packageManager: PackageManager = context.packageManager

    /** Installed theme packages, updated as packages are installed and removed. */
    val themePacks: Flow<List<ThemePackModel>> =
        themeComponentRepository.packageChanges.map {
            withContext(bgDispatcher) { getThemePacks() }
        }

    /** Loads what a theme package looks like. */
    suspend fun getPreview(packageName: String): ThemePackPreviewModel =
        withContext(bgDispatcher) {
            val packageContext =
                try {
                    context.createPackageContext(packageName, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    return@withContext ThemePackPreviewModel()
                }
            ThemePackPreviewModel(
                wallpaper = loadPreviewImage(packageContext, ThemeUtils.WALLPAPER_PATH),
                lockWallpaper =
                    loadPreviewImage(packageContext, ThemeUtils.LOCKSCREEN_WALLPAPER_PATH),
                icons = iconPackRepository.getPreviewIcons(packageName),
            )
        }

    private fun getThemePacks(): List<ThemePackModel> {
        val apps =
            packageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        val themes = apps.filter { ThemeUtils.isThemeApk(it) }.associateBy { it.packageName }
        val overlayComponents = getOverlayComponents(themes.keys)

        return themes.keys
            .mapNotNull { packageName ->
                val appInfo = themes[packageName] ?: return@mapNotNull null
                val components = buildList {
                    addAll(overlayComponents[packageName].orEmpty())
                    addAll(getAssetComponents(packageName))
                }
                if (components.isEmpty()) return@mapNotNull null
                ThemePackModel(
                    packageName = packageName,
                    name =
                        ThemeUtils.getThemeName(appInfo)
                            ?: appInfo.loadLabel(packageManager).toString(),
                    author =
                        appInfo.metaData?.getString(ThemeUtils.META_TAG_AUTHOR)
                            ?: appInfo.metaData?.getString(ThemeUtils.LEGACY_META_TAG_AUTHOR),
                    icon = appInfo.loadIcon(packageManager),
                    components = ThemeUtils.getAllComponents().filter { it in components },
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Maps theme packages to the components their overlays provide, using the same rules as
     * the theme component service.
     */
    private fun getOverlayComponents(themes: Set<String>): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        for (pkg in
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )) {
            val target = pkg.overlayTarget ?: continue
            if (pkg.isStaticOverlayPackage) continue
            val owner =
                ThemeUtils.getParentTheme(pkg.applicationInfo)
                    ?: themes
                        .filter { pkg.packageName == it || pkg.packageName.startsWith("$it.") }
                        .maxByOrNull { it.length }
                    ?: continue
            val component =
                when {
                    pkg.overlayCategory == OVERLAY_CATEGORY_FONT -> ThemeUtils.MODIFIES_FONTS
                    target == SYSTEMUI_PACKAGE ->
                        if (pkg.overlayCategory == ThemeUtils.OVERLAY_CATEGORY_NAVBAR) {
                            ThemeUtils.MODIFIES_NAVIGATION_BAR
                        } else {
                            ThemeUtils.MODIFIES_STATUS_BAR
                        }
                    else -> ThemeUtils.MODIFIES_OVERLAYS
                }
            result.getOrPut(owner) { mutableSetOf() }.add(component)
        }
        return result
    }

    /** @return the components a package provides through its assets and resources. */
    private fun getAssetComponents(packageName: String): Set<String> {
        val packageContext =
            try {
                context.createPackageContext(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                return emptySet()
            }
        val assets = packageContext.assets
        fun hasAssets(path: String) =
            try {
                !assets.list(path).isNullOrEmpty()
            } catch (e: IOException) {
                false
            }
        fun hasAsset(path: String) =
            try {
                assets.open(path).close()
                true
            } catch (e: IOException) {
                false
            }

        return buildSet {
            if (hasAssets(ThemeUtils.WALLPAPER_PATH)) add(ThemeUtils.MODIFIES_LAUNCHER)
            if (hasAssets(ThemeUtils.LOCKSCREEN_WALLPAPER_PATH)) {
                add(ThemeUtils.MODIFIES_LOCKSCREEN)
            }
            if (hasAssets(ThemeUtils.RINGTONES_PATH)) add(ThemeUtils.MODIFIES_RINGTONES)
            if (hasAssets(ThemeUtils.NOTIFICATIONS_PATH)) add(ThemeUtils.MODIFIES_NOTIFICATIONS)
            if (hasAssets(ThemeUtils.ALARMS_PATH)) add(ThemeUtils.MODIFIES_ALARMS)
            if (hasAsset(ThemeUtils.BOOTANIMATION_PATH)) add(ThemeUtils.MODIFIES_BOOT_ANIM)
            // cm-13.0 themes ship uncompiled overlays, which the theme service compiles
            try {
                    assets.list(ThemeUtils.LEGACY_OVERLAYS_PATH)
                } catch (e: IOException) {
                    null
                }
                ?.filter { it != ThemeUtils.LEGACY_COMMON_TARGET }
                ?.forEach { add(ThemeUtils.getLegacyOverlayComponent(it)) }
            if (iconPackRepository.hasIconPack(packageName)) add(ThemeUtils.MODIFIES_ICONS)
        }
    }

    /** Decodes the first image in an asset directory, scaled down for the preview. */
    private fun loadPreviewImage(packageContext: Context, dir: String): Bitmap? {
        val assets = packageContext.assets
        val name =
            try {
                assets.list(dir)?.sorted()?.firstOrNull()
            } catch (e: IOException) {
                null
            } ?: return null
        val path = "$dir/$name"
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sampleSize = 1
            while (
                bounds.outWidth / (sampleSize * 2) >= PREVIEW_SIZE &&
                    bounds.outHeight / (sampleSize * 2) >= PREVIEW_SIZE
            ) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: IOException) {
            Log.w(TAG, "Unable to load $path", e)
            null
        }
    }

    companion object {
        private const val TAG = "ThemePackRepository"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val OVERLAY_CATEGORY_FONT = "android.theme.customization.font"
        private const val PREVIEW_SIZE = 480
    }
}
