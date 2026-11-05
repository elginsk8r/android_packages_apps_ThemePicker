/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.wallpaper.modules

import android.content.Context
import com.android.customization.model.color.ColorCustomizationManager
import com.android.customization.model.theme.OverlayManagerCompat
import com.android.customization.module.CustomizationInjector
import com.android.customization.module.DefaultCustomizationPreferences
import com.android.customization.module.ThemePickerInjector
import com.android.customization.module.logging.ThemesUserEventLogger
import com.android.customization.module.logging.ThemesUserEventLoggerImpl
import com.android.customization.picker.clock.ai.ClockStyleViewUtil
import com.android.customization.picker.clock.ai.DefaultClockStyleViewUtil
import com.android.customization.picker.clock.data.repository.ClockPickerRepository
import com.android.customization.picker.clock.data.repository.ClockPickerRepositoryImpl
import com.android.customization.picker.clock.data.repository.ClockRegistryProvider
import com.android.customization.picker.color.data.repository.ColorPickerRepository
import com.android.customization.picker.color.data.repository.ColorPickerRepositoryImpl
import com.android.customization.picker.icon.data.repository.IconStyleRepository
import com.android.customization.picker.icon.data.repository.ThemePickerIconStyleRepository
import com.android.customization.picker.themes.wallpaper.ThemeWallpaperCategoryInteractor
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.systemui.shared.customization.data.content.CustomizationProviderClient
import com.android.systemui.shared.customization.data.content.CustomizationProviderClientImpl
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.shared.settings.data.repository.SystemSettingsRepository
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.customization.ui.binder.ThemePickerCustomizationOptionsBinder
import com.android.wallpaper.customization.ui.binder.ThemePickerToolbarBinder
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil
import com.android.wallpaper.effects.DefaultEffectsController
import com.android.wallpaper.effects.EffectsController
import com.android.wallpaper.module.DefaultExtendedEffectsHelper
import com.android.wallpaper.module.DefaultPartnerProvider
import com.android.wallpaper.module.DefaultThirdPartyLiveWallpaperModelFactory
import com.android.wallpaper.module.ExtendedEffectsHelper
import com.android.wallpaper.module.PartnerProvider
import com.android.wallpaper.module.ThirdPartyLiveWallpaperModelFactory
import com.android.wallpaper.module.WallpaperPreferences
import com.android.wallpaper.module.logging.UserEventLogger
import com.android.wallpaper.picker.category.domain.interactor.CategoriesLoadingStatusInteractor
import com.android.wallpaper.picker.category.domain.interactor.CategoryInteractor
import com.android.wallpaper.picker.category.domain.interactor.CreativeCategoryInteractor
import com.android.wallpaper.picker.category.domain.interactor.CuratedPhotosInteractor
import com.android.wallpaper.picker.category.domain.interactor.OnDeviceWallpapersInteractor
import com.android.wallpaper.picker.category.domain.interactor.ThirdPartyCategoryInteractor
import com.android.wallpaper.picker.category.domain.interactor.implementations.CreativeCategoryInteractorImpl
import com.android.wallpaper.picker.category.domain.interactor.implementations.DefaultCategoriesLoadingStatusInteractor
import com.android.wallpaper.picker.category.domain.interactor.implementations.DefaultCuratedPhotosInteractorImpl
import com.android.wallpaper.picker.category.domain.interactor.implementations.DefaultOnDeviceWallpapersInteractor
import com.android.wallpaper.picker.category.domain.interactor.implementations.ThirdPartyCategoryInteractorImpl
import com.android.wallpaper.picker.category.ui.binder.BannerProvider
import com.android.wallpaper.picker.category.ui.binder.DefaultBannerProvider
import com.android.wallpaper.picker.category.ui.view.providers.IndividualPickerFactory
import com.android.wallpaper.picker.category.ui.view.providers.implementation.DefaultIndividualPickerFactory
import com.android.wallpaper.picker.category.wrapper.DefaultWallpaperCategoryWrapper
import com.android.wallpaper.picker.category.wrapper.WallpaperCategoryWrapper
import com.android.wallpaper.picker.common.preview.ui.binder.ThemePickerWorkspaceBinder
import com.android.wallpaper.picker.common.preview.ui.binder.ThemePickerWorkspaceCallbackBinder
import com.android.wallpaper.picker.common.preview.ui.binder.WorkspaceBinder
import com.android.wallpaper.picker.common.preview.ui.binder.WorkspaceCallbackBinder
import com.android.wallpaper.picker.customization.ui.binder.CustomizationOptionsBinder
import com.android.wallpaper.picker.customization.ui.binder.DefaultPackThemeSuggestedEntryBinder
import com.android.wallpaper.picker.customization.ui.binder.PackThemeSuggestedEntryBinder
import com.android.wallpaper.picker.customization.ui.binder.ToolbarBinder
import com.android.wallpaper.picker.customization.ui.util.CustomizationOptionUtil
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import com.android.wallpaper.picker.di.modules.MainDispatcher
import com.android.wallpaper.picker.domain.interactor.PackThemeInteractor
import com.android.wallpaper.picker.domain.interactor.implementations.ThemePickerPackThemeInteractor
import com.android.wallpaper.picker.preview.ui.binder.ApplyWallpaperOptionsProvider
import com.android.wallpaper.picker.preview.ui.binder.DefaultApplyWallpaperOptionsProvider
import com.android.wallpaper.picker.preview.ui.util.DefaultImageEffectDialogUtil
import com.android.wallpaper.picker.preview.ui.util.ImageEffectDialogUtil
import com.android.wallpaper.picker.wallpapers.data.repository.DefaultRotationInitializerFactory
import com.android.wallpaper.picker.wallpapers.data.repository.RotationInitializerFactory
import com.android.wallpaper.settings.data.repository.SecureSettingsRepositoryImpl
import com.android.wallpaper.settings.data.repository.SystemSettingsRepositoryImpl
import com.android.wallpaper.util.DefaultWallpaperModelConversionHelper
import com.android.wallpaper.util.WallpaperModelConversionHelper
import com.android.wallpaper.util.converter.DefaultWallpaperModelFactory
import com.android.wallpaper.util.converter.WallpaperModelFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
abstract class ThemePickerAppModule {

