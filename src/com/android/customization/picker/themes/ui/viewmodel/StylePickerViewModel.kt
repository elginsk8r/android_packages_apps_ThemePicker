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
package com.android.customization.picker.themes.ui.viewmodel

import android.content.Context
import android.graphics.drawable.Drawable
import com.android.customization.picker.themes.domain.interactor.ThemePackInteractor
import com.android.customization.picker.themes.domain.interactor.ThemePackInteractor.Companion.DEFAULT_THEME_KEY
import com.android.customization.picker.themes.shared.model.ThemePackModel
import com.android.customization.picker.themes.shared.model.ThemePackPreviewModel
import com.android.themepicker.R
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel2
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * Picks a style, a theme package whose components are all applied together, modeled on the grid
 * picker.
 */
class StylePickerViewModel
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    private val interactor: ThemePackInteractor,
    @Assisted private val viewModelScope: CoroutineScope,
) {
    private val themePacks: Flow<List<ThemePackModel>> =
        interactor.themePacks.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    val isStyleCustomizationAvailable: Flow<Boolean> =
        themePacks
            .map { interactor.isAvailable && it.isNotEmpty() }
            .shareIn(scope = viewModelScope, started = SharingStarted.Lazily, replay = 1)

    // The key of the currently applied theme pack
    private val selectedThemePackKey: Flow<String> =
        interactor.getSelectedThemePackKey(themePacks).shareIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            replay = 1,
        )

    // The currently applied theme pack
    val selectedThemePack: Flow<OptionItemViewModel2<Drawable>> =
        combine(themePacks, selectedThemePackKey) { themePacks, selectedKey ->
                toOptionItemViewModel(themePacks.find { it.packageName == selectedKey })
            }
            .shareIn(scope = viewModelScope, started = SharingStarted.Lazily, replay = 1)

    private val defaultThemeIcon: Drawable? =
        context.getDrawable(R.drawable.ic_nav_theme)

    private val overridingThemePackKey = MutableStateFlow<String?>(null)
    // If the overriding key is null, use the currently applied theme pack
    val previewingThemePackKey: Flow<String> =
        combine(overridingThemePackKey, selectedThemePackKey) { overridingKey, selectedKey ->
                overridingKey ?: selectedKey
            }
            .shareIn(scope = viewModelScope, started = SharingStarted.Lazily, replay = 1)

    /** The theme pack being previewed, or null for the default theme. */
    val previewingThemePack: Flow<ThemePackModel?> =
        combine(previewingThemePackKey, themePacks) { key, themePacks ->
            themePacks.find { it.packageName == key }
        }

    /**
     * The theme pack being previewed, or null for the default theme, and what it looks like once
     * that has been loaded.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val previewingThemePackPreview: Flow<Pair<ThemePackModel?, ThemePackPreviewModel?>> =
        previewingThemePack
            .distinctUntilChanged()
            .transformLatest { themePack ->
                emit(themePack to null)
                if (themePack != null) {
                    emit(themePack to interactor.getPreview(themePack.packageName))
                }
            }
            .shareIn(scope = viewModelScope, started = SharingStarted.Lazily, replay = 1)

    val themePackOptionListItems: Flow<List<OptionItemViewModel2<Drawable>>> =
        themePacks
            .map { themePacks ->
                listOf(toOptionItemViewModel(null)) + themePacks.map { toOptionItemViewModel(it) }
            }
            .shareIn(scope = viewModelScope, started = SharingStarted.Lazily, replay = 1)

    val onApply: Flow<(suspend () -> Unit)?> =
        combine(overridingThemePackKey, selectedThemePackKey, themePacks) {
            overridingKey,
            selectedKey,
            themePacks ->
            if (overridingKey != null && overridingKey != selectedKey) {
                { interactor.applyThemePack(overridingKey, themePacks) }
            } else {
                null
            }
        }

    fun resetPreview() {
        overridingThemePackKey.value = null
    }

    /** @param themePack the theme pack, or null for the default theme */
    private fun toOptionItemViewModel(themePack: ThemePackModel?): OptionItemViewModel2<Drawable> {
        val key = themePack?.packageName ?: DEFAULT_THEME_KEY
        val isSelected =
            previewingThemePackKey
                .map { it == key }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Lazily,
                    initialValue = false,
                )
        return OptionItemViewModel2(
            key = MutableStateFlow(key),
            payload = themePack?.icon ?: defaultThemeIcon,
            text =
                themePack?.let { Text.Loaded(it.name) }
                    ?: Text.Resource(R.string.styles_default),
            isSelected = isSelected,
            onClicked =
                isSelected.map {
                    if (!it) {
                        { overridingThemePackKey.value = key }
                    } else {
                        null
                    }
                },
        )
    }

    @ViewModelScoped
    @AssistedFactory
    interface Factory {
        fun create(viewModelScope: CoroutineScope): StylePickerViewModel
    }
}
