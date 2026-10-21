package com.lhy.noadclicker.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** JSON (de)serialisation. Decoding is all-or-nothing. */
public final class ScriptCodec {
    public static final String FORMAT = "noadclicker";
    public static final int VERSION = 1;

    public static final class DecodeException extends Exception {
        public DecodeException(String m) { super(m); }
    }

    private ScriptCodec() {}

    public static String encode(Script s) {
        try {
            JSONObject o = new JSONObject();
            o.put("format", FORMAT);
            o.put("version", VERSION);
            o.put("name", s.name);
            JSONObject l = new JSONObject();
            l.put("mode", s.loop.mode.name());
            l.put("count", s.loop.count);
            l.put("durationMs", s.loop.durationMs);
            o.put("loop", l);
            JSONArray arr = new JSONArray();
            for (Step st : s.steps) {
                JSONObject j = new JSONObject();
                j.put("type", st.type.name());
                j.put("x", st.x); j.put("y", st.y);
                if (st.type == Step.Type.SWIPE) { j.put("x2", st.x2); j.put("y2", st.y2); }
                j.put("pressMs", st.pressMs);
                j.put("delayAfterMs", st.delayAfterMs);
                arr.put(j);
            }
            o.put("steps", arr);
            return o.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Script decode(String text) throws DecodeException {
        try {
            JSONObject o = new JSONObject(text);
            if (!FORMAT.equals(o.optString("format"))) throw new DecodeException("不是本应用的方案文件");
            if (o.optInt("version", -1) != VERSION) throw new DecodeException("不支持的方案版本");
            Script s = new Script();
            s.name = o.optString("name", s.name);
            JSONObject l = o.getJSONObject("loop");
            LoopPolicy p = new LoopPolicy();
            try { p.mode = LoopPolicy.Mode.valueOf(l.getString("mode")); }
            catch (IllegalArgumentException e) { throw new DecodeException("未知循环模式"); }
            p.count = l.optInt("count", 1);
            p.durationMs = l.optLong("durationMs", 60_000);
            String err = p.validate();
            if (err != null) throw new DecodeException(err);
            s.loop = p;
            JSONArray arr = o.getJSONArray("steps");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.getJSONObject(i);
                Step st = new Step();
                try { st.type = Step.Type.valueOf(j.getString("type")); }
                catch (IllegalArgumentException e) { throw new DecodeException("第 " + (i + 1) + " 步类型未知"); }
                st.x = j.getInt("x"); st.y = j.getInt("y");
                if (st.type == Step.Type.SWIPE) { st.x2 = j.getInt("x2"); st.y2 = j.getInt("y2"); }
                st.pressMs = j.getLong("pressMs");
                st.delayAfterMs = j.getLong("delayAfterMs");
                String e2 = st.validate();
                if (e2 != null) throw new DecodeException("第 " + (i + 1) + " 步：" + e2);
                s.steps.add(st);
            }
            return s;
        } catch (JSONException e) {
            throw new DecodeException("文件格式错误：" + e.getMessage());
        }
    }
}