    @Binds @Singleton abstract fun bindBannerProvider(impl: DefaultBannerProvider): BannerProvider

    @Binds
    @Singleton
    abstract fun bindClockPickerRepository(impl: ClockPickerRepositoryImpl): ClockPickerRepository

    @Binds
    @Singleton
    abstract fun bindColorPickerRepository(impl: ColorPickerRepositoryImpl): ColorPickerRepository

    @Binds
    @Singleton
    abstract fun bindIconStyleRepository(impl: ThemePickerIconStyleRepository): IconStyleRepository

    @Binds
    @Singleton
    abstract fun bindPackThemeSuggestedEntryBinder(
        impl: DefaultPackThemeSuggestedEntryBinder
    ): PackThemeSuggestedEntryBinder

    @Binds
    @Singleton
    abstract fun bindCreativeCategoryInteractor(
        impl: CreativeCategoryInteractorImpl
    ): CreativeCategoryInteractor

    @Binds
    @Singleton
    abstract fun bindWallpaperCategoryWrapper(
        impl: DefaultWallpaperCategoryWrapper
    ): WallpaperCategoryWrapper

    @Binds
    @Singleton
    abstract fun bindCuratedPhotosInteractor(
        impl: DefaultCuratedPhotosInteractorImpl
    ): CuratedPhotosInteractor

    @Binds
    @Singleton
    abstract fun bindCustomizationInjector(impl: ThemePickerInjector): CustomizationInjector

    @Binds
    @Singleton
    abstract fun bindCustomizationOptionsBinder(
        impl: ThemePickerCustomizationOptionsBinder
    ): CustomizationOptionsBinder

    @Binds
    @Singleton
    abstract fun bindCustomizationOptionUtil(
        impl: ThemePickerCustomizationOptionUtil
    ): CustomizationOptionUtil

    @Binds
    @Singleton
    abstract fun bindEffectsController(impl: DefaultEffectsController): EffectsController

    @Binds
    @Singleton
    abstract fun bindExtendedEffectsHelper(
        impl: DefaultExtendedEffectsHelper
    ): ExtendedEffectsHelper

    @Binds
    @Singleton
    abstract fun bindRotationInitializerFactory(
        impl: DefaultRotationInitializerFactory
    ): RotationInitializerFactory

    @Binds
    @Singleton
    abstract fun bindGoogleCategoryInteractor(
        impl: ThemeWallpaperCategoryInteractor
    ): CategoryInteractor

    @Binds
    @Singleton
    abstract fun bindOnDeviceWallpapersInteractor(
        impl: DefaultOnDeviceWallpapersInteractor
    ): OnDeviceWallpapersInteractor

    @Binds
    @Singleton
    abstract fun bindImageEffectDialogUtil(
        impl: DefaultImageEffectDialogUtil
    ): ImageEffectDialogUtil

