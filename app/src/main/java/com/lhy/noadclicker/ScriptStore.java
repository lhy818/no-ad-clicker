package com.lhy.noadclicker;

import android.content.Context;

import com.lhy.noadclicker.core.Script;
import com.lhy.noadclicker.core.ScriptCodec;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Scripts kept as JSON files in app-private storage. */
public final class ScriptStore {
    private final File dir;

    public ScriptStore(Context c) {
        dir = new File(c.getFilesDir(), "scripts");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    private static String safe(String name) {
        String n = name == null ? "" : name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return n.isEmpty() ? "未命名方案" : n;
    }

    public List<String> names() {
        File[] fs = dir.listFiles((d, n) -> n.endsWith(".json"));
        List<String> out = new ArrayList<>();
        if (fs == null) return out;
        Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : fs) out.add(f.getName().substring(0, f.getName().length() - 5));
        return out;
    }

    public void save(Script s) throws IOException {
        s.name = safe(s.name);
        try (FileOutputStream o = new FileOutputStream(new File(dir, s.name + ".json"))) {
            o.write(ScriptCodec.encode(s).getBytes(StandardCharsets.UTF_8));
        }
    }

    public Script load(String name) throws IOException, ScriptCodec.DecodeException {
        try (FileInputStream in = new FileInputStream(new File(dir, safe(name) + ".json"))) {
            return ScriptCodec.decode(readAll(in));
        }
    }

    public String raw(String name) throws IOException {
        try (FileInputStream in = new FileInputStream(new File(dir, safe(name) + ".json"))) {
            return readAll(in);
        }
    }

    public boolean delete(String name) {
        return new File(dir, safe(name) + ".json").delete();
    }

    public static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > 2_000_000) throw new IOException("文件过大");
            b.write(buf, 0, n);
        }
        return b.toString(StandardCharsets.UTF_8.name());
    }
}
