/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.themes.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import lineageos.content.res.ThemeComponentManager
import lineageos.content.res.ThemeComponentManager.ThemeChangeListener
import lineageos.content.res.ThemeConfig

/** Applies theme components through the theme component service. */
@Singleton
class ThemeComponentRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @BackgroundDispatcher private val bgDispatcher: CoroutineDispatcher,
) {
    private val themeManager: ThemeComponentManager? = ThemeComponentManager.getInstance(context)

    /** Whether the theme component service is available. */
    val isAvailable: Boolean
        get() = themeManager != null

    /** Emits when packages are installed, updated or removed, and once when collected. */
    val packageChanges: Flow<Unit> = callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    trySend(Unit)
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    /** Theme component -> theme package currently applied for it. */
    val appliedComponents: Flow<Map<String, String>> =
        themeChanges().map {
            withContext(bgDispatcher) { themeManager?.appliedComponents.orEmpty() }
        }

    /**
     * Applies the given components of a theme package, waiting for the theme change to finish.
     *
     * @param removePerAppThemes whether to also remove the themes applied to single apps
     * @return whether the components were applied successfully
     */
    suspend fun applyComponents(
        packageName: String,
        components: List<String>,
        removePerAppThemes: Boolean,
    ): Boolean = awaitThemeChange {
        it.requestThemeChange(packageName, components, removePerAppThemes)
    }

    /**
     * Resets the given components to the system default, waiting for the theme change to finish.
     *
     * @param removePerAppThemes whether to also remove the themes applied to single apps
     * @return whether the components were reset successfully
     */
    suspend fun resetComponents(components: List<String>, removePerAppThemes: Boolean): Boolean =
        awaitThemeChange {
            it.requestThemeChange(
                components.associateWith { ThemeConfig.SYSTEM_DEFAULT },
                removePerAppThemes,
            )
        }

    /**
     * Applies the theme the device is configured with by default, or resets every component if
     * there is none, waiting for the theme change to finish.
     *
     * @return whether the theme was applied successfully
     */
    suspend fun applyDefaultTheme(): Boolean = awaitThemeChange { it.applyDefaultTheme() }

    private suspend fun awaitThemeChange(request: (ThemeComponentManager) -> Unit): Boolean {
        val manager = themeManager ?: return false
        return suspendCancellableCoroutine { continuation ->
            val listener = object : ThemeChangeListener {
                override fun onProgress(progress: Int) {}

                override fun onFinish(isSuccess: Boolean) {
                    manager.unregisterThemeChangeListener(this)
                    if (continuation.isActive) continuation.resume(isSuccess)
                }
            }
            manager.registerThemeChangeListener(listener)
            continuation.invokeOnCancellation { manager.unregisterThemeChangeListener(listener) }
            request(manager)
        }
    }

    private fun themeChanges(): Flow<Unit> = callbackFlow {
        val listener = object : ThemeChangeListener {
            override fun onProgress(progress: Int) {}

            override fun onFinish(isSuccess: Boolean) {
                trySend(Unit)
            }
        }
        themeManager?.registerThemeChangeListener(listener)
        trySend(Unit)
        awaitClose { themeManager?.unregisterThemeChangeListener(listener) }
    }
}
