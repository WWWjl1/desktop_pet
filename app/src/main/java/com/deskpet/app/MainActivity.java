package com.deskpet.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private Button btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotifPermission();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(30);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("桌面宠物");
        title.setTextSize(28);
        title.setTextColor(0xFF5B4A5A);
        title.setPadding(0, 0, 0, dp(14));

        TextView desc = new TextView(this);
        desc.setText("装好的悬浮小家伙已就位~\n戳它会有意外惊喜！\n已支持：拖动 / 点击互动 / 长按菜单 / 调整大小。");
        desc.setTextSize(16);
        desc.setTextColor(0xFF8A7A88);
        desc.setPadding(0, 0, 0, dp(14));
        desc.setLineSpacing(dp(2), 1f);

        GradientDrawable card = new GradientDrawable();
        card.setColor(0xFFFFF3F7);
        card.setCornerRadius(dp(18));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        desc.setBackground(card);

        btn = new Button(this);
        btn.setAllCaps(false);
        btn.setTextSize(16);
        btn.setPadding(dp(12), 0, dp(12), 0);
        btn.setOnClickListener(v -> onStartPressed());

        root.addView(title);
        root.addView(desc, new LinearLayout.LayoutParams(-1, -2));
        int gap = dp(20);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        root.addView(btn, blp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (btn != null) {
            btn.setText(Settings.canDrawOverlays(this) ? "开启桌宠（悬浮窗）" : "去授权悬浮窗权限");
        }
    }

    private void onStartPressed() {
        if (Settings.canDrawOverlays(this)) {
            PetService.start(this);
            Toast.makeText(this, "桌宠已上线，回到桌面看看~", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        }
    }

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
