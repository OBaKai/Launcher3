/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;

import java.util.Arrays;

/**
 运行时保存了桌面上每个项目的信息，包括图标在第几屏，第几行第几列，高度宽度等信息，
 每一个ItemInfo对象都对应着数据库中的一条记录。在Launcher3源码路径下，
 会有很多以Info结尾的类，这些类都是ItemInfo的子类，具体代表了桌面上的某个项目。
 比如说FolderIcon和FolderInfo是对应的，BubbleTextView和ShortcutInfo是对应的，
 AppWidgetHostView和LauncherAppWidgetInfo是对应的。有了对应关系，
 可以这样通过view获取ItemInfo对象：

 ItemInfo info = (ItemInfo)bubbletextview.getTag();
 这样这里的info其实就是ShortcutInfo对象了。
 */
public class ItemInfo {

    /**
     * Intent extra to store the profile. Format: UserHandle
     */
    static final String EXTRA_PROFILE = "profile";
    
    public static final int NO_ID = -1;
    
    /**
     * The id in the settings database for this item
     */
    public long id = NO_ID;
    
    /**
     * One of {@link LauncherSettings.Favorites#ITEM_TYPE_APPLICATION},
     * {@link LauncherSettings.Favorites#ITEM_TYPE_SHORTCUT},
     * {@link LauncherSettings.Favorites#ITEM_TYPE_FOLDER}, or
     * {@link LauncherSettings.Favorites#ITEM_TYPE_APPWIDGET}.
     *
     * 数据保存类型 （启动的时候好生产具体的iteminfo子类对象）
     */
    public int itemType;
    
    /**
     * The id of the container that holds this item. For the desktop, this will be 
     * {@link LauncherSettings.Favorites#CONTAINER_DESKTOP}. For the all applications folder it
     * will be {@link #NO_ID} (since it is not stored in the settings DB). For user folders
     * it will be the id of the folder.
     *
     * 图标放置的位置
     */
    public long container = NO_ID;
    
    /**
     * 在第几个屏幕上
     */
    public long screenId = -1;
    
    /**
     * 在屏幕上的第几列
     */
    public int cellX = -1;

    /**
     * 在屏幕上的第几行
     */
    public int cellY = -1;

    /**

     * Indicates the X cell span.
     * 宽度（快捷方式跟文件夹都是为 1， 小工具看具体情况）
     */
    public int spanX = 1;

    /**
     * Title of the item
     * 标题（应用名称 或者 文件夹名， 小工具不需要这属性）
     */
    public CharSequence title;

    /**
     * Content description of the item.
     */
    public CharSequence contentDescription;

    /**
     * Indicates the Y cell span.
     * 高度（快捷方式跟文件夹都是为 1， 小工具看具体情况）
     */
    public int spanY = 1;

    /**
     * Indicates the minimum X cell span.
     */
    public int minSpanX = 1;

    /**
     * Indicates the minimum Y cell span.
     */
    public int minSpanY = 1;

    /**
     * Indicates the position in an ordered list.
     */
    public int rank = 0;

    /**
     * Indicates that this item needs to be updated in the db
     */
    public boolean requiresDbUpdate = false;

    /**
     * The position of the item in a drag-and-drop operation.
     */
    public int[] dropPos = null;

    public UserHandleCompat user;

    public ItemInfo() {
        user = UserHandleCompat.myUserHandle();
    }

    ItemInfo(ItemInfo info) {
        copyFrom(info);
        // tempdebug:
        LauncherModel.checkItemInfo(this);
    }

    public void copyFrom(ItemInfo info) {
        id = info.id;
        cellX = info.cellX;
        cellY = info.cellY;
        spanX = info.spanX;
        spanY = info.spanY;
        rank = info.rank;
        screenId = info.screenId;
        itemType = info.itemType;
        container = info.container;
        user = info.user;
        contentDescription = info.contentDescription;
    }

    public Intent getIntent() {
        throw new RuntimeException("Unexpected Intent");
    }

    /**
     * Write the fields of this item to the DB
     * 
     * @param context A context object to use for getting UserManagerCompat
     * @param values
     */

    void onAddToDatabase(Context context, ContentValues values) {
        values.put(LauncherSettings.BaseLauncherColumns.ITEM_TYPE, itemType);
        values.put(LauncherSettings.Favorites.CONTAINER, container);
        values.put(LauncherSettings.Favorites.SCREEN, screenId);
        values.put(LauncherSettings.Favorites.CELLX, cellX);
        values.put(LauncherSettings.Favorites.CELLY, cellY);
        values.put(LauncherSettings.Favorites.SPANX, spanX);
        values.put(LauncherSettings.Favorites.SPANY, spanY);
        values.put(LauncherSettings.Favorites.RANK, rank);
        long serialNumber = UserManagerCompat.getInstance(context).getSerialNumberForUser(user);
        values.put(LauncherSettings.Favorites.PROFILE_ID, serialNumber);

        if (screenId == Workspace.EXTRA_EMPTY_SCREEN_ID) {
            // We should never persist an item on the extra empty screen.
            throw new RuntimeException("Screen id should not be EXTRA_EMPTY_SCREEN_ID");
        }
    }

    static void writeBitmap(ContentValues values, Bitmap bitmap) {
        if (bitmap != null) {
            byte[] data = Utilities.flattenBitmap(bitmap);
            values.put(LauncherSettings.Favorites.ICON, data);
        }
    }

    /**
     * It is very important that sub-classes implement this if they contain any references
     * to the activity (anything in the view hierarchy etc.). If not, leaks can result since
     * ItemInfo objects persist across rotation and can hence leak by holding stale references
     * to the old view hierarchy / activity.
     */
    void unbind() {
    }

    @Override
    public String toString() {
        return "Item(id=" + this.id + " type=" + this.itemType + " container=" + this.container
            + " screen=" + screenId + " cellX=" + cellX + " cellY=" + cellY + " spanX=" + spanX
            + " spanY=" + spanY + " dropPos=" + Arrays.toString(dropPos)
            + " user=" + user + ")";
    }
}
