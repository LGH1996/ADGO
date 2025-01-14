package com.lgh.tapclick.myfunction;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lgh.tapclick.R;
import com.lgh.tapclick.databinding.ViewAddDataBinding;
import com.lgh.tapclick.databinding.ViewDbClickSettingBinding;
import com.lgh.tapclick.databinding.ViewDialogWarningBinding;
import com.lgh.tapclick.databinding.ViewWidgetSelectBinding;
import com.lgh.tapclick.myactivity.EditDataActivity;
import com.lgh.tapclick.myactivity.ListDataActivity;
import com.lgh.tapclick.myactivity.MainActivity;
import com.lgh.tapclick.mybean.AppDescribe;
import com.lgh.tapclick.mybean.Coordinate;
import com.lgh.tapclick.mybean.Widget;
import com.lgh.tapclick.myclass.DataDao;
import com.lgh.tapclick.myclass.MyApplication;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import cn.hutool.core.util.StrUtil;

/**
 * adb shell pm grant com.lgh.advertising.going android.permission.WRITE_SECURE_SETTINGS
 * adb shell settings put secure enabled_accessibility_services com.lgh.tapclick/com.lgh.tapclick.myfunction.MyAccessibilityService
 * adb shell settings put secure accessibility_enabled 1
 * <p>
 * Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, getPackageName() + "/" + MyAccessibilityService.class.getName());
 * Settings.Secure.putString(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "1");
 */

public class MainFunction {
    private final AccessibilityService service;
    private final WindowManager windowManager;
    private final PackageManager packageManager;
    private final InputMethodManager inputMethodManager;
    private final DataDao dataDao;
    private final Map<String, AppDescribe> appDescribeMap;
    private final ScheduledExecutorService executorServiceMain;
    private final ScheduledExecutorService executorServiceSub;
    private final Set<Widget> alreadyClickSet;
    private final Set<Widget> debounceSet;
    private final LinkedList<String> logList;
    private final Gson gson;
    private final Gson gsonNoPretty;
    private final SimpleDateFormat simpleDateFormat;
    private volatile AppDescribe appDescribe;
    private volatile String currentPackage;
    private volatile String currentPackageSub;
    private volatile String currentActivity;
    private volatile String prePackage;
    private volatile String preActivity;
    private volatile boolean onOffWidget;
    private volatile boolean onOffWidgetSub;
    private volatile boolean onOffCoordinate;
    private volatile boolean onOffCoordinateSub;
    private volatile boolean needChangeActivity;
    private volatile ScheduledFuture<?> futureWidget;
    private volatile ScheduledFuture<?> futureCoordinate;
    private volatile Map<String, Set<Coordinate>> coordinateSetMap;
    private volatile Map<String, Set<Widget>> widgetSetMap;
    private volatile Set<Coordinate> coordinateSet;
    private volatile Set<Widget> widgetSet;
    private volatile AccessibilityServiceInfo serviceInfo;
    private MyBroadcastReceiver myBroadcastReceiver;
    private WindowManager.LayoutParams aParams, bParams, cParams;
    private ViewAddDataBinding addDataBinding;
    private ViewWidgetSelectBinding widgetSelectBinding;
    private ImageView viewClickPosition;
    private Set<String> pkgSuggestNotOnList;
    private View ignoreView;
    private WindowManager.LayoutParams dbClickLp;
    private View dbClickView;

    public MainFunction(AccessibilityService accessibilityService) {
        service = accessibilityService;
        inputMethodManager = accessibilityService.getSystemService(InputMethodManager.class);
        windowManager = accessibilityService.getSystemService(WindowManager.class);
        packageManager = accessibilityService.getPackageManager();
        executorServiceMain = Executors.newSingleThreadScheduledExecutor();
        executorServiceSub = Executors.newSingleThreadScheduledExecutor();
        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        gson = new GsonBuilder().setPrettyPrinting().create();
        gsonNoPretty = new GsonBuilder().create();
        appDescribe = new AppDescribe();
        alreadyClickSet = new HashSet<>();
        appDescribeMap = new HashMap<>();
        debounceSet = new HashSet<>();
        logList = new LinkedList<>();
        dataDao = MyApplication.dataDao;
        currentPackage = StrUtil.EMPTY;
        currentPackageSub = StrUtil.EMPTY;
        currentActivity = StrUtil.EMPTY;
        prePackage = StrUtil.EMPTY;
        preActivity = StrUtil.EMPTY;
    }

    protected void onServiceConnected() {
        serviceInfo = service.getServiceInfo();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        myBroadcastReceiver = new MyBroadcastReceiver();
        service.registerReceiver(myBroadcastReceiver, intentFilter);
        executorServiceSub.execute(this::getRunningData);
        futureWidget = executorServiceSub.schedule(System::currentTimeMillis, 0, TimeUnit.MILLISECONDS);
        futureCoordinate = executorServiceSub.schedule(System::currentTimeMillis, 0, TimeUnit.MILLISECONDS);
        keepAliveByNotification(MyUtils.getKeepAliveByNotification());
        keepAliveByFloatingWindow(MyUtils.getKeepAliveByFloatingWindow());
        showDbClickFloating(MyUtils.getDbClickEnable());

        /*executorService.schedule(new Runnable() {
            @Override
            public void run() {
                NotificationManager notificationManager = service.getSystemService(NotificationManager.class);
                Notification.Builder builder = new Notification.Builder(service)
                        .setAutoCancel(true)
                        .setSmallIcon(R.drawable.app)
                        .setContentTitle(service.getText(R.string.app_name))
                        .setContentText("占用内存" + Debug.getPss() + "kb");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setChannelId(service.getPackageName());
                    NotificationChannel channel = new NotificationChannel(service.getPackageName(), service.getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
                    notificationManager.createNotificationChannel(channel);
                }
                notificationManager.notify(service.getPackageName(), 0x01, builder.build());
                executorService.schedule(this, 5000, TimeUnit.MILLISECONDS);
            }
        }, 0, TimeUnit.MILLISECONDS);*/
    }

