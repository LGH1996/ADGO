package com.lgh.tapclick.myfunction;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lgh.tapclick.BuildConfig;

import org.apache.commons.codec.digest.DigestUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import cn.hutool.core.util.StrUtil;

public class MyUtils {
    private static final String contentProviderAuthority = "content://" + BuildConfig.APPLICATION_ID;
    private static Context mContext;

    public static void init(Context context) {
        mContext = context;
    }

    public static boolean requestShowAddDataWindow() {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put("updateScope", "showAddDataWindow");
            int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
            return re > 0;
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean isServiceRunning() {
        try (Cursor cursor = mContext.getContentResolver().query(Uri.parse(contentProviderAuthority), null, "isServiceRunning", null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex("isServiceRunning");
                return cursor.getInt(index) == 1;
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean requestUpdateAppDescribe(String packageName) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put("updateScope", "updateAppDescribe");
            contentValues.put("packageName", packageName);
            int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
            return re > 0;
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean requestRemoveAppDescribes(List<String> packages) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put("updateScope", "removeAppDescribe");
            contentValues.put("packageName", String.join(",", packages));
            int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
            return re > 0;
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean requestUpdateKeepAliveByNotification(boolean enable) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put("updateScope", "keepAliveByNotification");
            contentValues.put("value", enable);
            int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
            return re > 0;
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean requestUpdateKeepAliveByFloatingWindow(boolean enable) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put("updateScope", "keepAliveByFloatingWindow");
            contentValues.put("value", enable);
            int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
            return re > 0;
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean getKeepAliveByNotification() {
        SharedPreferences preferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        return preferences.getBoolean("keepAliveByNotification", false);
    }

    public static boolean setKeepAliveByNotification(boolean enable) {
        SharedPreferences preferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        preferences.edit().putBoolean("keepAliveByNotification", enable).apply();
        return true;
    }

    public static boolean getKeepAliveByFloatingWindow() {
        SharedPreferences preferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        return preferences.getBoolean("keepAliveByFloatingWindow", false);
    }

    public static boolean setKeepAliveByFloatingWindow(boolean enable) {
        SharedPreferences preferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        preferences.edit().putBoolean("keepAliveByFloatingWindow", enable).apply();
        return true;
    }

    public static boolean requestUpdateAllDate() {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put("updateScope", "allDate");
            int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
            return re > 0;
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean requestShowDbClickSetting() {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put("updateScope", "showDbClickSetting");
            int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
            return re > 0;
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean requestUpdateShowDbClickFloating(boolean enable) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put("updateScope", "showDbClickFloating");
            contentValues.put("value", enable);
            int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
            return re > 0;
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean getDbClickEnable() {
        SharedPreferences preferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        return preferences.getBoolean("dbClickEnable", false);
    }

    public static boolean setDbClickEnable(boolean enable) {
        SharedPreferences preferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        preferences.edit().putBoolean("dbClickEnable", enable).apply();
        return true;
    }

    public static Rect getDbClickPosition() {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        mContext.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(displayMetrics);
        float left = sharedPreferences.getFloat("dbClickLeftPercent", (displayMetrics.widthPixels - 150f) / displayMetrics.widthPixels);
        float top = sharedPreferences.getFloat("dbClickTopPercent", 0f);
        float right = sharedPreferences.getFloat("dbClickRightPercent", 1f);
        float bottom = sharedPreferences.getFloat("dbClickBottomPercent", 100f / displayMetrics.heightPixels);
        Rect rect = new Rect();
        rect.left = (int) (left * displayMetrics.widthPixels);
        rect.top = (int) (top * displayMetrics.heightPixels);
        rect.right = (int) (right * displayMetrics.widthPixels);
        rect.bottom = (int) (bottom * displayMetrics.heightPixels);
        return rect;
    }

    public static boolean setDbClickPosition(Rect rect) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        mContext.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(displayMetrics);
        sharedPreferences.edit().putFloat("dbClickLeftPercent", (float) rect.left / displayMetrics.widthPixels).apply();
        sharedPreferences.edit().putFloat("dbClickTopPercent", (float) rect.top / displayMetrics.heightPixels).apply();
        sharedPreferences.edit().putFloat("dbClickRightPercent", (float) rect.right / displayMetrics.widthPixels).apply();
        sharedPreferences.edit().putFloat("dbClickBottomPercent", (float) rect.bottom / displayMetrics.heightPixels).apply();
        return true;
    }

    public static boolean getIsFirstStart() {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        return sharedPreferences.getBoolean("isFirstStart", true);
    }

    public static void setIsFirstStart(boolean isFirstStart) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        sharedPreferences.edit().putBoolean("isFirstStart", isFirstStart).apply();
    }

    public static void setExcludeFromRecents(boolean exclude) {
        ActivityManager activityManager = mContext.getSystemService(ActivityManager.class);
        activityManager.getAppTasks().forEach(e -> e.setExcludeFromRecents(exclude));
    }

    public static String getLog() {
        try (Cursor cursor = mContext.getContentResolver().query(Uri.parse(contentProviderAuthority), null, "log", null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex("log");
                return cursor.getString(index);
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressLint("HardwareIds")
    public static String getMyDeviceNo() {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        String myDeviceNo = sharedPreferences.getString("myDeviceNo", null);
        if (StrUtil.isBlank(myDeviceNo)) {
            Map<String, Object> mapMain = new TreeMap<>();
            Field[] fields = Build.class.getFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    mapMain.put(field.getName(), field.get(Build.class));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            Map<String, Object> mapVersion = new TreeMap<>();
            fields = Build.VERSION.class.getFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    mapVersion.put(field.getName(), field.get(Build.VERSION.class));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            mapMain.put(Build.VERSION.class.getSimpleName(), mapVersion);
            String androidId = Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);
            mapMain.put(Settings.Secure.ANDROID_ID, androidId);
            Gson gson = new GsonBuilder().create();
            myDeviceNo = DigestUtils.md5Hex(gson.toJson(mapMain)).toUpperCase();
            sharedPreferences.edit().putString("myDeviceNo", myDeviceNo).apply();
        }
        return myDeviceNo;
    }
}
