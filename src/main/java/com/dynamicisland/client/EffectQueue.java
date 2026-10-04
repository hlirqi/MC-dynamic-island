package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 药水效果队列：按剩余时间排序，快结束的那个会被 UI 高亮闪烁。 */
public class EffectQueue {

    public static class Row {
        public MobEffectInstance inst;
        public String name;
        public int secs;
        public int color;
        public Row(MobEffectInstance i, String n, int s, int c) { inst = i; name = n; secs = s; color = c; }
    }

    private static final List<Row> rows = new ArrayList<>();

    public static List<Row> update(LocalPlayer p) {
        rows.clear();
        if (!Config.modEffects || p == null) return rows;
        try {
            for (MobEffectInstance e : p.getActiveEffects()) {
                int c = Config.theme.accent;
                try { c = e.getEffect().getColor(); } catch (Throwable ignored) { }
                String n = e.getEffect().getDisplayName().getString();
                if (e.getAmplifier() > 0) n += " " + roman(e.getAmplifier() + 1);
                rows.add(new Row(e, n, e.getDuration() / 20, c));
            }
            rows.sort(Comparator.comparingInt(r -> r.secs));
            while (rows.size() > 6) rows.remove(rows.size() - 1);
        } catch (Throwable ignored) { }
        return rows;
    }

    public static List<Row> rows() { return rows; }
    public static boolean active() { return Config.modEffects && !rows.isEmpty(); }

    private static String roman(int i) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return i >= 0 && i < r.length ? r[i] : String.valueOf(i);
    }
}