    @SuppressLint("SwitchIntDef")
    public void onAccessibilityEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: {
                AccessibilityNodeInfo root = service.getRootInActiveWindow();
                if (root == null) {
                    break;
                }
                String packageName = root.getPackageName() != null ? root.getPackageName().toString() : null;
                String activityName = event.getClassName() != null ? event.getClassName().toString() : null;
                if (packageName == null) {
                    break;
                }
                List<AccessibilityWindowInfo> windowInfoList = service.getWindows();
                if (windowInfoList.isEmpty()) {
                    break;
                }
                List<AccessibilityNodeInfo> nodeInfoList = new ArrayList<>();
                for (AccessibilityWindowInfo windowInfo : windowInfoList) {
                    AccessibilityNodeInfo nodeInfo = windowInfo.getRoot();
                    if (nodeInfo != null && TextUtils.equals(nodeInfo.getPackageName(), packageName)) {
                        nodeInfoList.add(nodeInfo);
                    }
                }
                if (!packageName.equals(currentPackage)) {
                    addLog("打开应用：" + packageName);
                    currentPackage = packageName;
                    appDescribe = appDescribeMap.get(packageName);
                    if (appDescribe == null) {
                        appDescribe = new AppDescribe();
                    }
                }
                if (!event.isFullScreen()
                        && !appDescribe.coordinateOnOff
                        && !appDescribe.widgetOnOff
                        && !currentPackageSub.isEmpty()
                        && !currentActivity.isEmpty()) {
                    break;
                }
                if (!packageName.equals(currentPackageSub)) {
                    needChangeActivity = true;
                    currentPackageSub = packageName;
                    futureWidget.cancel(false);
                    futureCoordinate.cancel(false);
                    serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                    service.setServiceInfo(serviceInfo);
                    debounceSet.clear();
                    onOffWidgetSub = false;
                    onOffCoordinateSub = false;
                    widgetSetMap = appDescribe.widgetSetMap;
                    coordinateSetMap = appDescribe.coordinateSetMap;
                    onOffWidget = appDescribe.widgetOnOff && !widgetSetMap.isEmpty();
                    onOffCoordinate = appDescribe.coordinateOnOff && !coordinateSetMap.isEmpty();

                    if (onOffWidget && !appDescribe.widgetRetrieveAllTime) {
                        futureWidget = executorServiceSub.schedule(new Runnable() {
                            @Override
                            public void run() {
                                onOffWidget = false;
                                onOffWidgetSub = false;
                                serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                service.setServiceInfo(serviceInfo);
                            }
                        }, appDescribe.widgetRetrieveTime, TimeUnit.MILLISECONDS);
                    }

                    if (onOffCoordinate && !appDescribe.coordinateRetrieveAllTime) {
                        futureCoordinate = executorServiceSub.schedule(new Runnable() {
                            @Override
                            public void run() {
                                onOffCoordinate = false;
                                onOffCoordinateSub = false;
                            }
                        }, appDescribe.coordinateRetrieveTime, TimeUnit.MILLISECONDS);
                    }
                }

                if (activityName == null) {
                    break;
                }
                if (!TextUtils.equals(event.getPackageName(), currentPackage)) {
                    break;
                }
                if ((!activityName.equals(currentActivity)
                        && !activityName.startsWith("android.view.")
                        && !activityName.startsWith("android.widget."))
                        || (activityName.equals("android.widget.FrameLayout")
                        && needChangeActivity)) {
                    addLog("进入页面：" + activityName);
                    serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                    service.setServiceInfo(serviceInfo);
                    needChangeActivity = false;
                    currentActivity = activityName;
                    alreadyClickSet.clear();
                    coordinateSet = coordinateSetMap != null ? coordinateSetMap.get(activityName) : null;
                    widgetSet = widgetSetMap != null ? widgetSetMap.get(activityName) : null;
                    onOffCoordinateSub = onOffCoordinate && coordinateSet != null;
                    onOffWidgetSub = onOffWidget && widgetSet != null;

                    if (onOffWidgetSub) {
                        serviceInfo.eventTypes |= AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                        service.setServiceInfo(serviceInfo);
                    }

                    if (onOffCoordinateSub) {
                        for (Coordinate coordinate : coordinateSet) {
                            executorServiceSub.scheduleWithFixedDelay(new Runnable() {
                                private int num = 0;

                                @Override
                                public void run() {
                                    if (onOffCoordinateSub && num++ < coordinate.clickNumber && currentActivity.equals(coordinate.appActivity)) {
                                        click(coordinate.xPosition, coordinate.yPosition);
                                    } else {
                                        throw new RuntimeException();
                                    }
                                    if (num == 1) {
                                        coordinate.triggerCount += 1;
                                        coordinate.lastTriggerTime = System.currentTimeMillis();
                                        dataDao.updateCoordinate(coordinate);
                                        addLog("点击坐标：" + gson.toJson(coordinate));
                                    }
                                }
                            }, coordinate.clickDelay, coordinate.clickInterval <= 0 ? 10 : coordinate.clickInterval, TimeUnit.MILLISECONDS);
                        }
                    }
                }
                if (nodeInfoList.isEmpty()) {
                    break;
                }
                if (!onOffWidgetSub) {
                    break;
                }
                executorServiceMain.execute(new Runnable() {
                    @Override
                    public void run() {
                        findAndClickView(nodeInfoList, widgetSet);
                    }
                });
                break;
            }
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: {
                if (!TextUtils.equals(event.getPackageName(), currentPackageSub)) {
                    break;
                }
                AccessibilityNodeInfo source = event.getSource();
                if (source == null) {
                    break;
                }
                if (!onOffWidgetSub) {
                    break;
                }
                executorServiceMain.execute(new Runnable() {
                    @Override
                    public void run() {
                        findAndClickView(Collections.singletonList(source), widgetSet);
                    }
                });
                break;
            }
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (addDataBinding != null && viewClickPosition != null && widgetSelectBinding != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            aParams.x = (metrics.widthPixels - aParams.width) / 2;
            aParams.y = metrics.heightPixels - aParams.height;
            bParams.width = metrics.widthPixels;
            bParams.height = metrics.heightPixels;
            cParams.x = (metrics.widthPixels - cParams.width) / 2;
            cParams.y = (metrics.heightPixels - cParams.height) / 2;
            windowManager.updateViewLayout(addDataBinding.getRoot(), aParams);
            windowManager.updateViewLayout(viewClickPosition, cParams);
            widgetSelectBinding.frame.removeAllViews();
            TextView text = new TextView(service);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
            text.setGravity(Gravity.CENTER);
            text.setTextColor(0xffff0000);
            text.setText("请重新刷新布局");
            widgetSelectBinding.frame.addView(text, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            windowManager.updateViewLayout(widgetSelectBinding.frame, bParams);
        }
        if (dbClickView != null) {
            Rect rect = MyUtils.getDbClickPosition();
            dbClickLp.x = rect.left;
            dbClickLp.y = rect.top;
            dbClickLp.width = rect.width();
            dbClickLp.height = rect.height();
            windowManager.updateViewLayout(dbClickView, dbClickLp);
        }
    }

