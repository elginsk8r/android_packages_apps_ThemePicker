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
package com.android.customization.picker.themes.wallpaper;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ThemeUtils;
import android.os.Parcel;
import android.util.Log;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.model.InlinePreviewIntentFactory;
import com.android.wallpaper.model.WallpaperInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents a wallpaper shipped by a theme package, an image in its assets/wallpapers or
 * assets/lockscreen. See {@link ThemeUtils}.
 */
public class ThemeWallpaperInfo extends WallpaperInfo {
    private static final String TAG = "ThemeWallpaperInfo";

    public static final Creator<ThemeWallpaperInfo> CREATOR =
            new Creator<ThemeWallpaperInfo>() {
                @Override
                public ThemeWallpaperInfo createFromParcel(Parcel in) {
                    return new ThemeWallpaperInfo(in);
                }

                @Override
                public ThemeWallpaperInfo[] newArray(int size) {
                    return new ThemeWallpaperInfo[size];
                }
            };

    private final String mPackageName;
    private final String mThemeName;
    private final String mPath;
    private ThemeAsset mAsset;

    public ThemeWallpaperInfo(String packageName, String themeName, String path) {
        mPackageName = packageName;
        mThemeName = themeName;
        mPath = path;
    }

    private ThemeWallpaperInfo(Parcel in) {
        super(in);
        mPackageName = in.readString();
        mThemeName = in.readString();
        mPath = in.readString();
    }

    /**
     * @return the wallpapers of a theme package, its home screen wallpapers first.
     */
    public static List<WallpaperInfo> getAll(Context context, String packageName,
            String themeName) {
        final List<WallpaperInfo> wallpapers = new ArrayList<>();
        try {
            final Context themeContext = context.createPackageContext(packageName, 0);
            for (String dir : Arrays.asList(ThemeUtils.WALLPAPER_PATH,
                    ThemeUtils.LOCKSCREEN_WALLPAPER_PATH)) {
                final String[] assets = themeContext.getAssets().list(dir);
                if (assets == null) {
                    continue;
                }
                Arrays.sort(assets);
                for (String asset : assets) {
                    wallpapers.add(new ThemeWallpaperInfo(packageName, themeName,
                            dir + "/" + asset));
                }
            }
        } catch (PackageManager.NameNotFoundException | IOException e) {
            Log.w(TAG, "Unable to list the wallpapers of " + packageName, e);
        }
        return wallpapers;
    }

    @Override
    public List<String> getAttributions(Context context) {
        return Collections.singletonList(mThemeName);
    }

    @Override
    public Asset getAsset(Context context) {
        if (mAsset == null) {
            mAsset = new ThemeAsset(context, mPackageName, mPath);
        }
        return mAsset;
    }

    @Override
    public Asset getThumbAsset(Context context) {
        return getAsset(context);
    }

    @Override
    public String getCollectionId(Context context) {
        return getCollectionId(mPackageName);
    }

    /** @return the collection id of the wallpapers of a theme package. */
    public static String getCollectionId(String packageName) {
        return "theme_" + packageName;
    }

    @Override
    public String getWallpaperId() {
        return mPackageName + "/" + mPath;
    }

    @Override
    public void showPreview(Activity srcActivity, InlinePreviewIntentFactory factory,
            int requestCode, boolean isAssetIdPresent) {
        srcActivity.startActivityForResult(factory.newIntent(srcActivity, this,
                isAssetIdPresent, false), requestCode);
    }

    @Override
    public void showPreview(Activity srcActivity, InlinePreviewIntentFactory factory,
            int requestCode, boolean isAssetIdPresent, boolean shouldRefreshCategory) {
        srcActivity.startActivityForResult(factory.newIntent(srcActivity, this,
                isAssetIdPresent, shouldRefreshCategory), requestCode);
    }

    @Override
    @BackupPermission
    public int getBackupPermission() {
        return BACKUP_NOT_ALLOWED;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        super.writeToParcel(parcel, flags);
        parcel.writeString(mPackageName);
        parcel.writeString(mThemeName);
        parcel.writeString(mPath);
    }
}
