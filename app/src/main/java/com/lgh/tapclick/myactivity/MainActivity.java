package com.lgh.tapclick.myactivity;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lgh.tapclick.BuildConfig;
import com.lgh.tapclick.R;
import com.lgh.tapclick.databinding.ActivityMainBinding;
import com.lgh.tapclick.databinding.ViewAccessibilityStatementBinding;
import com.lgh.tapclick.databinding.ViewMainItemBinding;
import com.lgh.tapclick.databinding.ViewNewRuleBinding;
import com.lgh.tapclick.databinding.ViewPrivacyAgreementBinding;
import com.lgh.tapclick.mybean.AppDescribe;
import com.lgh.tapclick.mybean.CoordinateShare;
import com.lgh.tapclick.mybean.LatestMessage;
import com.lgh.tapclick.mybean.MyAppConfig;
import com.lgh.tapclick.mybean.Regulation;
import com.lgh.tapclick.mybean.RegulationExport;
import com.lgh.tapclick.mybean.WidgetShare;
import com.lgh.tapclick.myclass.DataDao;
import com.lgh.tapclick.myclass.MyApplication;
import com.lgh.tapclick.myfunction.MyUtils;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MainActivity extends BaseActivity {

    private Context context;
    private DataDao dataDao;
    private Handler handler;
    private LayoutInflater inflater;
    private ActivityMainBinding mainBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        dataDao = MyApplication.dataDao;
        handler = new Handler(Looper.getMainLooper());
        mainBinding = ActivityMainBinding.inflate(inflater = getLayoutInflater());
        setContentView(mainBinding.getRoot());

        final List<Resource> source = new ArrayList<>();
        source.add(new Resource("授权管理", R.drawable.authorization));
        source.add(new Resource("创建规则", R.drawable.add_data));
        source.add(new Resource("规则管理", R.drawable.edit_data));
        source.add(new Resource("应用设置", R.drawable.setting));
        source.add(new Resource("使用说明", R.drawable.instructions));
        source.add(new Resource("运行日志", R.drawable.log));
        BaseAdapter baseAdapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return source.size();
            }

            @Override
            public Object getItem(int position) {
                return position;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ViewMainItemBinding itemBinding = ViewMainItemBinding.inflate(inflater);
                Resource resource = source.get(position);
                itemBinding.mainImg.setImageResource(resource.drawableId);
                itemBinding.mainName.setText(resource.name);
                return itemBinding.getRoot();
            }
        };
        mainBinding.mainListView.setAdapter(baseAdapter);
        mainBinding.mainListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: {
                        startActivity(new Intent(context, AuthorizationActivity.class));
                        break;
                    }
                    case 1: {
                        startActivity(new Intent(context, AddDataActivity.class));
                        break;
                    }
                    case 2: {
                        startActivity(new Intent(context, ListDataActivity.class));
                        break;
                    }
                    case 3: {
                        Intent intent = new Intent(context, SettingActivity.class);
                        startActivityForResult(intent, 0x01);
                        break;
                    }
                    case 4: {
                        startActivity(new Intent(context, MoreMessageActivity.class));
                        break;
                    }
                    case 5: {
                        startActivity(new Intent(context, LogActivity.class));
                        break;
                    }
                }
            }
        });

