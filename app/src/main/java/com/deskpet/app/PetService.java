package com.deskpet.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

public class PetService extends Service {

    public static final String ACTION_SHOW = "com.deskpet.app.SHOW";
    public static final String ACTION_STOP = "com.deskpet.app.STOP";
    private static final String CHANNEL_ID = "pet_channel";
    private static final String PREFS = "pet";

    private static final int C_JUMP = 0;
    private static final int C_SQUASH = 1;
    private static final int[] CYCLE = {C_JUMP, C_SQUASH, C_JUMP, C_SQUASH};

    private static final String[] TEXT_JUMP = {"起飞啦～✈️", "跳得好高！", "嘿嘿，好玩~", "哇啊啊～"};
    private static final String[] TEXT_SQUASH = {"哎呀压扁了！", "弹~弹~", "我又弹回来了~"};
    private static final String[] TEXT_PET = {"喵～好舒服~", "再摸一下嘛~", "好暖哦❤️", "嘿嘿，开心~"};
    private static final String[] TEXT_FEED = {"啊呜～真好吃！", "谢谢投喂！", "还要还要~", "好吃到跺脚~"};
    private static final String[] TEXT_WALK = {"散步去咯~", "出发！", "我跑跑跑~", "跟着我走~"};
    private static final String[] TEXT_WALK_EDGE = {"这边到头啦~", "回头再来~"};
    private static final String[] TEXT_CHAT = {
            "你今天真好看～", "要不要一起玩游戏？", "我是不是很可爱？(叉腰)", "别摸秃我啦！",
            "老板，加鸡腿！", "今天也要元气满满哦~", "偷偷吃零食被我发现了吧~", "举起手来！(举起Jio)"};
    private static final String[] TEXT_WAKE = {"嗯？…我醒了！", "谁在叫我？", "睡饱啦~"};

    private WindowManager wm;
    private NotificationManager nm;
    private Handler h;
    private Random rnd;
    private SharedPreferences prefs;

    private FrameLayout rootView;
    private ImageView pet;
    private TextView bubble;
    private WindowManager.LayoutParams params;

    private Bitmap bm;
    private float aspect;
    private int sizeDp;
    private boolean alwaysOnTop = true;

    private int screenW, screenH;
    private int interactionIndex;
    private boolean sleeping;
    private boolean chatMode;
    private boolean walking;
    private boolean trotting;
    private boolean flipPhase;
    private boolean facingRight = true;
    private int walkDirection = 1;
    private float lastRawX, lastRawY;

    private Runnable trotTick, walkTick, chatTick, hideBubbleTick;

