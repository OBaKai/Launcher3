/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.launcher3;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import com.android.launcher3.util.Log;

import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.util.Thunk;

import java.lang.ref.WeakReference;

/**
 * 单例模式，主要在启动的时候用，
 * 他初始化了一些对象，并且注册了广播监听器和ContentObserver
 * */
public class LauncherAppState {

    private final AppFilter mAppFilter;
    private final BuildInfo mBuildInfo;
    @Thunk final LauncherModel mModel;
    private final IconCache mIconCache;
    private final WidgetPreviewLoader mWidgetCache;

    private boolean mWallpaperChangedSinceLastCheck;

    private static WeakReference<LauncherProvider> sLauncherProvider;
    private static Context sContext;

    private static LauncherAppState INSTANCE;

    private InvariantDeviceProfile mInvariantDeviceProfile;

    private LauncherAccessibilityDelegate mAccessibilityDelegate;

    public static LauncherAppState getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LauncherAppState();
        }
        return INSTANCE;
    }

    public static LauncherAppState getInstanceNoCreate() {
        return INSTANCE;
    }

    public Context getContext() {
        return sContext;
    }

    public static void setApplicationContext(Context context) {
        if (sContext != null) {
            Log.w(Launcher.TAG, "setApplicationContext called twice! old=" + sContext + " new=" + context);
        }
        sContext = context.getApplicationContext();
    }

    private LauncherAppState() {
        if (sContext == null) {
            throw new IllegalStateException("LauncherAppState inited before app context set");
        }

        Log.v(Launcher.TAG, "LauncherAppState inited");

        /** 调试内存空间 */
        if (sContext.getResources().getBoolean(R.bool.debug_memory_enabled)) {
            MemoryTracker.startTrackingMe(sContext, "L");
        }

        /** 初始化固定的设备配置 */
        mInvariantDeviceProfile = new InvariantDeviceProfile(sContext);
        /** 初始化图标管理工具 */
        mIconCache = new IconCache(sContext, mInvariantDeviceProfile);
        /** 初始化Widget加载混存工具 */
        mWidgetCache = new WidgetPreviewLoader(sContext, mIconCache);

        /** 初始化了 mAppFilter 和  mBuildInfo */
        /** Class.forName().newInstance()相当于new */
        mAppFilter = AppFilter.loadByName(sContext.getString(R.string.app_filter_class));
        mBuildInfo = BuildInfo.loadByName(sContext.getString(R.string.build_info_class));

        //这里初始化了伟大的LauncherModel
        //这是一个BroadcastReceiver
        mModel = new LauncherModel(this, mIconCache, mAppFilter);
        LauncherAppsCompat.getInstance(sContext).addOnAppsChangedCallback(mModel);

        /** 注册广播监听 */
        IntentFilter filter = new IntentFilter();
        //locale信息改变的广播
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(SearchManager.INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED);
        // 管理资料的add、remove广播
        filter.addAction(LauncherAppsCompat.ACTION_MANAGED_PROFILE_ADDED);
        filter.addAction(LauncherAppsCompat.ACTION_MANAGED_PROFILE_REMOVED);

        sContext.registerReceiver(mModel, filter);
        UserManagerCompat.getInstance(sContext).enableAndResetCache();
    }

    /**
     * Call from Application.onTerminate(), which is not guaranteed to ever be called.
     */
    public void onTerminate() {
        sContext.unregisterReceiver(mModel);
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(sContext);
        launcherApps.removeOnAppsChangedCallback(mModel);
        PackageInstallerCompat.getInstance(sContext).onStop();
    }

    /**
     * Reloads the workspace items from the DB and re-binds the workspace. This should generally
     * not be called as DB updates are automatically followed by UI update
     */
    public void reloadWorkspace() {
        mModel.resetLoadedState(false, true);
        mModel.startLoaderFromBackground();
    }

    LauncherModel setLauncher(Launcher launcher) {
        getLauncherProvider().setLauncherProviderChangeListener(launcher);
        mModel.initialize(launcher);
        mAccessibilityDelegate = ((launcher != null) && Utilities.ATLEAST_LOLLIPOP) ?
            new LauncherAccessibilityDelegate(launcher) : null;
        return mModel;
    }

    public LauncherAccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    public IconCache getIconCache() {
        return mIconCache;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    static void setLauncherProvider(LauncherProvider provider) {
        sLauncherProvider = new WeakReference<LauncherProvider>(provider);
    }

    public static LauncherProvider getLauncherProvider() {
        return sLauncherProvider.get();
    }

    public static String getSharedPreferencesKey() {
        return LauncherFiles.SHARED_PREFERENCES_KEY;
    }

    public WidgetPreviewLoader getWidgetCache() {
        return mWidgetCache;
    }
    
    public void onWallpaperChanged() {
        mWallpaperChangedSinceLastCheck = true;
    }

    public boolean hasWallpaperChangedSinceLastCheck() {
        boolean result = mWallpaperChangedSinceLastCheck;
        mWallpaperChangedSinceLastCheck = false;
        return result;
    }

    public InvariantDeviceProfile getInvariantDeviceProfile() {
        return mInvariantDeviceProfile;
    }

    public static boolean isDogfoodBuild() {
        return getInstance().mBuildInfo.isDogfoodBuild();
    }
}
