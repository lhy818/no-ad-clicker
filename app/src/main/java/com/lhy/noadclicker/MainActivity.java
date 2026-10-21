package com.lhy.noadclicker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.lhy.noadclicker.core.Script;
import com.lhy.noadclicker.core.ScriptCodec;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT = 1, REQ_EXPORT = 2;

    private TextView status;
    private Button panelBtn;
    private ArrayAdapter<String> adapter;
    private final List<String> names = new ArrayList<>();
    private ScriptStore store;
    private String pendingExport;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new ScriptStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        status = new TextView(this);
        status.setTextSize(15);
        root.addView(status);

        Button acc = new Button(this);
        acc.setText("打开无障碍设置");
        acc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(acc);

        panelBtn = new Button(this);
        panelBtn.setOnClickListener(v -> togglePanel());
        root.addView(panelBtn);

        Button imp = new Button(this);
        imp.setText("导入方案文件");
        imp.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_IMPORT);
        });
        root.addView(imp);

        TextView title = new TextView(this);
        title.setText("已保存的方案（点一下操作）");
        title.setPadding(0, dp(16), 0, dp(4));
        root.addView(title);

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> scriptMenu(names.get(pos)));
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView foot = new TextView(this);
        foot.setText("本应用不联网、无广告、不读取屏幕内容。");
        foot.setTextSize(12);
        root.addView(foot);

        setContentView(root);
        handleViewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleViewIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        ClickService s = ClickService.get();
        status.setText(s == null
                ? "无障碍服务：未开启\n请点下方按钮，在列表中找到「无广告连点器」并开启。"
                : "无障碍服务：已开启");
        panelBtn.setEnabled(s != null);
        panelBtn.setText(s != null && s.isPanelShown() ? "关闭悬浮面板" : "显示悬浮面板");
        names.clear();
        names.addAll(store.names());
        adapter.notifyDataSetChanged();
    }

    private void togglePanel() {
        ClickService s = ClickService.get();
        if (s == null) return;
        if (s.isPanelShown()) s.hidePanel();
        else { s.showPanel(); moveTaskToBack(true); }
        refresh();
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    private void scriptMenu(String name) {
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{"加载到悬浮面板", "导出为文件", "删除"}, (d, which) -> {
                    if (which == 0) loadIntoPanel(name);
                    else if (which == 1) export(name);
                    else confirmDelete(name);
                })
                .show();
    }

    private void loadIntoPanel(String name) {
        ClickService s = ClickService.get();
        if (s == null) { toast("请先开启无障碍服务"); return; }
        try {
            Script sc = store.load(name);
            s.showPanel();
            s.loadScript(sc);
            moveTaskToBack(true);
        } catch (Exception e) {
            toast("加载失败：" + e.getMessage());
        }
    }

    private void export(String name) {
        pendingExport = name;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, name + ".json");
        startActivityForResult(i, REQ_EXPORT);
    }

    private void confirmDelete(String name) {
        new AlertDialog.Builder(this)
                .setMessage("删除方案「" + name + "」？")
                .setPositiveButton("删除", (d, w) -> { store.delete(name); refresh(); })
                .setNegativeButton("取消", null)
                .show();
    }

    private void handleViewIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            importFrom(intent.getData());
            intent.setData(null);
        }
    }

    private void importFrom(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("无法读取文件");
            Script s = ScriptCodec.decode(ScriptStore.readAll(in));
            store.save(s);
            toast("已导入：" + s.name + "（" + s.steps.size() + " 步）");
            refresh();
        } catch (ScriptCodec.DecodeException e) {
            toast("导入失败：" + e.getMessage());
        } catch (Exception e) {
            toast("导入失败：" + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_IMPORT) {
            importFrom(data.getData());
        } else if (req == REQ_EXPORT && pendingExport != null) {
            try (OutputStream o = getContentResolver().openOutputStream(data.getData())) {
                if (o == null) throw new java.io.IOException("无法写入");
                o.write(store.raw(pendingExport).getBytes(StandardCharsets.UTF_8));
                toast("已导出");
            } catch (Exception e) {
                toast("导出失败：" + e.getMessage());
            }
            pendingExport = null;
        }
    }

    @SuppressWarnings("unused")
    private void noop(View v) { }
}
