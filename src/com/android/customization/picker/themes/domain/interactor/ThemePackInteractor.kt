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

package com.android.customization.picker.themes.domain.interactor

import android.content.res.ThemeConfig
import com.android.customization.picker.themes.data.repository.ThemeComponentRepository
import com.android.customization.picker.themes.data.repository.ThemePackRepository
import com.android.customization.picker.themes.shared.model.ThemePackModel
import com.android.customization.picker.themes.shared.model.ThemePackPreviewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class ThemePackInteractor
@Inject
constructor(
    private val repository: ThemePackRepository,
    private val themeComponentRepository: ThemeComponentRepository,
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
     * Applies every component of a theme pack, or the device's default theme for
     * [DEFAULT_THEME_KEY].
     */
    suspend fun applyThemePack(key: String, themePacks: List<ThemePackModel>): Boolean {
        if (key == DEFAULT_THEME_KEY) {
            return themeComponentRepository.applyDefaultTheme()
        }
        val themePack = themePacks.find { it.packageName == key } ?: return false
        return themeComponentRepository.applyComponents(
            themePack.packageName,
            themePack.components,
            true,
        )
    }

    suspend fun getPreview(key: String): ThemePackPreviewModel = repository.getPreview(key)

    companion object {
        /** Key of the option applying the device's default theme. */
        const val DEFAULT_THEME_KEY = ThemeConfig.SYSTEM_DEFAULT
    }
}