    public boolean onUnbind(Intent intent) {
        service.unregisterReceiver(myBroadcastReceiver);
        return true;
    }

    /**
     * 将数据暴露给其他组件
     * 方便修改
     */
    public Map<String, AppDescribe> getAppDescribeMap() {
        return appDescribeMap;
    }

    /**
     * 查找并点击View
     */
    private void findAndClickView(List<AccessibilityNodeInfo> nodeInfoList, Set<Widget> widgets) {
        LinkedList<AccessibilityNodeInfo> list = new LinkedList<>(nodeInfoList);
        while (!list.isEmpty() && onOffWidgetSub) {
            AccessibilityNodeInfo nodeInfo = list.poll();
            if (nodeInfo != null) {
                clickByWidget(nodeInfo, widgets);
                for (int n = 0; n < nodeInfo.getChildCount(); n++) {
                    AccessibilityNodeInfo node = nodeInfo.getChild(n);
                    if (node.isVisibleToUser()) {
                        list.add(node);
                    }
                }
            }
        }
    }

    private void clickByWidget(AccessibilityNodeInfo nodeInfo, Set<Widget> widgets) {
        Rect rect = new Rect();
        nodeInfo.getBoundsInScreen(rect);
        Long nodeId = nodeInfo.getSourceNodeId();
        String viewId = StrUtil.emptyToNull(nodeInfo.getViewIdResourceName());
        String describe = StrUtil.emptyToNull(nodeInfo.getContentDescription());
        String text = StrUtil.emptyToNull(nodeInfo.getText());
        for (Widget e : widgets) {
            if (e.condition == Widget.CONDITION_OR) {
                if (rect.equals(e.widgetRect)) {
                    e.triggerReason = "Bonus 匹配";
                    addLog(String.format("找到控件：Bonus[%s]", gsonNoPretty.toJson(e.widgetRect)));
                } else if (nodeId != null && nodeId.equals(e.widgetNodeId)) {
                    e.triggerReason = "NodeId 匹配";
                    addLog(String.format("找到控件：NodeId[%s]", e.widgetNodeId));
                } else if (viewId != null && !e.widgetViewId.isEmpty() && viewId.equals(e.widgetViewId)) {
                    e.triggerReason = "ViewId 匹配";
                    addLog(String.format("找到控件：ViewId[%s]", e.widgetViewId));
                } else if (describe != null && !e.widgetDescribe.isEmpty() && describe.matches(e.widgetDescribe)) {
                    e.triggerReason = "Describe 匹配";
                    addLog(String.format("找到控件：Describe[%s]", e.widgetDescribe));
                } else if (text != null && !e.widgetText.isEmpty() && text.matches(e.widgetText)) {
                    e.triggerReason = "Text 匹配";
                    addLog(String.format("找到控件：Text[%s]", e.widgetText));
                } else {
                    continue;
                }
            } else if (e.condition == Widget.CONDITION_AND) {
                StringBuilder strBuildTrigger = new StringBuilder();
                StringBuilder strBuildLog = new StringBuilder();
                if (e.widgetRect != null) {
                    if (rect.equals(e.widgetRect)) {
                        strBuildTrigger.append(", Bonus");
                        strBuildLog.append(String.format(", Bonus[%s]", gsonNoPretty.toJson(e.widgetRect)));
                    } else {
                        continue;
                    }
                }
                if (e.widgetNodeId != null) {
                    if (nodeId != null && nodeId.equals(e.widgetNodeId)) {
                        strBuildTrigger.append(", NodeId");
                        strBuildLog.append(String.format(", NodeId[%s]", e.widgetNodeId));
                    } else {
                        continue;
                    }
                }
                if (!e.widgetViewId.isEmpty()) {
                    if (viewId != null && viewId.equals(e.widgetViewId)) {
                        strBuildTrigger.append(", ViewId");
                        strBuildLog.append(String.format(", ViewId[%s]", e.widgetViewId));
                    } else {
                        continue;
                    }
                }
                if (!e.widgetDescribe.isEmpty()) {
                    if (describe != null && describe.matches(e.widgetDescribe)) {
                        strBuildTrigger.append(", Describe");
                        strBuildLog.append(String.format(", Describe[%s]", e.widgetDescribe));
                    } else {
                        continue;
                    }
                }
                if (!e.widgetText.isEmpty()) {
                    if (text != null && text.matches(e.widgetText)) {
                        strBuildTrigger.append(", Text");
                        strBuildLog.append(String.format(", Text[%s]", e.widgetText));
                    } else {
                        continue;
                    }
                }
                if (e.widgetRect == null
                        && e.widgetNodeId == null
                        && e.widgetViewId.isEmpty()
                        && e.widgetDescribe.isEmpty()
                        && e.widgetText.isEmpty()) {
                    continue;
                }
                e.triggerReason = strBuildTrigger.append(" 匹配").substring(2);
                addLog(String.format("找到控件：%s", strBuildLog.substring(2)));
            } else {
                continue;
            }
            if (e.action == Widget.ACTION_CLICK) {
                if (!e.noRepeat || alreadyClickSet.add(e)) {
                    if (!debounceSet.add(e)) {
                        return;
                    }
                    executorServiceSub.schedule(new Runnable() {
                        @Override
                        public void run() {
                            debounceSet.remove(e);
                        }
                    }, e.clickDelay + e.debounceDelay, TimeUnit.MILLISECONDS);

                    executorServiceSub.scheduleWithFixedDelay(new Runnable() {
                        private int clickNumber = 0;

                        @Override
                        public void run() {
                            if (!onOffWidgetSub
                                    || !currentActivity.equals(e.appActivity)
                                    || !nodeInfo.refresh()) {
                                throw new RuntimeException();
                            }
                            if (clickNumber++ >= e.clickNumber) {
                                throw new RuntimeException();
                            }
                            if (e.clickOnly) {
                                click(rect.centerX(), rect.centerY());
                            } else if (!nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                click(rect.centerX(), rect.centerY());
                            }
                            if (clickNumber == 1) {
                                e.triggerCount += 1;
                                e.lastTriggerTime = System.currentTimeMillis();
                                dataDao.updateWidget(e);
                                addLog("点击控件：" + gson.toJson(e));
                                if (alreadyClickSet.size() >= widgets.size()) {
                                    serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                    service.setServiceInfo(serviceInfo);
                                }
                            }
                        }
                    }, e.clickDelay, e.clickInterval <= 0 ? 10 : e.clickInterval, TimeUnit.MILLISECONDS);
                }
            } else if (e.action == Widget.ACTION_BACK) {
                if (alreadyClickSet.add(e)) {
                    if (onOffWidgetSub
                            && currentActivity.equals(e.appActivity)
                            && nodeInfo.refresh()) {
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                        e.triggerCount += 1;
                        e.lastTriggerTime = System.currentTimeMillis();
                        dataDao.updateWidget(e);
                        addLog("执行返回：" + gson.toJson(e));
                        if (alreadyClickSet.size() >= widgets.size()) {
                            serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                            service.setServiceInfo(serviceInfo);
                        }
                    }
                }
            }
            break;
        }
    }