    private boolean menuPopupActive;
    private View menuView, catcherView;
    private WindowManager.LayoutParams menuLp, catcherLp;

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, PetService.class);
        i.setAction(ACTION_SHOW);
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        rnd = new Random();
        h = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        sizeDp = prefs.getInt("size", 150);
        alwaysOnTop = prefs.getBoolean("onTop", true);

        bm = BitmapFactory.decodeResource(getResources(), R.drawable.character);
        if (bm == null) { stopSelf(); return; }
        aspect = (float) bm.getHeight() / bm.getWidth();

        initRunnables();
        buildViews();
        buildParams();
        loadPosition();
        try {
            if (alwaysOnTop) ensureOverlayVisible();
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void initRunnables() {
        trotTick = new Runnable() {
            @Override
            public void run() {
                if (trotting || walking) {
                    flipPhase = !flipPhase;
                    setTrotFrame();
                    h.postDelayed(this, 110);
                }
            }
        };
        walkTick = new Runnable() {
            @Override
            public void run() {
                if (!walking) return;
                params.x += walkDirection * dp(3);
                if (params.x <= 0) {
                    walkDirection = 1;
                    facingRight = true;
                    showBubble(rand(TEXT_WALK_EDGE), 1400);
                } else if (params.x >= screenW - params.width) {
                    walkDirection = -1;
                    facingRight = false;
                    showBubble(rand(TEXT_WALK_EDGE), 1400);
                }
                clampPosition();
                try { wm.updateViewLayout(rootView, params); } catch (Exception ignored) {}
                savePosition();
                h.postDelayed(this, 16);
            }
        };
        chatTick = new Runnable() {
            @Override
            public void run() {
                if (!chatMode) return;
                showBubble(rand(TEXT_CHAT), 2600);
                h.postDelayed(this, 2600);
            }
        };
    }

    private void buildViews() {
        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(Color.TRANSPARENT);

        pet = new ImageView(this);
        pet.setImageBitmap(bm);
        pet.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pet.setBackgroundColor(Color.TRANSPARENT);

        bubble = new TextView(this);
        bubble.setTextColor(0xFF443648);
        bubble.setTextSize(15);
        int pad = dp(12);
        bubble.setPadding(pad, pad, pad, pad);
        bubble.setBackground(roundedBg(0xFFFFFFFF, 18));
        bubble.setGravity(Gravity.CENTER);
        bubble.setVisibility(View.GONE);

        rootView.addView(pet);
        rootView.addView(bubble);

        final GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                lastRawX = e.getRawX();
                lastRawY = e.getRawY();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                showMenu();
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (sleeping) return true;
                if (walking) stopWalk();
                if (chatMode) stopChat();
                float rx = e2.getRawX();
                float ry = e2.getRawY();
                int mdx = (int) (rx - lastRawX);
                int mdy = (int) (ry - lastRawY);
                lastRawX = rx;
                lastRawY = ry;

                params.x += mdx;
                params.y += mdy;
                clampPosition();
                try { wm.updateViewLayout(rootView, params); } catch (Exception ignored) {}

                if (Math.abs(mdx) > Math.abs(mdy)) {
                    facingRight = mdx > 0;
                    startTrot();
                }
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                onTap();
                return true;
            }
        });

        pet.setOnTouchListener((v, e) -> {
            boolean handled = detector.onTouchEvent(e);
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                if (!walking) stopTrot();
                savePosition();
            }
            return handled;
        });
    }

    private void buildParams() {
        params = new WindowManager.LayoutParams();
        params.type = overlayType();
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params.gravity = Gravity.TOP | Gravity.START;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        applyDimensions();
    }

    private void applyDimensions() {
        int pw = dp(sizeDp);
        int ph = Math.round(pw * aspect);
        int reserve = dp(120);
        params.width = pw;
        params.height = ph + reserve;

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(pw, ph);
        plp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        pet.setLayoutParams(plp);
        pet.setPivotX(pw / 2f);
        pet.setPivotY(ph);

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(pw, reserve);
        blp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        blp.topMargin = dp(4);
        bubble.setLayoutParams(blp);

        if (rootView != null && rootView.getParent() != null) {
            try { wm.updateViewLayout(rootView, params); } catch (Exception ignored) {}
        }
    }

    private void ensureOverlayVisible() {
        if (!Settings.canDrawOverlays(this)) return;
        if (rootView.getParent() == null) {
            wm.addView(rootView, params);
        } else {
            try { wm.updateViewLayout(rootView, params); } catch (Exception ignored) {}
        }
    }

    private void hideOverlay() {
        if (rootView.getParent() != null) {
            try { wm.removeView(rootView); } catch (Exception ignored) {}
        }
    }

    private void loadPosition() {
        int x = prefs.getInt("x", screenW - params.width - dp(12));
        int y = prefs.getInt("y", screenH - params.height - dp(90));
        params.x = x;
        params.y = y;
        clampPosition();
    }

    private void savePosition() {
        prefs.edit()
                .putInt("x", params.x)
                .putInt("y", params.y)
                .apply();
    }

    private void clampPosition() {
        if (params.x < 0) params.x = 0;
        if (params.y < 0) params.y = 0;
        if (params.x > screenW - params.width) params.x = screenW - params.width;
        if (params.y > screenH - params.height) params.y = screenH - params.height;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String action = intent != null ? intent.getAction() : ACTION_SHOW;
        if (ACTION_STOP.equals(action)) {
            exitApp();
            return START_NOT_STICKY;
        }
        startAsForeground();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_SHOW.equals(action)) {
            alwaysOnTop = true;
            prefs.edit().putBoolean("onTop", true).apply();
        }
        if (alwaysOnTop) ensureOverlayVisible();
        return START_STICKY;
    }

    private void startAsForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "桌面宠物", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent showIntent = new Intent(this, PetService.class).setAction(ACTION_SHOW);
        PendingIntent pi = PendingIntent.getForegroundService(this, 1, showIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_pet)
                .setContentTitle("桌宠正在运行")
                .setContentText("点击唤回悬浮桌宠")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    // ---------------- interactions ----------------

    private void onTap() {
        if (menuPopupActive) { dismissMenu(); return; }
        if (walking) { stopWalk(); showBubble("嗯？叫我吗？", 1600); return; }
        if (chatMode) { stopChat(); showBubble("不聊啦~", 1200); return; }
        if (sleeping) { wake(); return; }
        triggerInteraction();
    }

    private void triggerInteraction() {
        int c = CYCLE[interactionIndex % CYCLE.length];
        interactionIndex++;
        if (c == C_JUMP) {
            doJump();
            showBubble(rand(TEXT_JUMP), 1800);
        } else {
            doSquash(1.15f, 0.82f);
            showBubble(rand(TEXT_SQUASH), 1800);
        }
    }

    private void doJump() {
        pet.animate().cancel();
        resetPetTransform();
        int up = dp(60);
        pet.animate().translationY(-up).setDuration(300).setInterpolator(new DecelerateInterpolator(1.5f))
                .withEndAction(() -> pet.animate().translationY(0).setDuration(300)
                        .setInterpolator(new AccelerateInterpolator(1.2f)).start())
                .start();
    }

    private void doSquash(float sx, float sy) {
        pet.animate().cancel();
        pet.setRotation(0f);
        pet.setTranslationY(0f);
        pet.setPivotX(pet.getWidth() / 2f);
        pet.setPivotY(pet.getHeight());
        pet.animate().scaleX(sx).scaleY(sy).setDuration(130)
                .withEndAction(() -> pet.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(new OvershootInterpolator(1.8f)).start())
                .start();
    }

    private void doChew() {
        pet.animate().cancel();
        pet.setPivotX(pet.getWidth() / 2f);
        pet.setPivotY(pet.getHeight());
        pet.animate().scaleY(0.92f).scaleX(1.08f).setDuration(90).withEndAction(() ->
                pet.animate().scaleY(1f).scaleX(1f).setDuration(90).withEndAction(() ->
                        pet.animate().scaleY(0.94f).scaleX(1.05f).setDuration(90).withEndAction(() ->
                                pet.animate().scaleY(1f).scaleX(1f).setDuration(120).start()).start()).start()).start();
    }

    private void showPetHead() {
        doSquash(1.12f, 0.86f);
        showBubble(rand(TEXT_PET), 1800);
    }

    private void showFeed() {
        doChew();
        showBubble("🍎 " + rand(TEXT_FEED), 1800);
    }

    // ---------------- trot / walk ----------------

    private void setTrotFrame() {
        float base = facingRight ? -1f : 1f;
        float rot = flipPhase ? 8f : -8f;
        float ty = flipPhase ? dp(-5) : dp(3);
        pet.setScaleX(base);
        pet.setRotation(rot);
        pet.setTranslationY(ty);
        pet.setScaleY(1f);
    }

    private void resetPetTransform() {
        pet.setScaleX(1f);
        pet.setScaleY(1f);
        pet.setRotation(0f);
        pet.setTranslationY(0f);
    }

    private void startTrot() {
        if (!trotting) {
            trotting = true;
            h.removeCallbacks(trotTick);
            h.post(trotTick);
        }
    }

    private void stopTrot() {
        trotting = false;
        h.removeCallbacks(trotTick);
        if (!walking) resetPetTransform();
    }

    private void startWalk() {
        if (walking) return;
        walking = true;
        facingRight = true;
        walkDirection = 1;
        showBubble(rand(TEXT_WALK), 1600);
        h.removeCallbacks(trotTick);
        h.post(trotTick);
        h.removeCallbacks(walkTick);
        h.post(walkTick);
    }

    private void stopWalk() {
        if (!walking) return;
        walking = false;
        h.removeCallbacks(walkTick);
        h.removeCallbacks(trotTick);
        resetPetTransform();
    }

    // ---------------- chat / sleep / wake ----------------

    private void startChat() {
        chatMode = true;
        h.removeCallbacks(chatTick);
        h.post(chatTick);
    }

    private void stopChat() {
        chatMode = false;
        h.removeCallbacks(chatTick);
    }

    private void showSleep() {
        sleeping = true;
        if (walking) stopWalk();
        if (chatMode) stopChat();
        resetPetTransform();
        pet.setAlpha(0.92f);
        showBubble("💤 困了，睡个美容觉…", 2600);
    }

    private void wake() {
        sleeping = false;
        pet.setAlpha(1f);
        showBubble(rand(TEXT_WAKE), 1800);
        doJump();
    }

    // ---------------- bubble ----------------

    private void showBubble(String text, int ms) {
        bubble.setText(text);
        bubble.setVisibility(View.VISIBLE);
        bubble.setAlpha(0f);
        bubble.setScaleX(0.8f);
        bubble.setScaleY(0.8f);
        bubble.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170).start();
        h.removeCallbacks(hideBubbleTick);
        hideBubbleTick = this::hideBubble;
        h.postDelayed(hideBubbleTick, ms);
    }

    private void hideBubble() {
        bubble.animate().alpha(0f).setDuration(160)
                .withEndAction(() -> bubble.setVisibility(View.GONE)).start();
    }

    // ---------------- menu ----------------

    private void showMenu() {
        if (menuPopupActive) return;
        menuPopupActive = true;

        FrameLayout catcher = new FrameLayout(this);
        catcher.setBackgroundColor(0x30000000);
        catcher.setOnClickListener(v -> dismissMenu());
        WindowManager.LayoutParams clp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        clp.gravity = Gravity.TOP | Gravity.START;
        clp.x = 0;
        clp.y = 0;
        catcherView = catcher;
        catcherLp = clp;

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackground(roundedBg(0xFFFFFFFF, 18));
        int p = dp(8);
        content.setPadding(p, p, p, p);
        addMenuRow(content, "陪我聊聊天", () -> handleMenu(1));
        addMenuRow(content, "摸摸头", () -> handleMenu(2));
        addMenuRow(content, "喂吃的", () -> handleMenu(3));
        addMenuRow(content, "让她走路", () -> handleMenu(4));
        addMenuRow(content, "让她睡觉", () -> handleMenu(5));
        addMenuRow(content, "调整大小", () -> handleMenu(6));
        addMenuRow(content, "置顶开关", () -> handleMenu(7));
        addMenuRow(content, "退出程序", () -> handleMenu(8));
        menuView = content;

        WindowManager.LayoutParams mlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        mlp.gravity = Gravity.TOP | Gravity.START;
        mlp.x = params.x + params.width + dp(8);
        mlp.y = params.y + dp(10);
        if (mlp.x > screenW - dp(190)) mlp.x = screenW - dp(190);
        if (mlp.y > screenH - dp(420)) mlp.y = screenH - dp(420);
        menuLp = mlp;

        wm.addView(catcherView, catcherLp);
        wm.addView(menuView, menuLp);
    }

    private void addMenuRow(LinearLayout parent, String text, Runnable onClick) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(0xFF443648);
        tv.setPadding(dp(14), dp(12), dp(14), dp(12));
        tv.setOnClickListener(v -> {
            dismissMenu();
            onClick.run();
        });
        parent.addView(tv);
    }

    private void dismissMenu() {
        if (!menuPopupActive) return;
        menuPopupActive = false;
        if (menuView != null) {
            try { wm.removeView(menuView); } catch (Exception ignored) {}
            menuView = null;
        }
        if (catcherView != null) {
            try { wm.removeView(catcherView); } catch (Exception ignored) {}
            catcherView = null;
        }
    }

    private void handleMenu(int idx) {
        switch (idx) {
            case 1: startChat(); break;
            case 2: showPetHead(); break;
            case 3: showFeed(); break;
            case 4: startWalk(); break;
            case 5: showSleep(); break;
            case 6: cycleSize(); break;
            case 7: toggleOnTop(); break;
            case 8: exitApp(); break;
        }
    }

    private void cycleSize() {
        int[] sizes = {100, 150, 200, 260};
        int next = 150;
        for (int i = 0; i < sizes.length; i++) {
            if (sizes[i] == sizeDp) { next = sizes[(i + 1) % sizes.length]; break; }
        }
        sizeDp = next;
        prefs.edit().putInt("size", next).apply();
        resetPetTransform();
        applyDimensions();
        savePosition();
        showBubble("变成 " + next + " 大小啦~", 1600);
    }

    private void toggleOnTop() {
        alwaysOnTop = !alwaysOnTop;
        prefs.edit().putBoolean("onTop", alwaysOnTop).apply();
        savePosition();
        if (alwaysOnTop) {
            ensureOverlayVisible();
            showBubble("已开启置顶 ✦", 1600);
        } else {
            hideOverlay();
            toast("已取消置顶，桌宠收进通知栏~");
        }
    }

    private void exitApp() {
        dismissMenu();
        hideOverlay();
        h.removeCallbacksAndMessages(null);
        stopForeground(true);
        stopSelf();
    }

    // ---------------- util ----------------

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private GradientDrawable roundedBg(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private String rand(String[] arr) {
        return arr[rnd.nextInt(arr.length)];
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
