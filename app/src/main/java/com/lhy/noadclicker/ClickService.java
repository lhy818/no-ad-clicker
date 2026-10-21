package com.lhy.noadclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.lhy.noadclicker.core.LoopPolicy;
import com.lhy.noadclicker.core.Recorder;
import com.lhy.noadclicker.core.Script;
import com.lhy.noadclicker.core.Sequencer;
import com.lhy.noadclicker.core.Step;

import java.util.ArrayList;
import java.util.List;

/**
 * Performs gestures and hosts all floating UI as accessibility overlays.
 * It never reads window content.
 */
public class ClickService extends AccessibilityService {

    private static ClickService instance;

    public static ClickService get() { return instance; }

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private Script script = new Script();

    private LinearLayout panel;
    private WindowManager.LayoutParams panelLp;
    private TextView playBtn, recBtn;
    private final List<View> markers = new ArrayList<>();

    private boolean running;
    private Sequencer sequencer;
    private long runStart;

    private View recLayer;
    private WindowManager.LayoutParams recLp;
    private Recorder recorder;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* not used */ }

    @Override public void onInterrupt() { stopRun(); }

    @Override
    public void onDestroy() {
        stopRun();
        hidePanel();
        instance = null;
        super.onDestroy();
    }

    // ---------------------------------------------------------------- public API

    public boolean isPanelShown() { return panel != null; }

    public Script currentScript() { return script; }

    public void loadScript(Script s) {
        stopRun();
        stopRecording(false);
        script = s;
        rebuildMarkers();
    }

    public void showPanel() {
        if (panel != null) return;
        buildPanel();
        rebuildMarkers();
    }

    public void hidePanel() {
        stopRun();
        stopRecording(false);
        clearMarkers();
        if (panel != null) { safeRemove(panel); panel = null; }
    }

    // ---------------------------------------------------------------- helpers

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private Point screen() {
        Point p = new Point();
        wm.getDefaultDisplay().getRealSize(p);
        return p;
    }

    private WindowManager.LayoutParams overlayLp(int w, int h, boolean touchable) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | (touchable ? 0 : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return lp;
    }

    private void safeRemove(View v) {
        try { wm.removeView(v); } catch (Exception ignored) { }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---------------------------------------------------------------- panel

    private TextView panelButton(String label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(8), dp(6), dp(8));
        t.setOnClickListener(l);
        panel.addView(t, new LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT));
        return t;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void buildPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC222222);
        bg.setCornerRadius(dp(12));
        panel.setBackground(bg);

        TextView handle = panelButton("⠿ 拖动", null);
        handle.setTextColor(0xFFAAAAAA);
        handle.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY(); ox = panelLp.x; oy = panelLp.y; return true;
                    case MotionEvent.ACTION_MOVE:
                        panelLp.x = ox + (int) (e.getRawX() - sx);
                        panelLp.y = oy + (int) (e.getRawY() - sy);
                        wm.updateViewLayout(panel, panelLp);
                        return true;
                }
                return false;
            }
        });
        playBtn = panelButton("▶ 开始", v -> { if (running) stopRun(); else startRun(); });
        panelButton("＋点击", v -> addTap());
        panelButton("＋滑动", v -> addSwipe());
        recBtn = panelButton("● 录制", v -> { if (recorder != null) stopRecording(true); else startRecording(); });
        panelButton("⚙ 设置", v -> showSettings());
        panelButton("💾 保存", v -> showSave());
        panelButton("🗑 清空", v -> confirmClear());
        panelButton("✕ 关闭", v -> hidePanel());

        panelLp = overlayLp(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, true);
        panelLp.x = dp(4);
        panelLp.y = screen().y / 4;
        wm.addView(panel, panelLp);
    }

    private void bringPanelToFront() {
        if (panel == null) return;
        safeRemove(panel);
        wm.addView(panel, panelLp);
    }

    private void refreshButtons() {
        if (playBtn != null) playBtn.setText(running ? "■ 停止" : "▶ 开始");
        if (recBtn != null) recBtn.setText(recorder != null ? "■ 停录" : "● 录制");
    }

    // ---------------------------------------------------------------- markers

    /** Numbered circle. kind: 0 tap, 1 swipe start, 2 swipe end. */
    private class MarkerView extends View {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        String label;

        MarkerView(Context c, String label, int kind) {
            super(c);
            this.label = label;
            fill.setColor(kind == 0 ? 0x993D7BFF : kind == 1 ? 0x9928A745 : 0x99E8590C);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dp(2));
            ring.setColor(Color.WHITE);
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(13));
            text.setFakeBoldText(true);
        }

        @Override protected void onDraw(Canvas c) {
            float r = getWidth() / 2f;
            c.drawCircle(r, r, r - dp(2), fill);
            c.drawCircle(r, r, r - dp(2), ring);
            c.drawLine(r - dp(5), r, r + dp(5), r, ring);
            c.drawLine(r, r - dp(5), r, r + dp(5), ring);
            c.drawText(label, r, r - dp(8), text);
        }
    }

    private int markerSize() { return dp(44); }

    private void clearMarkers() {
        for (View v : markers) safeRemove(v);
        markers.clear();
    }

    private void rebuildMarkers() {
        clearMarkers();
        if (panel == null || recorder != null) return;
        for (int i = 0; i < script.steps.size(); i++) {
            Step s = script.steps.get(i);
            String n = String.valueOf(i + 1);
            if (s.type == Step.Type.TAP) {
                addMarker(s, false, n, 0);
            } else {
                addMarker(s, false, n + "起", 1);
                addMarker(s, true, n + "终", 2);
            }
        }
        bringPanelToFront();
        setMarkersTouchable(!running);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addMarker(Step step, boolean endPoint, String label, int kind) {
        int size = markerSize();
        int half = size / 2;
        MarkerView v = new MarkerView(this, label, kind);
        WindowManager.LayoutParams lp = overlayLp(size, size, true);
        lp.x = (endPoint ? step.x2 : step.x) - half;
        lp.y = (endPoint ? step.y2 : step.y) - half;
        Runnable longPress = () -> showStepEditor(step);
        v.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy; boolean moved;
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY(); ox = lp.x; oy = lp.y; moved = false;
                        main.postDelayed(longPress, 600);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                        if (Math.hypot(dx, dy) > dp(6)) { moved = true; main.removeCallbacks(longPress); }
                        if (moved) {
                            lp.x = ox + (int) dx; lp.y = oy + (int) dy;
                            wm.updateViewLayout(view, lp);
                            int cx = Math.max(0, lp.x + half), cy = Math.max(0, lp.y + half);
                            if (endPoint) { step.x2 = cx; step.y2 = cy; } else { step.x = cx; step.y = cy; }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        main.removeCallbacks(longPress);
                        return true;
                }
                return false;
            }
        });
        wm.addView(v, lp);
        markers.add(v);
    }

    private void setMarkersTouchable(boolean touchable) {
        for (View v : markers) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
            if (touchable) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            else lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            v.setAlpha(touchable ? 1f : 0.45f);
            try { wm.updateViewLayout(v, lp); } catch (Exception ignored) { }
        }
    }

    private void addTap() {
        if (running || recorder != null) return;
        Point p = screen();
        int k = script.steps.size() % 5;
        script.steps.add(Step.tap(p.x / 2 + dp(20) * k, p.y / 2 + dp(20) * k));
        rebuildMarkers();
    }

    private void addSwipe() {
        if (running || recorder != null) return;
        Point p = screen();
        script.steps.add(Step.swipe(p.x / 2, p.y * 2 / 3, p.x / 2, p.y / 3));
        rebuildMarkers();
    }

    // ---------------------------------------------------------------- dialogs

    private AlertDialog overlayDialog(AlertDialog.Builder b) {
        AlertDialog d = b.create();
        if (d.getWindow() != null) d.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        return d;
    }

    private EditText numberField(LinearLayout parent, String label, long value) {
        TextView t = new TextView(this);
        t.setText(label);
        parent.addView(t);
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(value));
        parent.addView(e);
        return e;
    }

    private static long parse(EditText e, long fallback) {
        try { return Long.parseLong(e.getText().toString().trim()); } catch (NumberFormatException ex) { return fallback; }
    }

    private LinearLayout form() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(8), dp(20), 0);
        return l;
    }

    private void showStepEditor(Step step) {
        if (running) return;
        int idx = script.steps.indexOf(step);
        LinearLayout f = form();
        EditText press = numberField(f, step.type == Step.Type.TAP ? "按压时长（毫秒）" : "滑动时长（毫秒）", step.pressMs);
        EditText delay = numberField(f, "执行后等待（毫秒）", step.delayAfterMs);
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("第 " + (idx + 1) + " 步")
                .setView(f)
                .setPositiveButton("保存", (d, w) -> {
                    step.pressMs = Step.clampPress(parse(press, step.pressMs));
                    step.delayAfterMs = Step.clampDelay(parse(delay, step.delayAfterMs));
                })
                .setNeutralButton("删除此步", (d, w) -> { script.steps.remove(step); rebuildMarkers(); })
                .setNegativeButton("取消", null);
        overlayDialog(b).show();
    }

    private void showSettings() {
        if (running) return;
        LinearLayout f = form();
        RadioGroup g = new RadioGroup(this);
        RadioButton rInf = new RadioButton(this); rInf.setText("无限循环"); rInf.setId(View.generateViewId());
        RadioButton rCnt = new RadioButton(this); rCnt.setText("按次数"); rCnt.setId(View.generateViewId());
        RadioButton rDur = new RadioButton(this); rDur.setText("按时长"); rDur.setId(View.generateViewId());
        g.addView(rInf); g.addView(rCnt); g.addView(rDur);
        f.addView(g);
        LoopPolicy lp = script.loop;
        g.check(lp.mode == LoopPolicy.Mode.COUNT ? rCnt.getId() : lp.mode == LoopPolicy.Mode.DURATION ? rDur.getId() : rInf.getId());
        EditText count = numberField(f, "循环次数", lp.count);
        EditText secs = numberField(f, "运行时长（秒）", Math.max(1, lp.durationMs / 1000));
        EditText all = numberField(f, "统一设置每步等待（毫秒，留空不改）", 0);
        all.setText("");
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("运行设置")
                .setView(f)
                .setPositiveButton("确定", (d, w) -> {
                    LoopPolicy p = new LoopPolicy();
                    int id = g.getCheckedRadioButtonId();
                    p.mode = id == rCnt.getId() ? LoopPolicy.Mode.COUNT : id == rDur.getId() ? LoopPolicy.Mode.DURATION : LoopPolicy.Mode.INFINITE;
                    p.count = (int) Math.max(1, Math.min(Integer.MAX_VALUE, parse(count, 1)));
                    p.durationMs = Math.max(1, parse(secs, 60)) * 1000L;
                    script.loop = p;
                    if (!all.getText().toString().trim().isEmpty()) {
                        long v = Step.clampDelay(parse(all, Step.DEFAULT_DELAY));
                        for (Step s : script.steps) s.delayAfterMs = v;
                    }
                })
                .setNegativeButton("取消", null);
        overlayDialog(b).show();
    }

    private void showSave() {
        if (running) return;
        LinearLayout f = form();
        EditText name = new EditText(this);
        name.setText(script.name);
        f.addView(name);
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("保存方案")
                .setView(f)
                .setPositiveButton("保存", (d, w) -> {
                    script.name = name.getText().toString();
                    try { new ScriptStore(this).save(script); toast("已保存：" + script.name); }
                    catch (Exception e) { toast("保存失败：" + e.getMessage()); }
                })
                .setNegativeButton("取消", null);
        overlayDialog(b).show();
    }

    private void confirmClear() {
        if (running || recorder != null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setMessage("清空当前所有步骤？")
                .setPositiveButton("清空", (d, w) -> { script.steps.clear(); rebuildMarkers(); })
                .setNegativeButton("取消", null);
        overlayDialog(b).show();
    }

    // ---------------------------------------------------------------- running

    private static GestureDescription toGesture(Step s) {
        Path p = new Path();
        p.moveTo(s.x, s.y);
        if (s.type == Step.Type.SWIPE) p.lineTo(s.x2, s.y2);
        return new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, s.pressMs))
                .build();
    }

    private void startRun() {
        if (recorder != null) return;
        if (script.steps.isEmpty()) { toast("先添加点击或滑动"); return; }
        running = true;
        sequencer = new Sequencer(script.steps.size(), script.loop);
        runStart = SystemClock.elapsedRealtime();
        setMarkersTouchable(false);
        refreshButtons();
        main.post(this::runNext);
    }

    private void runNext() {
        if (!running) return;
        int i = sequencer.next(SystemClock.elapsedRealtime() - runStart);
        if (i < 0) { stopRun(); toast("运行结束"); return; }
        Step s = script.steps.get(i);
        GestureResultCallback cb = new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { scheduleNext(s.delayAfterMs); }
            @Override public void onCancelled(GestureDescription g) { scheduleNext(s.delayAfterMs); }
        };
        if (!dispatchGesture(toGesture(s), cb, main)) scheduleNext(s.delayAfterMs);
    }

    private void scheduleNext(long delay) {
        if (running) main.postDelayed(this::runNext, delay);
    }

    private void stopRun() {
        running = false;
        main.removeCallbacksAndMessages(null);
        setMarkersTouchable(true);
        refreshButtons();
    }

    // ---------------------------------------------------------------- recording

    @SuppressLint("ClickableViewAccessibility")
    private void startRecording() {
        if (running || panel == null) return;
        recorder = new Recorder();
        clearMarkers();
        recLayer = new View(this);
        recLayer.setBackgroundColor(0x14FF0000);
        Point p = screen();
        recLp = overlayLp(p.x, p.y, true);
        recLayer.setOnTouchListener((v, e) -> {
            if (recorder == null) return false;
            if (e.getPointerCount() > 1) return true;
            int x = (int) e.getRawX(), y = (int) e.getRawY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: recorder.onDown(x, y, e.getEventTime()); break;
                case MotionEvent.ACTION_MOVE: recorder.onMove(x, y); break;
                case MotionEvent.ACTION_UP:
                    Step s = recorder.onUp(x, y, e.getEventTime());
                    if (s != null) passThrough(s);
                    break;
            }
            return true;
        });
        wm.addView(recLayer, recLp);
        bringPanelToFront();
        refreshButtons();
        toast("录制中：正常操作屏幕，完成后点「停录」");
    }

    /** Replays the captured gesture underneath the recording layer so the app reacts. */
    private void passThrough(Step s) {
        if (recLayer == null) return;
        recLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        wm.updateViewLayout(recLayer, recLp);
        Runnable restore = () -> {
            if (recLayer == null) return;
            recLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try { wm.updateViewLayout(recLayer, recLp); } catch (Exception ignored) { }
        };
        GestureResultCallback cb = new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { main.postDelayed(restore, 30); }
            @Override public void onCancelled(GestureDescription g) { main.postDelayed(restore, 30); }
        };
        main.postDelayed(() -> {
            if (!dispatchGesture(toGesture(s), cb, main)) restore.run();
        }, 30);
    }

    private void stopRecording(boolean keep) {
        if (recorder == null) return;
        List<Step> steps = recorder.result();
        recorder = null;
        if (recLayer != null) { safeRemove(recLayer); recLayer = null; }
        if (keep) {
            if (steps.isEmpty()) toast("没有录到操作");
            else { script.steps.clear(); script.steps.addAll(steps); toast("录到 " + steps.size() + " 步"); }
        }
        refreshButtons();
        rebuildMarkers();
    }
}