    /**
     * 查找所有
     * 的控件
     */
    private List<AccessibilityNodeInfo> findAllNode(List<AccessibilityNodeInfo> root) {
        LinkedList<AccessibilityNodeInfo> listA = new LinkedList<>(root);
        HashSet<AccessibilityNodeInfo> setR = new HashSet<>();
        while (!listA.isEmpty()) {
            AccessibilityNodeInfo nodeInfo = listA.poll();
            if (nodeInfo != null) {
                setR.add(nodeInfo);
                for (int n = 0; n < nodeInfo.getChildCount(); n++) {
                    listA.add(nodeInfo.getChild(n));
                }
            }
        }
        return setR.stream().sorted(new Comparator<AccessibilityNodeInfo>() {
            @Override
            public int compare(AccessibilityNodeInfo a, AccessibilityNodeInfo b) {
                Rect rectA = new Rect();
                Rect rectB = new Rect();
                a.getBoundsInScreen(rectA);
                b.getBoundsInScreen(rectB);
                return rectB.width() * rectB.height() - rectA.width() * rectA.height();
            }
        }).collect(Collectors.toList());
    }

    /**
     * 模拟
     * 点击
     */
    private boolean click(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
        return service.dispatchGesture(builder.build(), null, null);
    }

    /**
     * 开启无障碍服务时调用
     * 获取运行时需要的数据
     */
    private void getRunningData() {
        List<AppDescribe> appDescribeList = dataDao.getAllAppDescribes();
        for (AppDescribe describe : appDescribeList) {
            describe.getOtherFieldsFromDatabase(dataDao);
            appDescribeMap.put(describe.appPackage, describe);
        }
    }

