package com.lhy.noadclicker.core;

import java.util.ArrayList;
import java.util.List;

public final class Script {
    public String name = "未命名方案";
    public LoopPolicy loop = LoopPolicy.infinite();
    public final List<Step> steps = new ArrayList<>();
}
