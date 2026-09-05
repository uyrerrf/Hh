package com.xrc.system.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.xrc.system.core.Constants;

public class OverlayActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        TextView tv = new TextView(this);
        tv.setText("System Update");
        tv.setTextSize(24);
        layout.addView(tv);
        Button btn = new Button(this);
        btn.setText("Continue");
        btn.setOnClickListener(v -> finish());
        layout.addView(btn);
        setContentView(layout);
    }

    @Override
    public void onBackPressed() {
        // Block back button
    }
}