    /**
     * 创建规则时调用
     */
    @SuppressLint("ClickableViewAccessibility")
    public void showAddDataWindow(boolean capture) {
        if (pkgSuggestNotOnList == null) {
            Set<String> pkgSysSet = packageManager
                    .getInstalledPackages(PackageManager.MATCH_SYSTEM_ONLY)
                    .stream().map(e -> e.packageName)
                    .collect(Collectors.toSet());
            Set<String> pkgInputMethodSet = inputMethodManager
                    .getInputMethodList()
                    .stream()
                    .map(InputMethodInfo::getPackageName)
                    .collect(Collectors.toSet());
            Set<String> pkgHasHomeSet = packageManager
                    .queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
                    .stream()
                    .map(e -> e.activityInfo.packageName)
                    .collect(Collectors.toSet());
            pkgSuggestNotOnList = new HashSet<>();
            pkgSuggestNotOnList.addAll(pkgSysSet);
            pkgSuggestNotOnList.addAll(pkgInputMethodSet);
            pkgSuggestNotOnList.addAll(pkgHasHomeSet);
        }
        if (viewClickPosition != null || addDataBinding != null || widgetSelectBinding != null) {
            return;
        }
        final Widget widgetSelect = new Widget();
        final Coordinate coordinateSelect = new Coordinate();
        final LayoutInflater inflater = LayoutInflater.from(service);

        addDataBinding = ViewAddDataBinding.inflate(inflater);
        widgetSelectBinding = ViewWidgetSelectBinding.inflate(inflater);

        viewClickPosition = new ImageView(service);
        viewClickPosition.setImageResource(R.drawable.p);

        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int width = Math.min(metrics.heightPixels, metrics.widthPixels);
        int height = Math.max(metrics.heightPixels, metrics.widthPixels);

        aParams = new WindowManager.LayoutParams();
        aParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        aParams.format = PixelFormat.TRANSPARENT;
        aParams.gravity = Gravity.START | Gravity.TOP;
        aParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        aParams.width = width;
        aParams.height = height / 5;
        aParams.x = (metrics.widthPixels - aParams.width) / 2;
        aParams.y = metrics.heightPixels / 5 * 3;
        aParams.alpha = 0.9f;

        bParams = new WindowManager.LayoutParams();
        bParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        bParams.format = PixelFormat.TRANSPARENT;
        bParams.gravity = Gravity.START | Gravity.TOP;
        bParams.width = metrics.widthPixels;
        bParams.height = metrics.heightPixels;
        bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        bParams.alpha = 0f;

        cParams = new WindowManager.LayoutParams();
        cParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        cParams.format = PixelFormat.TRANSPARENT;
        cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        cParams.gravity = Gravity.START | Gravity.TOP;
        cParams.width = cParams.height = width / 4;
        cParams.x = (metrics.widthPixels - cParams.width) / 2;
        cParams.y = (metrics.heightPixels - cParams.height) / 2;
        cParams.alpha = 0f;

        addDataBinding.getRoot().setOnTouchListener(new View.OnTouchListener() {
            int startRowX = 0, startRowY = 0, startLpX = 0, startLpY = 0;
            int preRowX = 0, preRowY = 0;
            long preEventTime = 0;
            boolean openPageFlag = false;
            final Pattern pattern = Pattern.compile("[A-Za-z0-9_.]+");

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                v.post(new Runnable() {
                    final int action = event.getAction();
                    final int rowX = Math.round(event.getRawX());
                    final int rowY = Math.round(event.getRawY());

                    @Override
                    public void run() {
                        if (addDataBinding == null) {
                            return;
                        }
                        switch (action) {
                            case MotionEvent.ACTION_DOWN:
                                startRowX = rowX;
                                startRowY = rowY;
                                startLpX = aParams.x;
                                startLpY = aParams.y;
                                break;
                            case MotionEvent.ACTION_MOVE:
                                aParams.x = startLpX + (rowX - startRowX);
                                aParams.y = startLpY + (rowY - startRowY);
                                windowManager.updateViewLayout(v, aParams);
                                break;
                            case MotionEvent.ACTION_UP:
                                DisplayMetrics metrics = new DisplayMetrics();
                                windowManager.getDefaultDisplay().getRealMetrics(metrics);
                                aParams.x = Math.max(aParams.x, 0);
                                aParams.x = Math.min(aParams.x, metrics.widthPixels - aParams.width);
                                aParams.y = Math.max(aParams.y, 0);
                                aParams.y = Math.min(aParams.y, metrics.heightPixels - aParams.height);
                                windowManager.updateViewLayout(v, aParams);
                                break;
                        }
                    }
                });
                // 双击打开规则管理页面
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (Math.abs(event.getEventTime() - preEventTime) < 500) {
                        if (!openPageFlag && Math.abs(event.getRawX() - preRowX) < 100 && Math.abs(event.getRawY() - preRowY) < 100) {
                            Matcher matcher = pattern.matcher(addDataBinding.pkgName.getText().toString());
                            if (matcher.find()) {
                                if (appDescribeMap.containsKey(matcher.group())) {
                                    Intent intent = new Intent(service, EditDataActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                    intent.putExtra("packageName", matcher.group());
                                    service.startActivity(intent);
                                } else {
                                    Intent intent = new Intent(service, ListDataActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                    service.startActivity(intent);
                                }
                                if (bParams.alpha != 0) {
                                    addDataBinding.switchWid.callOnClick();
                                }
                            }
                            openPageFlag = true;
                        }
                    } else {
                        openPageFlag = false;
                    }
                    preRowX = Math.round(event.getRawX());
                    preRowY = Math.round(event.getRawY());
                    preEventTime = event.getEventTime();
                }
                return true;
            }
        });
        viewClickPosition.setOnTouchListener(new View.OnTouchListener() {
            int startRowX = 0, startRowY = 0, startLpX = 0, startLpY = 0;
            final int width = cParams.width / 2;
            final int height = cParams.height / 2;
            final Pattern pattern = Pattern.compile("[A-Za-z0-9_.]+");

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                v.post(new Runnable() {
                    final int action = event.getAction();
                    final int rowX = Math.round(event.getRawX());
                    final int rowY = Math.round(event.getRawY());

                    @Override
                    public void run() {
                        if (viewClickPosition == null) {
                            return;
                        }
                        switch (action) {
                            case MotionEvent.ACTION_DOWN:
                                cParams.alpha = 0.9f;
                                windowManager.updateViewLayout(v, cParams);
                                startRowX = rowX;
                                startRowY = rowY;
                                startLpX = cParams.x;
                                startLpY = cParams.y;
                                break;
                            case MotionEvent.ACTION_MOVE:
                                cParams.x = startLpX + (rowX - startRowX);
                                cParams.y = startLpY + (rowY - startRowY);
                                windowManager.updateViewLayout(v, cParams);
                                coordinateSelect.appPackage = currentPackage;
                                coordinateSelect.appActivity = currentActivity;
                                coordinateSelect.xPosition = cParams.x + width;
                                coordinateSelect.yPosition = cParams.y + height;
                                addDataBinding.pkgName.setText(coordinateSelect.appPackage);
                                addDataBinding.actName.setText(coordinateSelect.appActivity);
                                addDataBinding.saveAim.setEnabled(pattern.matcher(coordinateSelect.appPackage).matches());
                                addDataBinding.xy.setText("X轴：" + String.format("%-4d", coordinateSelect.xPosition) + "    " + "Y轴：" + String.format("%-4d", coordinateSelect.yPosition));
                                break;
                            case MotionEvent.ACTION_UP:
                                cParams.alpha = 0.5f;
                                windowManager.updateViewLayout(v, cParams);
                                break;
                        }
                    }
                });
                return true;
            }
        });
        addDataBinding.switchWid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bParams.alpha == 0) {
                    executorServiceMain.execute(new Runnable() {
                        @Override
                        public void run() {
                            AccessibilityNodeInfo root = service.getRootInActiveWindow();
                            if (root == null) {
                                return;
                            }
                            List<AccessibilityWindowInfo> windowInfoList = service.getWindows();
                            Collections.reverse(windowInfoList);
                            if (windowInfoList.isEmpty()) {
                                return;
                            }
                            ArrayList<AccessibilityNodeInfo> nodeList = new ArrayList<>();
                            for (AccessibilityWindowInfo windowInfo : windowInfoList) {
                                AccessibilityNodeInfo nodeInfo = windowInfo.getRoot();
                                if (TextUtils.equals(nodeInfo.getPackageName(), root.getPackageName())) {
                                    nodeList.addAll(findAllNode(Collections.singletonList(nodeInfo)));
                                }
                            }
                            if (nodeList.isEmpty()) {
                                return;
                            }
                            v.post(new Runnable() {
                                @Override
                                public void run() {
                                    View.OnClickListener onClickListener = new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            v.requestFocus();
                                        }
                                    };
                                    View.OnFocusChangeListener onFocusChangeListener = new View.OnFocusChangeListener() {
                                        final Pattern pattern = Pattern.compile("[A-Za-z0-9_.]+");

                                        @Override
                                        public void onFocusChange(View v, boolean hasFocus) {
                                            if (hasFocus) {
                                                AccessibilityNodeInfo nodeInfo = (AccessibilityNodeInfo) v.getTag(R.string.nodeInfo);
                                                widgetSelect.widgetClickable = nodeInfo.isClickable();
                                                widgetSelect.widgetRect = (Rect) v.getTag(R.string.rect);
                                                widgetSelect.widgetNodeId = nodeInfo.getSourceNodeId();
                                                widgetSelect.widgetViewId = StrUtil.toStringOrEmpty(nodeInfo.getViewIdResourceName());
                                                widgetSelect.widgetDescribe = StrUtil.toStringOrEmpty(nodeInfo.getContentDescription());
                                                widgetSelect.widgetText = StrUtil.toStringOrEmpty(nodeInfo.getText());
                                                addDataBinding.pkgName.setText(widgetSelect.appPackage);
                                                addDataBinding.actName.setText(widgetSelect.appActivity);
                                                addDataBinding.saveWid.setEnabled(pattern.matcher(widgetSelect.appPackage).matches());
                                                String clickable = "clickable:" + widgetSelect.widgetClickable;
                                                String nodeId = "nodeId:" + widgetSelect.widgetNodeId;
                                                String viewId = widgetSelect.widgetViewId.isEmpty() ? "" : widgetSelect.widgetViewId.contains(":id/") ? "viewId:" + widgetSelect.widgetViewId.substring(widgetSelect.widgetViewId.indexOf(":id/") + 4) : "";
                                                String desc = widgetSelect.widgetDescribe.isEmpty() ? "" : "describe:" + widgetSelect.widgetDescribe;
                                                String text = widgetSelect.widgetText.isEmpty() ? "" : "text:" + widgetSelect.widgetText;
                                                addDataBinding.widget.setText(clickable + " " + nodeId + (viewId.isEmpty() ? "" : " " + viewId) + (desc.isEmpty() ? "" : " " + desc) + (text.isEmpty() ? "" : " " + text));
                                                v.setBackgroundResource(R.drawable.node_focus);
                                            } else {
                                                v.setBackgroundResource(R.drawable.node);
                                            }
                                        }
                                    };
                                    for (AccessibilityNodeInfo nodeInfo : nodeList) {
                                        Rect rect = new Rect();
                                        nodeInfo.getBoundsInScreen(rect);
                                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(rect.width(), rect.height());
                                        params.leftMargin = rect.left;
                                        params.topMargin = rect.top;
                                        View view = new View(service);
                                        view.setBackgroundResource(R.drawable.node);
                                        view.setFocusableInTouchMode(true);
                                        view.setFocusable(true);
                                        view.setOnClickListener(onClickListener);
                                        view.setOnFocusChangeListener(onFocusChangeListener);
                                        view.setTag(R.string.nodeInfo, nodeInfo);
                                        view.setTag(R.string.rect, rect);
                                        widgetSelectBinding.frame.addView(view, params);
                                    }
                                    bParams.alpha = 0.5f;
                                    bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                                    windowManager.updateViewLayout(widgetSelectBinding.getRoot(), bParams);
                                    widgetSelect.appPackage = currentPackage;
                                    widgetSelect.appActivity = currentActivity;
                                    addDataBinding.pkgName.setText(widgetSelect.appPackage);
                                    addDataBinding.actName.setText(widgetSelect.appActivity);
                                    addDataBinding.switchWid.setText("隐藏布局");
                                }
                            });
                        }
                    });
                } else {
                    bParams.alpha = 0f;
                    bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(widgetSelectBinding.getRoot(), bParams);
                    addDataBinding.saveWid.setEnabled(false);
                    widgetSelectBinding.frame.removeAllViews();
                    addDataBinding.switchWid.setText("显示布局");
                }
            }
        });
        addDataBinding.switchAim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Button button = (Button) v;
                if (cParams.alpha == 0) {
                    coordinateSelect.appPackage = currentPackage;
                    coordinateSelect.appActivity = currentActivity;
                    cParams.alpha = 0.5f;
                    cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    windowManager.updateViewLayout(viewClickPosition, cParams);
                    addDataBinding.pkgName.setText(coordinateSelect.appPackage);
                    addDataBinding.actName.setText(coordinateSelect.appActivity);
                    button.setText("隐藏准星");
                } else {
                    cParams.alpha = 0f;
                    cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(viewClickPosition, cParams);
                    addDataBinding.saveAim.setEnabled(false);
                    button.setText("显示准星");
                }
            }
        });
        addDataBinding.saveWid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prePackage = currentPackage;
                preActivity = currentActivity;
                Runnable runnable = new Runnable() {
                    AppDescribe appDescribeTemp;

                    @Override
                    public void run() {
                        Map<String, Boolean> propsMap = new LinkedHashMap<>();
                        if (Objects.nonNull(widgetSelect.widgetRect)) {
                            propsMap.put("Bonus", true);
                        }
                        if (Objects.nonNull(widgetSelect.widgetNodeId)) {
                            propsMap.put("NodeId", false);
                        }
                        if (StrUtil.isNotBlank(widgetSelect.widgetViewId)) {
                            propsMap.put("ViewId", true);
                        }
                        if (StrUtil.isNotBlank(widgetSelect.widgetDescribe)) {
                            propsMap.put("Describe", true);
                        }
                        if (StrUtil.isNotBlank(widgetSelect.widgetText)) {
                            propsMap.put("Text", true);
                        }
                        int index = 0;
                        String[] keys = new String[propsMap.size()];
                        boolean[] values = new boolean[propsMap.size()];
                        for (Map.Entry<String, Boolean> entry : propsMap.entrySet()) {
                            keys[index] = entry.getKey();
                            values[index] = entry.getValue();
                            index++;
                        }
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(service);
                        alertDialogBuilder.setTitle("请选择需要保存的属性");
                        alertDialogBuilder.setMultiChoiceItems(keys, values, new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                propsMap.put(keys[which], isChecked);
                                values[which] = isChecked;
                            }
                        });
                        alertDialogBuilder.setNegativeButton("取消", null);
                        alertDialogBuilder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                appDescribeTemp = appDescribeMap.get(widgetSelect.appPackage);
                                if (appDescribeTemp == null) {
                                    appDescribeTemp = new AppDescribe();
                                    appDescribeTemp.appPackage = widgetSelect.appPackage;
                                    try {
                                        PackageInfo packageInfo = packageManager.getPackageInfo(widgetSelect.appPackage, PackageManager.GET_META_DATA);
                                        appDescribeTemp.appName = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString();
                                    } catch (PackageManager.NameNotFoundException e) {
                                        // e.printStackTrace();
                                    }
                                    appDescribeTemp.id = dataDao.insertAppDescribe(appDescribeTemp);
                                    appDescribeMap.put(appDescribeTemp.appPackage, appDescribeTemp);
                                }
                                Widget temWidget = new Widget(widgetSelect);
                                temWidget.createTime = System.currentTimeMillis();
                                temWidget.widgetRect = Boolean.TRUE.equals(propsMap.get("Bonus")) ? temWidget.widgetRect : null;
                                temWidget.widgetNodeId = Boolean.TRUE.equals(propsMap.get("NodeId")) ? temWidget.widgetNodeId : null;
                                temWidget.widgetViewId = Boolean.TRUE.equals(propsMap.get("ViewId")) ? temWidget.widgetViewId : "";
                                temWidget.widgetDescribe = Boolean.TRUE.equals(propsMap.get("Describe")) ? temWidget.widgetDescribe : "";
                                temWidget.widgetText = Boolean.TRUE.equals(propsMap.get("Text")) ? temWidget.widgetText : "";
                                dataDao.insertWidget(temWidget);
                                addDataBinding.saveWid.setEnabled(false);
                                addDataBinding.pkgName.setText(widgetSelect.appPackage + " (以下控件数据已保存)");
                                appDescribeTemp.getWidgetFromDatabase(dataDao);
                                if (!appDescribeTemp.widgetOnOff) {
                                    showWarningDialog(new Runnable() {
                                        @Override
                                        public void run() {
                                            appDescribeTemp.widgetOnOff = true;
                                            dataDao.updateAppDescribe(appDescribeTemp);
                                        }
                                    }, service.getString(R.string.widgetOffWarning));
                                }
                            }
                        });
                        alertDialogBuilder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                currentPackage = prePackage;
                                currentActivity = preActivity;
                            }
                        });
                        Dialog dialog = alertDialogBuilder.create();
                        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                        dialog.show();
                    }
                };
                if (pkgSuggestNotOnList.contains(widgetSelect.appPackage)) {
                    showWarningDialog(runnable, service.getString(R.string.addWarning));
                } else {
                    runnable.run();
                }
            }
        });
        addDataBinding.saveAim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prePackage = currentPackage;
                preActivity = currentActivity;
                Runnable runnable = new Runnable() {
                    AppDescribe appDescribeTemp;

                    @Override
                    public void run() {
                        appDescribeTemp = appDescribeMap.get(coordinateSelect.appPackage);
                        if (appDescribeTemp == null) {
                            appDescribeTemp = new AppDescribe();
                            appDescribeTemp.appPackage = widgetSelect.appPackage;
                            try {
                                PackageInfo packageInfo = packageManager.getPackageInfo(widgetSelect.appPackage, PackageManager.GET_META_DATA);
                                appDescribeTemp.appName = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString();
                            } catch (PackageManager.NameNotFoundException e) {
                                // e.printStackTrace();
                            }
                            appDescribeTemp.id = dataDao.insertAppDescribe(appDescribeTemp);
                            appDescribeMap.put(appDescribeTemp.appPackage, appDescribeTemp);
                        }
                        Coordinate temCoordinate = new Coordinate(coordinateSelect);
                        temCoordinate.createTime = System.currentTimeMillis();
                        dataDao.insertCoordinate(temCoordinate);
                        addDataBinding.saveAim.setEnabled(false);
                        addDataBinding.pkgName.setText(coordinateSelect.appPackage + " (以下坐标数据已保存)");
                        appDescribeTemp.getCoordinateFromDatabase(dataDao);
                        if (!appDescribeTemp.coordinateOnOff) {
                            showWarningDialog(new Runnable() {
                                @Override
                                public void run() {
                                    appDescribeTemp.coordinateOnOff = true;
                                    dataDao.updateAppDescribe(appDescribeTemp);
                                }
                            }, service.getString(R.string.coordinateOffWarning));
                        }
                    }
                };
                if (pkgSuggestNotOnList.contains(coordinateSelect.appPackage)) {
                    showWarningDialog(runnable, service.getString(R.string.addWarning));
                } else {
                    runnable.run();
                }
            }
        });
        addDataBinding.quit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                windowManager.removeViewImmediate(widgetSelectBinding.getRoot());
                windowManager.removeViewImmediate(addDataBinding.getRoot());
                windowManager.removeViewImmediate(viewClickPosition);
                pkgSuggestNotOnList = null;
                widgetSelectBinding = null;
                addDataBinding = null;
                viewClickPosition = null;
            }
        });
        windowManager.addView(widgetSelectBinding.getRoot(), bParams);
        windowManager.addView(addDataBinding.getRoot(), aParams);
        windowManager.addView(viewClickPosition, cParams);

        if (capture) {
            addDataBinding.switchWid.callOnClick();
        }
    }

    private void showWarningDialog(Runnable onSureRun, String message) {
        ViewDialogWarningBinding binding = ViewDialogWarningBinding.inflate(LayoutInflater.from(service));
        binding.message.setText(message);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(service);
        alertDialogBuilder.setView(binding.getRoot());
        alertDialogBuilder.setNegativeButton("取消", null);
        alertDialogBuilder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onSureRun.run();
            }
        });
        alertDialogBuilder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                currentPackage = prePackage;
                currentActivity = preActivity;
            }
        });
        AlertDialog dialog = alertDialogBuilder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        dialog.show();
    }

    public void keepAliveByNotification(boolean enable) {
        if (enable) {
            NotificationManager notificationManager = service.getSystemService(NotificationManager.class);
            Intent intent = new Intent(service, MainActivity.class);
            Notification.Builder builder = new Notification.Builder(service);
            builder.setOngoing(true);
            builder.setAutoCancel(false);
            builder.setSmallIcon(R.drawable.app);
            builder.setContentTitle(service.getText(R.string.appName));
            builder.setContentText("运行中");
            builder.setContentIntent(PendingIntent.getActivity(service, 0x01, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(service.getPackageName());
                NotificationChannel channel = new NotificationChannel(service.getPackageName(), service.getString(R.string.appName), NotificationManager.IMPORTANCE_HIGH);
                notificationManager.createNotificationChannel(channel);
            }
            service.startForeground(0x01, builder.build());
        } else {
            service.stopForeground(true);
        }
    }

    public void keepAliveByFloatingWindow(boolean enable) {
        if (enable && ignoreView == null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            lp.gravity = Gravity.START | Gravity.TOP;
            lp.format = PixelFormat.TRANSPARENT;
            lp.alpha = 0;
            lp.width = 0;
            lp.height = 0;
            lp.x = 0;
            lp.y = 0;
            ignoreView = new View(service);
            ignoreView.setBackgroundColor(Color.TRANSPARENT);
            windowManager.addView(ignoreView, lp);
        } else if (ignoreView != null) {
            windowManager.removeView(ignoreView);
            ignoreView = null;
        }
    }

    public void showDbClickFloating(boolean enable) {
        if (enable && dbClickView == null) {
            dbClickLp = new WindowManager.LayoutParams();
            dbClickLp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            dbClickLp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            dbClickLp.gravity = Gravity.START | Gravity.TOP;
            dbClickLp.format = PixelFormat.TRANSPARENT;
            dbClickLp.alpha = 0.5f;
            Rect rect = MyUtils.getDbClickPosition();
            dbClickLp.x = rect.left;
            dbClickLp.y = rect.top;
            dbClickLp.width = rect.width();
            dbClickLp.height = rect.height();

            dbClickView = new View(service);
            dbClickView.setBackgroundColor(Color.TRANSPARENT);
            dbClickView.setOnClickListener(new View.OnClickListener() {
                private long previousTime = 0;

                @Override
                public void onClick(View v) {
                    long currentTime = System.currentTimeMillis();
                    long interval = currentTime - previousTime;
                    previousTime = currentTime;
                    if (interval <= 1000) {
                        showAddDataWindow(true);
                    }
                }
            });
            windowManager.addView(dbClickView, dbClickLp);
        } else if (dbClickView != null) {
            windowManager.removeView(dbClickView);
            dbClickView = null;
        }
    }

    public void showDbClickSetting() {
        if (dbClickView == null || dbClickLp == null) {
            return;
        }
        ViewDbClickSettingBinding dbClickSettingBinding = ViewDbClickSettingBinding.inflate(LayoutInflater.from(service));
        AlertDialog alertDialog = new AlertDialog.Builder(service).setView(dbClickSettingBinding.getRoot()).create();
        alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        alertDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                dbClickView.setBackgroundColor(Color.TRANSPARENT);
                windowManager.updateViewLayout(dbClickView, dbClickLp);
            }
        });
        alertDialog.show();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        WindowManager.LayoutParams lp = alertDialog.getWindow().getAttributes();
        lp.width = displayMetrics.widthPixels / 5 * 4;
        lp.height = displayMetrics.heightPixels / 3;
        alertDialog.onWindowAttributesChanged(lp);

        dbClickSettingBinding.seekBarW.setMax(displayMetrics.widthPixels / 2);
        dbClickSettingBinding.seekBarH.setMax(displayMetrics.heightPixels / 4);
        dbClickSettingBinding.seekBarX.setMax(displayMetrics.widthPixels);
        dbClickSettingBinding.seekBarY.setMax(displayMetrics.heightPixels);
        dbClickSettingBinding.seekBarW.setProgress(dbClickLp.width);
        dbClickSettingBinding.seekBarH.setProgress(dbClickLp.height);
        dbClickSettingBinding.seekBarX.setProgress(dbClickLp.x);
        dbClickSettingBinding.seekBarY.setProgress(dbClickLp.y);
        SeekBar.OnSeekBarChangeListener onSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (seekBar == dbClickSettingBinding.seekBarW) {
                    dbClickLp.width = i;
                }
                if (seekBar == dbClickSettingBinding.seekBarH) {
                    dbClickLp.height = i;
                }
                if (seekBar == dbClickSettingBinding.seekBarX) {
                    dbClickLp.x = i;
                }
                if (seekBar == dbClickSettingBinding.seekBarY) {
                    dbClickLp.y = i;
                }
                windowManager.updateViewLayout(dbClickView, dbClickLp);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Rect rect = new Rect();
                rect.left = dbClickLp.x;
                rect.top = dbClickLp.y;
                rect.right = dbClickLp.x + dbClickLp.width;
                rect.bottom = dbClickLp.y + dbClickLp.height;
                MyUtils.setDbClickPosition(rect);
            }
        };
        dbClickSettingBinding.seekBarW.setOnSeekBarChangeListener(onSeekBarChangeListener);
        dbClickSettingBinding.seekBarH.setOnSeekBarChangeListener(onSeekBarChangeListener);
        dbClickSettingBinding.seekBarX.setOnSeekBarChangeListener(onSeekBarChangeListener);
        dbClickSettingBinding.seekBarY.setOnSeekBarChangeListener(onSeekBarChangeListener);
        dbClickView.setBackgroundColor(Color.RED);
        windowManager.updateViewLayout(dbClickView, dbClickLp);
    }

    private void addLog(String log) {
        logList.add(simpleDateFormat.format(new Date()) + " " + log);
        if (logList.size() > 1000) {
            logList.poll();
        }
    }

    public String getLog() {
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : logList) {
            stringBuilder.append(s).append("\n");
        }
        return stringBuilder.toString();
    }

    public class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), Intent.ACTION_SCREEN_OFF)) {
                currentPackageSub = StrUtil.EMPTY;
                currentActivity = StrUtil.EMPTY;
            }
        }
    }
}
