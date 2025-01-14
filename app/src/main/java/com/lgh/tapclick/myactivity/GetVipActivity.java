package com.lgh.tapclick.myactivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.lgh.tapclick.databinding.ActivityGetVipBinding;
import com.lgh.tapclick.databinding.ViewDialogGetVipBinding;
import com.lgh.tapclick.myfunction.MyUtils;

public class GetVipActivity extends BaseActivity {

    private ActivityGetVipBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGetVipBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                binding.progress.setText(String.valueOf(newProgress));
                if (newProgress >= 100) {
                    binding.progress.setVisibility(View.GONE);
                }
            }
        });
        WebSettings settings = getWebSettings();
        settings.supportMultipleWindows();
        binding.webView.loadUrl("https://docs.qq.com/doc/DWXhWVmFodlJGRnhL");

        binding.getVip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ViewDialogGetVipBinding binding = ViewDialogGetVipBinding.inflate(getLayoutInflater());
                binding.deviceNo.setText(MyUtils.getMyDeviceNo());
                new AlertDialog.Builder(GetVipActivity.this)
                        .setView(binding.getRoot())
                        .setPositiveButton("确定", null)
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private @NonNull WebSettings getWebSettings() {
        WebSettings settings = binding.webView.getSettings();
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setJavaScriptEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setGeolocationEnabled(true);
        settings.setDatabaseEnabled(true);
        return settings;
    }

    @Override
    public void onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack();
            return;
        }
        super.onBackPressed();
    }
}