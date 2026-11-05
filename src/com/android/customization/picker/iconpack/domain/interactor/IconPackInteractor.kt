/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.iconpack.domain.interactor

import android.graphics.drawable.Drawable
import com.android.customization.picker.iconpack.data.repository.IconPackRepository
import com.android.customization.picker.iconpack.shared.model.IconPackModel
import com.android.customization.picker.themes.data.repository.ThemeComponentRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import lineageos.content.pm.ThemeUtils
import lineageos.content.res.ThemeConfig

@Singleton
class IconPackInteractor
@Inject
constructor(
    private val repository: IconPackRepository,
    private val themeComponentRepository: ThemeComponentRepository,
) {
    val isAvailable: Boolean
        get() = themeComponentRepository.isAvailable

    val iconPacks: Flow<List<IconPackModel>> = repository.iconPacks

    /** The key of the applied icon pack, or [DEFAULT_ICON_PACK_KEY] if none is applied. */
    val selectedIconPackKey: Flow<String> =
        themeComponentRepository.appliedComponents.map {
            it[ThemeUtils.MODIFIES_ICONS] ?: DEFAULT_ICON_PACK_KEY
        }

    /** Applies an icon pack, or restores the apps' own icons for [DEFAULT_ICON_PACK_KEY]. */
    suspend fun applyIconPack(key: String): Boolean =
        if (key == DEFAULT_ICON_PACK_KEY) {
            themeComponentRepository.resetComponents(ICON_COMPONENTS)
        } else {
            themeComponentRepository.applyComponents(key, ICON_COMPONENTS)
        }

    suspend fun getPreviewIcons(key: String): List<Drawable> = repository.getPreviewIcons(key)

    companion object {
        /** Key of the option restoring the apps' own icons. */
        const val DEFAULT_ICON_PACK_KEY = ThemeConfig.SYSTEM_DEFAULT

        private val ICON_COMPONENTS = listOf(ThemeUtils.MODIFIES_ICONS)
    }
}
