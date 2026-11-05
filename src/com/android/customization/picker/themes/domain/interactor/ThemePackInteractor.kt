/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.themes.domain.interactor

import android.content.Context
import android.util.Log
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.customization.picker.themes.data.repository.ThemeColorLoader
import com.android.customization.picker.themes.data.repository.ThemeComponentRepository
import com.android.customization.picker.themes.data.repository.ThemePackRepository
import com.android.customization.picker.themes.shared.model.ThemePackModel
import com.android.customization.picker.themes.shared.model.ThemePackPreviewModel
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import lineageos.content.res.ThemeConfig

@Singleton
class ThemePackInteractor
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @BackgroundDispatcher private val bgDispatcher: CoroutineDispatcher,
    private val repository: ThemePackRepository,
    private val themeComponentRepository: ThemeComponentRepository,
    private val colorPickerInteractor: ColorPickerInteractor,
) {

    val isAvailable: Boolean
        get() = themeComponentRepository.isAvailable

    val themePacks: Flow<List<ThemePackModel>> = repository.themePacks

    /**
     * The key of the applied theme pack: the pack applied for most of the components it provides,
     * or [DEFAULT_THEME_KEY] if none is applied.
     *
     * @param themePacks the installed theme packs, as collected from [themePacks]
     */
    fun getSelectedThemePackKey(themePacks: Flow<List<ThemePackModel>>): Flow<String> =
        combine(themePacks, themeComponentRepository.appliedComponents) {
            themePacks,
            appliedComponents ->
            themePacks
                .map { pack ->
                    pack to pack.components.count { appliedComponents[it] == pack.packageName }
                }
                .filter { (_, appliedCount) -> appliedCount > 0 }
                .maxByOrNull { (_, appliedCount) -> appliedCount }
                ?.first
                ?.packageName ?: DEFAULT_THEME_KEY
        }

    /**
     * Applies every component of a theme pack, along with the first color it defines, or the
     * device's default theme for [DEFAULT_THEME_KEY].
     */
    suspend fun applyThemePack(key: String, themePacks: List<ThemePackModel>): Boolean {
        if (key == DEFAULT_THEME_KEY) {
            return themeComponentRepository.applyDefaultTheme()
        }
        val themePack = themePacks.find { it.packageName == key } ?: return false
        val applied =
            themeComponentRepository.applyComponents(
                themePack.packageName,
                themePack.components,
                true,
            )
        if (applied) {
            applyThemeColor(themePack.packageName)
        }
        return applied
    }

    /**
     * Applies the first color a theme pack defines, as listed by the color picker. Colors taken
     * from the theme's wallpaper are left to follow the wallpaper.
     */
    private suspend fun applyThemeColor(packageName: String) {
        val themeColor =
            withContext(bgDispatcher) { ThemeColorLoader(context).load(packageName) }
                .firstOrNull { !it.isFromWallpaper } ?: return
        val option =
            colorPickerInteractor.colorOptions
                .first()
                .flatMap { (_, options) -> options }
                .firstOrNull {
                    it.seedColor == themeColor.color &&
                        it.style == themeColor.style &&
                        it.title == themeColor.themeName
                }
        if (option == null || !colorPickerInteractor.apply(option)) {
            Log.w(TAG, "Unable to apply the color of $packageName")
        }
    }

    suspend fun getPreview(key: String): ThemePackPreviewModel = repository.getPreview(key)

    companion object {
        private const val TAG = "ThemePackInteractor"

        /** Key of the option applying the device's default theme. */
        const val DEFAULT_THEME_KEY = ThemeConfig.SYSTEM_DEFAULT
    }
}