//        Settings.Secure.putString(getContentResolver(), Settings.Secure.ANDROID_ID, "11111");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH", Locale.getDefault());
        String forUpdate = dateFormat.format(new Date());
        MyAppConfig myAppConfig = dataDao.getMyAppConfig();
        if (!forUpdate.equals(myAppConfig.forUpdate)) {
            myAppConfig.forUpdate = forUpdate;
            dataDao.updateMyAppConfig(myAppConfig);
            showUpdateInfo();
        }
        if (myAppConfig.autoHideOnTaskList) {
            MyUtils.setExcludeFromRecents(true);
        }
        if (MyUtils.getIsFirstStart()) {
            showPrivacyAgreement();
        }
        handleImportRule(getIntent());
        // 触发允许读取应用列表授权弹窗
        getPackageManager().getInstalledPackages(PackageManager.GET_META_DATA);

        Log.i("LinGH", MyUtils.getMyDeviceNo());
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(new Runnable() {
            @Override
            public void run() {
                refreshAccessibilityServiceStatus();
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishAndRemoveTask();
    }

    private void refreshAccessibilityServiceStatus() {
        if (MyUtils.isServiceRunning()) {
            mainBinding.statusImg.setImageResource(R.drawable.ic_ok);
            mainBinding.statusTip.setText("无障碍服务已开启");
        } else {
            mainBinding.statusImg.setImageResource(R.drawable.ic_error);
            mainBinding.statusTip.setText("无障碍服务未开启");
        }
    }

    private void showUpdateInfo() {
        Observable<LatestMessage> observable = MyApplication.myHttpRequest.getLatestMessage();
        observable.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<LatestMessage>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
            }

            @Override
            public void onNext(@NonNull LatestMessage latestMessage) {
                try {
                    String appName = latestMessage.assets.get(0).name;
                    Matcher matcher = Pattern.compile("\\d+").matcher(appName);
                    if (matcher.find()) {
                        int newVersion = Integer.parseInt(matcher.group());
                        if (newVersion > BuildConfig.VERSION_CODE) {
                            Intent intent = new Intent(context, UpdateActivity.class);
                            intent.putExtra("updateMessage", latestMessage.body);
                            intent.putExtra("updateUrl", latestMessage.assets.get(0).browser_download_url);
                            if (getPackageManager().resolveActivity(intent, PackageManager.MATCH_ALL) != null) {
                                startActivity(intent);
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    // e.printStackTrace();
                }
            }

            @Override
            public void onError(@NonNull Throwable e) {
                // e.printStackTrace();
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void showPrivacyAgreement() {
        Observable<String> observable = MyApplication.myHttpRequest.getPrivacyAgreement();
        observable.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<String>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
            }

            @Override
            public void onNext(@NonNull String str) {
                ViewPrivacyAgreementBinding privacyAgreementBinding = ViewPrivacyAgreementBinding.inflate(getLayoutInflater());
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setCancelable(false).setView(privacyAgreementBinding.getRoot()).create();
                privacyAgreementBinding.content.setText(str);
                privacyAgreementBinding.sure.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                        showAccessibilityStatement();
                    }
                });
                privacyAgreementBinding.cancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                        finishAndRemoveTask();
                    }
                });
                Window window = alertDialog.getWindow();
                window.setBackgroundDrawableResource(R.drawable.add_data_background);
                alertDialog.show();
                WindowManager.LayoutParams lp = window.getAttributes();
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                lp.width = metrics.widthPixels / 5 * 4;
                lp.height = metrics.heightPixels / 5 * 2;
                window.setAttributes(lp);
            }

            @Override
            public void onError(@NonNull Throwable e) {
                onNext(getString(R.string.privacyAgreement));
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void showAccessibilityStatement() {
        Observable<String> observable = MyApplication.myHttpRequest.getAccessibilityStatement();
        observable.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<String>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
            }

            @Override
            public void onNext(@NonNull String str) {
                ViewAccessibilityStatementBinding accessibilityStatementBinding = ViewAccessibilityStatementBinding.inflate(getLayoutInflater());
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setCancelable(false).setView(accessibilityStatementBinding.getRoot()).create();
                accessibilityStatementBinding.content.setText(str);
                accessibilityStatementBinding.sure.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        MyUtils.setIsFirstStart(false);
                        alertDialog.dismiss();
                    }
                });
                accessibilityStatementBinding.cancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                        finishAndRemoveTask();
                    }
                });
                Window window = alertDialog.getWindow();
                window.setBackgroundDrawableResource(R.drawable.add_data_background);
                alertDialog.show();
                WindowManager.LayoutParams lp = window.getAttributes();
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                lp.width = metrics.widthPixels / 5 * 4;
                lp.height = metrics.heightPixels / 5 * 2;
                window.setAttributes(lp);
            }

            @Override
            public void onError(@NonNull Throwable e) {
                onNext(getString(R.string.accessibilityStatement));
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void handleImportRule(Intent intent) {
        try {
            String strRule = intent.getStringExtra(Intent.EXTRA_TEXT);

            if (TextUtils.isEmpty(strRule)) {
                Uri uri = intent.getData();
                if (uri == null) {
                    uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                }
                if (uri == null && intent.getClipData() != null) {
                    for (int n = 0; n < intent.getClipData().getItemCount(); n++) {
                        ClipData.Item item = intent.getClipData().getItemAt(n);
                        if (item.getUri() != null) {
                            uri = item.getUri();
                            break;
                        }
                    }
                }
                if (uri != null) {
                    StringBuilder stringBuilder = new StringBuilder();
                    Scanner scanner = new Scanner(getContentResolver().openInputStream(uri));
                    while (scanner.hasNextLine()) {
                        stringBuilder.append(scanner.nextLine());
                    }
                    strRule = stringBuilder.toString().trim();
                    scanner.close();
                }
            }

            if (TextUtils.isEmpty(strRule)) {
                return;
            }
            String regStr = "^\"(" + WidgetShare.class.getSimpleName() + "|" + CoordinateShare.class.getSimpleName() + "|" + RegulationExport.class.getSimpleName() + ")\"\\s*:\\s*(.+)$";
            Pattern pattern = Pattern.compile(regStr, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(strRule);
            if (!matcher.matches()) {
                Toast.makeText(context, "无效的规则", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.equals(matcher.group(1), WidgetShare.class.getSimpleName())) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                WidgetShare widgetShare = gson.fromJson(matcher.group(2), WidgetShare.class);
                PackageInfo packageInfo = null;
                String appName = "";
                try {
                    packageInfo = getPackageManager().getPackageInfo(widgetShare.widget.appPackage, PackageManager.GET_META_DATA);
                    appName = getPackageManager().getApplicationLabel(packageInfo.applicationInfo).toString();
                } catch (PackageManager.NameNotFoundException e) {
                    // e.printStackTrace();
                }
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("应用包名：").append(widgetShare.widget.appPackage).append(!appName.isEmpty() ? String.format("（%s）", appName) : "（无权限或未安装）").append("\n\n");
                stringBuilder.append("我的系统指纹：").append(Build.FINGERPRINT).append("\n");
                stringBuilder.append("他的系统指纹：").append(widgetShare.basicContent.fingerPrint).append("\n\n");
                DisplayMetrics displayMetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
                stringBuilder.append("我的手机屏幕：").append(displayMetrics).append("\n");
                stringBuilder.append("他的手机屏幕：").append(widgetShare.basicContent.displayMetrics).append("\n\n");
                stringBuilder.append("我的应用版本名：").append(packageInfo != null ? packageInfo.versionName : "无权限或未安装").append("\n");
                stringBuilder.append("他的应用版本名：").append(widgetShare.basicContent.versionName).append("\n\n");
                stringBuilder.append("我的应用版本号：").append(packageInfo != null ? packageInfo.versionCode : "无权限或未安装").append("\n");
                stringBuilder.append("他的应用版本号：").append(widgetShare.basicContent.versionCode).append("\n\n");
                stringBuilder.append("控件内容：").append(gson.toJson(widgetShare.widget));

                ViewNewRuleBinding newRuleBinding = ViewNewRuleBinding.inflate(getLayoutInflater());
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setCancelable(false).setView(newRuleBinding.getRoot()).create();
                newRuleBinding.sure.setTag(appName);
                newRuleBinding.content.setText(stringBuilder.toString());
                newRuleBinding.sure.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        widgetShare.widget.createTime = System.currentTimeMillis();
                        widgetShare.widget.lastTriggerTime = 0;
                        widgetShare.widget.triggerCount = 0;
                        widgetShare.widget.triggerReason = "";
                        dataDao.insertWidget(widgetShare.widget);
                        AppDescribe appDescribe = dataDao.getAppDescribeByPackage(widgetShare.widget.appPackage);
                        if (appDescribe == null) {
                            appDescribe = new AppDescribe();
                            appDescribe.appPackage = widgetShare.widget.appPackage;
                            appDescribe.appName = v.getTag().toString();
                            appDescribe.id = dataDao.insertAppDescribe(appDescribe);
                        }
                        appDescribe.widgetOnOff = true;
                        dataDao.updateAppDescribe(appDescribe);
                        MyUtils.requestUpdateAppDescribe(appDescribe.appPackage);
                        Intent intent = new Intent(context, EditDataActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        intent.putExtra("packageName", appDescribe.appPackage);
                        startActivity(intent);
                        Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show();
                        alertDialog.dismiss();
                    }
                });
                newRuleBinding.cancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                    }
                });
                Window window = alertDialog.getWindow();
                window.setBackgroundDrawableResource(R.drawable.add_data_background);
                alertDialog.show();
                WindowManager.LayoutParams lp = window.getAttributes();
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                lp.width = metrics.widthPixels / 5 * 4;
                lp.height = metrics.heightPixels / 2;
                window.setAttributes(lp);
            } else if (TextUtils.equals(matcher.group(1), CoordinateShare.class.getSimpleName())) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                CoordinateShare coordinateShare = gson.fromJson(matcher.group(2), CoordinateShare.class);
                PackageInfo packageInfo = null;
                String appName = "";
                try {
                    packageInfo = getPackageManager().getPackageInfo(coordinateShare.coordinate.appPackage, PackageManager.GET_META_DATA);
                    appName = getPackageManager().getApplicationLabel(packageInfo.applicationInfo).toString();
                } catch (PackageManager.NameNotFoundException e) {
                    // e.printStackTrace();
                }
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("应用包名：").append(coordinateShare.coordinate.appPackage).append(!appName.isEmpty() ? String.format("（%s）", appName) : "（无权限或未安装）").append("\n\n");
                stringBuilder.append("我的系统指纹：").append(Build.FINGERPRINT).append("\n");
                stringBuilder.append("他的系统指纹：").append(coordinateShare.basicContent.fingerPrint).append("\n\n");
                DisplayMetrics displayMetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
                stringBuilder.append("我的手机屏幕：").append(displayMetrics).append("\n");
                stringBuilder.append("他的手机屏幕：").append(coordinateShare.basicContent.displayMetrics).append("\n\n");
                stringBuilder.append("我的应用版本名：").append(packageInfo != null ? packageInfo.versionName : "无权限或未安装").append("\n");
                stringBuilder.append("他的应用版本名：").append(coordinateShare.basicContent.versionName).append("\n\n");
                stringBuilder.append("我的应用版本号：").append(packageInfo != null ? packageInfo.versionCode : "无权限或未安装").append("\n");
                stringBuilder.append("他的应用版本号：").append(coordinateShare.basicContent.versionCode).append("\n\n");
                stringBuilder.append("坐标内容：").append(gson.toJson(coordinateShare.coordinate));

                ViewNewRuleBinding newRuleBinding = ViewNewRuleBinding.inflate(getLayoutInflater());
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setCancelable(false).setView(newRuleBinding.getRoot()).create();
                newRuleBinding.sure.setTag(appName);
                newRuleBinding.content.setText(stringBuilder.toString());
                newRuleBinding.sure.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        coordinateShare.coordinate.createTime = System.currentTimeMillis();
                        coordinateShare.coordinate.lastTriggerTime = 0;
                        coordinateShare.coordinate.triggerCount = 0;
                        dataDao.insertCoordinate(coordinateShare.coordinate);
                        AppDescribe appDescribe = dataDao.getAppDescribeByPackage(coordinateShare.coordinate.appPackage);
                        if (appDescribe == null) {
                            appDescribe = new AppDescribe();
                            appDescribe.appPackage = coordinateShare.coordinate.appPackage;
                            appDescribe.appName = v.getTag().toString();
                            appDescribe.id = dataDao.insertAppDescribe(appDescribe);
                        }
                        appDescribe.coordinateOnOff = true;
                        dataDao.updateAppDescribe(appDescribe);
                        MyUtils.requestUpdateAppDescribe(appDescribe.appPackage);
                        Intent intent = new Intent(context, EditDataActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        intent.putExtra("packageName", appDescribe.appPackage);
                        startActivity(intent);
                        Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show();
                        alertDialog.dismiss();
                    }
                });
                newRuleBinding.cancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                    }
                });
                Window window = alertDialog.getWindow();
                window.setBackgroundDrawableResource(R.drawable.add_data_background);
                alertDialog.show();
                WindowManager.LayoutParams lp = window.getAttributes();
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                lp.width = metrics.widthPixels / 5 * 4;
                lp.height = metrics.heightPixels / 2;
                window.setAttributes(lp);
            } else if (TextUtils.equals(matcher.group(1), RegulationExport.class.getSimpleName())) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                RegulationExport regulationExport = gson.fromJson(matcher.group(2), RegulationExport.class);

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("我的系统指纹：").append(Build.FINGERPRINT).append("\n");
                stringBuilder.append("他的系统指纹：").append(regulationExport.fingerPrint).append("\n\n");
                DisplayMetrics displayMetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
                stringBuilder.append("我的手机屏幕：").append(displayMetrics).append("\n");
                stringBuilder.append("他的手机屏幕：").append(regulationExport.displayMetrics).append("\n\n");
                int coordinateNum = 0;
                int widgetNum = 0;
                for (Regulation e : regulationExport.regulationList) {
                    coordinateNum += e.coordinateList.size();
                    widgetNum += e.widgetList.size();
                }
                stringBuilder.append(String.format(Locale.ROOT, "共%d个应用，%d条控件规则，%d条坐标规则。", regulationExport.regulationList.size(), widgetNum, coordinateNum));

                ViewNewRuleBinding newRuleBinding = ViewNewRuleBinding.inflate(getLayoutInflater());
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setCancelable(false).setView(newRuleBinding.getRoot()).create();
                newRuleBinding.content.setText(stringBuilder);
                newRuleBinding.sure.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                        RegulationImportActivity.regulationList = regulationExport.regulationList;
                        Intent intent = new Intent(MainActivity.this, RegulationImportActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }
                });
                newRuleBinding.cancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                    }
                });
                Window window = alertDialog.getWindow();
                window.setBackgroundDrawableResource(R.drawable.add_data_background);
                alertDialog.show();
                WindowManager.LayoutParams lp = window.getAttributes();
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                lp.width = metrics.widthPixels / 5 * 4;
                lp.height = metrics.heightPixels / 2;
                window.setAttributes(lp);
            }
        } catch (RuntimeException | FileNotFoundException e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    static class Resource {
        public String name;
        public int drawableId;

        public Resource(String name, int drawableId) {
            this.name = name;
            this.drawableId = drawableId;
        }
    }
}