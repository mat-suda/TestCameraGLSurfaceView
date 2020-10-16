package com.example.testcameraglsurfaceview;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.TextView;

public class ScreenUtil {

    public static void hideSystemUI(AppCompatActivity activity) {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = activity.getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    public static float setScreenBrightness(AppCompatActivity activity, int brightness){
        return setScreenBrightness(activity, (float)(brightness / 255.0f));
    }

    public static float setScreenBrightness(AppCompatActivity activity, float brightness){
        Window window = activity.getWindow();
        LayoutParams lp = window.getAttributes();
        // 明るさを設定
        if(brightness > 1.0f){
            brightness = 1.0f;
        } else if(brightness < 0.0f){
            brightness = 0.0f;
        }
        lp.screenBrightness = brightness;
        window.setAttributes(lp);

        return activity.getWindow().getAttributes().screenBrightness;
    }
}
