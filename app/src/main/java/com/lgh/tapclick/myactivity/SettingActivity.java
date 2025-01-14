package com.lgh.tapclick.myactivity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.lgh.tapclick.BuildConfig;
import com.lgh.tapclick.R;
import com.lgh.tapclick.databinding.ActivitySettingBinding;
import com.lgh.tapclick.mybean.LatestMessage;
import com.lgh.tapclick.mybean.MyAppConfig;
import com.lgh.tapclick.myclass.DataDao;
import com.lgh.tapclick.myclass.MyApplication;
import com.lgh.tapclick.myfunction.MyUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SettingActivity extends BaseActivity {
    private Context context;
    private DataDao dataDao;
    private MyAppConfig myAppConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySettingBinding settingBinding = ActivitySettingBinding.inflate(getLayoutInflater());
        setContentView(settingBinding.getRoot());
        context = getApplicationContext();
        dataDao = MyApplication.dataDao;
        myAppConfig = dataDao.getMyAppConfig();

        settingBinding.settingAutoHideOnTaskList.setChecked(myAppConfig.autoHideOnTaskList);
        settingBinding.settingOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });

        settingBinding.settingUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                Observable<LatestMessage> observable = MyApplication.myHttpRequest.getLatestMessage();
                observable.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<LatestMessage>() {
                    AlertDialog waitDialog;

                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        waitDialog = new AlertDialog.Builder(SettingActivity.this).setView(new ProgressBar(context)).setCancelable(false).create();
                        Window window = waitDialog.getWindow();
                        window.setBackgroundDrawableResource(R.color.transparent);
                        waitDialog.show();
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
                                } else {
                                    Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show();
                                }
                            }
                        } catch (RuntimeException e) {
                            Toast.makeText(context, "解析版本号时出现错误", Toast.LENGTH_SHORT).show();
                            // e.printStackTrace();
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        // e.printStackTrace();
                        Toast.makeText(context, "查询新版本时出现错误", Toast.LENGTH_SHORT).show();
                        waitDialog.dismiss();
                    }

                    @Override
                    public void onComplete() {
                        waitDialog.dismiss();
                    }
                });
            }
        });

        settingBinding.settingShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Observable<String> observable = MyApplication.myHttpRequest.getShareContent();
                observable.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<String>() {
                    private AlertDialog waitDialog;

                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        waitDialog = new AlertDialog.Builder(SettingActivity.this).setView(new ProgressBar(context)).setCancelable(false).create();
                        Window window = waitDialog.getWindow();
                        if (window != null) {
                            window.setBackgroundDrawableResource(R.color.transparent);
                        }
                        waitDialog.show();
                    }

                    @Override
                    public void onNext(@NonNull String s) {
                        if (!s.isEmpty()) {
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("text/plain");
                            shareIntent.putExtra(Intent.EXTRA_TEXT, s);
                            startActivityForResult(Intent.createChooser(shareIntent, "请选择分享方式"), 0x01);
                        } else {
                            Toast.makeText(context, "暂时不支持分享", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        waitDialog.dismiss();
                        onNext(getString(R.string.shareContent));
                    }

                    @Override
                    public void onComplete() {
                        waitDialog.dismiss();
                    }
                });
            }
        });

        settingBinding.settingPraise.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=%s" + getPackageName()));
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(Intent.createChooser(intent, "请选择应用市场"));
                } else {
                    Toast.makeText(context, "请到应用市场评分", Toast.LENGTH_SHORT).show();
                }
            }
        });

        settingBinding.settingAutoHideOnTaskList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myAppConfig.autoHideOnTaskList = settingBinding.settingAutoHideOnTaskList.isChecked();
                MyUtils.setExcludeFromRecents(myAppConfig.autoHideOnTaskList);
                dataDao.updateMyAppConfig(myAppConfig);
            }
        });

        settingBinding.settingAuthorChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent github = new Intent(Intent.ACTION_VIEW);
                github.addCategory(Intent.CATEGORY_DEFAULT);
                github.addCategory(Intent.CATEGORY_BROWSABLE);
                github.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                github.setData(Uri.parse("https://github.com/LGH1996/TapClick"));
                startActivity(Intent.createChooser(github, "github"));
            }
        });

        settingBinding.settingGroupChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent openChat = new Intent(Intent.ACTION_VIEW, Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3Dw3oVSTyApiatRQNpBpZbdxWYVdK5f-08"));
                if (openChat.resolveActivity(getPackageManager()) != null) {
                    startActivity(openChat);
                } else {
                    Toast.makeText(context, "未安装QQ或TIM", Toast.LENGTH_SHORT).show();
                }
            }
        });

        settingBinding.settingGetVip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(context, GetVipActivity.class));
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0x01) {
            if (!myAppConfig.isVip) {
                myAppConfig.isVip = true;
                dataDao.updateMyAppConfig(myAppConfig);
                Toast.makeText(context, "水印已去除，重启后生效", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