    @Binds
    @Singleton
    abstract fun bindPackThemeInteractor(impl: ThemePickerPackThemeInteractor): PackThemeInteractor

    @Binds
    @Singleton
    abstract fun bindIndividualPickerFactoryFragment(
        impl: DefaultIndividualPickerFactory
    ): IndividualPickerFactory

    @Binds
    @Singleton
    abstract fun bindLoadingStatusInteractor(
        impl: DefaultCategoriesLoadingStatusInteractor
    ): CategoriesLoadingStatusInteractor

    @Binds
    @Singleton
    abstract fun bindPartnerProvider(impl: DefaultPartnerProvider): PartnerProvider

    @Binds
    @Singleton
    abstract fun bindThemesUserEventLogger(impl: ThemesUserEventLoggerImpl): ThemesUserEventLogger

    @Binds
    @Singleton
    abstract fun bindThirdPartyCategoryInteractor(
        impl: ThirdPartyCategoryInteractorImpl
    ): ThirdPartyCategoryInteractor

    @Binds @Singleton abstract fun bindToolbarBinder(impl: ThemePickerToolbarBinder): ToolbarBinder

    @Binds
    @Singleton
    abstract fun bindUserEventLogger(impl: ThemesUserEventLoggerImpl): UserEventLogger

    @Binds
    @Singleton
    abstract fun bindWallpaperModelFactory(
        impl: DefaultWallpaperModelFactory
    ): WallpaperModelFactory

    @Binds
    @Singleton
    abstract fun bindWallpaperModelConversionHelper(
        impl: DefaultWallpaperModelConversionHelper
    ): WallpaperModelConversionHelper

    @Binds
    @Singleton
    abstract fun bindWallpaperPreferences(
        impl: DefaultCustomizationPreferences
    ): WallpaperPreferences

    @Binds
    @Singleton
    abstract fun bindWorkspaceBinder(impl: ThemePickerWorkspaceBinder): WorkspaceBinder

    @Binds
    @Singleton
    abstract fun bindWorkspaceCallbackBinder(
        impl: ThemePickerWorkspaceCallbackBinder
    ): WorkspaceCallbackBinder

    @Binds
    @Singleton
    abstract fun bindApplyWallpaperOptionsProvider(
        impl: DefaultApplyWallpaperOptionsProvider
    ): ApplyWallpaperOptionsProvider

    @Binds
    @Singleton
    abstract fun bindThirdPartyLiveWallpaperModelFactory(
        impl: DefaultThirdPartyLiveWallpaperModelFactory
    ): ThirdPartyLiveWallpaperModelFactory

    @Binds
    @Singleton
    abstract fun bindClockStyleViewUtil(impl: DefaultClockStyleViewUtil): ClockStyleViewUtil

    companion object {

        @Provides
        @Singleton
        fun provideClockRegistry(
            @ApplicationContext context: Context,
            @MainDispatcher mainScope: CoroutineScope,
            @MainDispatcher mainDispatcher: CoroutineDispatcher,
            @BackgroundDispatcher bgDispatcher: CoroutineDispatcher,
        ): ClockRegistry {
            return ClockRegistryProvider(
                    context = context,
                    coroutineScope = mainScope,
                    mainDispatcher = mainDispatcher,
                    backgroundDispatcher = bgDispatcher,
                )
                .get()
        }

        @Provides
        @Singleton
        fun provideColorCustomizationManager(
            @ApplicationContext context: Context
        ): ColorCustomizationManager {
            return ColorCustomizationManager.getInstance(context, OverlayManagerCompat(context))
        }

        @Provides
        @Singleton
        fun provideCustomizationProviderClient(
            @ApplicationContext context: Context,
            @BackgroundDispatcher bgDispatcher: CoroutineDispatcher,
        ): CustomizationProviderClient {
            return CustomizationProviderClientImpl(context, bgDispatcher)
        }

        @Provides
        @Singleton
        fun provideFlags(): BaseFlags {
            return object : BaseFlags() {}
        }

        @Provides
        @Singleton
        fun provideSecureSettingsRepository(
            @ApplicationContext context: Context,
            @BackgroundDispatcher bgDispatcher: CoroutineDispatcher,
        ): SecureSettingsRepository {
            return SecureSettingsRepositoryImpl(context.contentResolver, bgDispatcher)
        }

        @Provides
        @Singleton
        fun provideSystemSettingsRepository(
            @ApplicationContext context: Context,
            @BackgroundDispatcher bgDispatcher: CoroutineDispatcher,
        ): SystemSettingsRepository {
            return SystemSettingsRepositoryImpl(context.contentResolver, bgDispatcher)
        }
    }
}
