/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.themes.wallpaper

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import com.android.wallpaper.asset.Asset
import com.android.wallpaper.model.InlinePreviewIntentFactory
import com.android.wallpaper.model.WallpaperInfo
import lineageos.content.pm.ThemeUtils
import java.io.IOException
import java.util.Arrays

/**
 * Represents a wallpaper shipped by a theme package, an image in its assets/wallpapers or
 * assets/lockscreen. See {@link ThemeUtils}.
 */
class ThemeWallpaperInfo : WallpaperInfo, Parcelable {
    private val mPackageName: String
    private val mThemeName: String
    private val mPath: String
    private var mAsset: ThemeAsset? = null

    constructor(packageName: String, themeName: String, path: String) {
        mPackageName = packageName
        mThemeName = themeName
        mPath = path
    }

    private constructor(parcel: Parcel) : super(parcel) {
        mPackageName = parcel.readString() ?: ""
        mThemeName = parcel.readString() ?: ""
        mPath = parcel.readString() ?: ""
    }

    override fun getAttributions(context: Context): List<String> {
        return listOf(mThemeName)
    }

    override fun getAsset(context: Context): Asset {
        if (mAsset == null) {
            mAsset = ThemeAsset(context, mPackageName, mPath)
        }
        return mAsset!!
    }

    override fun getThumbAsset(context: Context): Asset {
        return getAsset(context)
    }

    override fun getCollectionId(context: Context): String {
        return getCollectionId(mPackageName)
    }

    override fun getWallpaperId(): String {
        return "$mPackageName/$mPath"
    }

    override fun showPreview(
        srcActivity: Activity,
        factory: InlinePreviewIntentFactory,
        requestCode: Int,
        isAssetIdPresent: Boolean
    ) {
        srcActivity.startActivityForResult(
            factory.newIntent(srcActivity, this, isAssetIdPresent, false),
            requestCode
        )
    }

    override fun showPreview(
        srcActivity: Activity,
        factory: InlinePreviewIntentFactory,
        requestCode: Int,
        isAssetIdPresent: Boolean,
        shouldRefreshCategory: Boolean
    ) {
        srcActivity.startActivityForResult(
            factory.newIntent(srcActivity, this, isAssetIdPresent, shouldRefreshCategory),
            requestCode
        )
    }

    @WallpaperInfo.BackupPermission
    override fun getBackupPermission(): Int {
        return BACKUP_NOT_ALLOWED
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        super.writeToParcel(parcel, flags)
        parcel.writeString(mPackageName)
        parcel.writeString(mThemeName)
        parcel.writeString(mPath)
    }

    companion object {
        private const val TAG = "ThemeWallpaperInfo"

        @JvmField
        val CREATOR: Parcelable.Creator<ThemeWallpaperInfo> =
            object : Parcelable.Creator<ThemeWallpaperInfo> {
                override fun createFromParcel(parcel: Parcel): ThemeWallpaperInfo {
                    return ThemeWallpaperInfo(parcel)
                }

                override fun newArray(size: Int): Array<ThemeWallpaperInfo?> {
                    return arrayOfNulls(size)
                }
            }

        /**
         * @return the wallpapers of a theme package, its home screen wallpapers first.
         */
        @JvmStatic
        fun getAll(context: Context, packageName: String, themeName: String): List<WallpaperInfo> {
            val wallpapers = mutableListOf<WallpaperInfo>()
            try {
                val themeContext = context.createPackageContext(packageName, 0)
                for (dir in listOf(ThemeUtils.WALLPAPER_PATH, ThemeUtils.LOCKSCREEN_WALLPAPER_PATH)) {
                    val assets = themeContext.assets.list(dir) ?: continue
                    Arrays.sort(assets)
                    for (asset in assets) {
                        wallpapers.add(ThemeWallpaperInfo(packageName, themeName, "$dir/$asset"))
                    }
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Unable to list the wallpapers of $packageName", e)
            } catch (e: IOException) {
                Log.w(TAG, "Unable to list the wallpapers of $packageName", e)
            }
            return wallpapers
        }

        /** @return the collection id of the wallpapers of a theme package. */
        @JvmStatic
        fun getCollectionId(packageName: String): String {
            return "theme_$packageName"
        }
    }
}
