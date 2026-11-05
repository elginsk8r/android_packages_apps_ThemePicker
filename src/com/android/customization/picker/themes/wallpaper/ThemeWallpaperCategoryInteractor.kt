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

package com.android.customization.picker.themes.wallpaper

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ThemeUtils
import com.android.customization.picker.themes.data.repository.ThemeComponentRepository
import com.android.wallpaper.model.WallpaperCategory
import com.android.wallpaper.picker.category.domain.interactor.CategoryInteractor
import com.android.wallpaper.picker.category.domain.interactor.implementations.CategoryInteractorImpl
import com.android.wallpaper.picker.data.category.CategoryModel
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import com.android.wallpaper.util.converter.category.CategoryFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Adds a category with the wallpapers of every installed theme package that ships any to the
 * wallpaper categories, so they can be picked without applying the rest of the theme.
 */
@Singleton
class ThemeWallpaperCategoryInteractor
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @BackgroundDispatcher private val bgDispatcher: CoroutineDispatcher,
    private val delegate: CategoryInteractorImpl,
    private val categoryFactory: CategoryFactory,
    themeComponentRepository: ThemeComponentRepository,
) : CategoryInteractor by delegate {

    private val themeCategories: Flow<List<CategoryModel>> =
        themeComponentRepository.packageChanges.map {
            withContext(bgDispatcher) { getThemeCategories() }
        }

    override val categories: Flow<List<CategoryModel>> =
        combine(delegate.categories, themeCategories) { categories, themeCategories ->
            (categories + themeCategories).sortedBy { it.commonCategoryData.priority }
        }

    private fun getThemeCategories(): List<CategoryModel> =
        context.packageManager
            .getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
            .mapNotNull { appInfo ->
                val themeName = ThemeUtils.getThemeName(appInfo) ?: return@mapNotNull null
                val wallpapers = ThemeWallpaperInfo.getAll(context, appInfo.packageName, themeName)
                if (wallpapers.isEmpty()) {
                    return@mapNotNull null
                }
                val category =
                    WallpaperCategory(
                        themeName,
                        ThemeWallpaperInfo.getCollectionId(appInfo.packageName),
                        wallpapers,
                        PRIORITY_THEMES,
                    )
                val categoryModel = categoryFactory.getCategoryModel(category)
                categoryModel.copy(
                    commonCategoryData =
                        categoryModel.commonCategoryData.copy(
                            fetchWallpapers = { _ ->
                                categoryModel.collectionCategoryData?.wallpaperModels
                            }
                        )
                )
            }
            .sortedBy { it.commonCategoryData.title.lowercase() }

    companion object {
        // Between the system wallpapers and the wallpapers on the device
        private const val PRIORITY_THEMES = 150
    }
}
