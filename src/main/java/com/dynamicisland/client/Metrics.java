package com.dynamicisland.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** 性能与环境数据采集。所有取值都做了空值与异常保护。 */
public class Metrics {

    public static int fps = 60, tps = 20, ping = -1;
    public static float memRatio = 0f;
    public static long memUsedMb = 0, memMaxMb = 0;
    public static String coords = "", facing = "", biome = "", dimension = "", timeStr = "", speed = "", weather = "";
    /** 本次启动至今玩了多久（秒） */
    public static int playSeconds = 0;
    public static int entityCount = 0, particleCount = 0;
    /** 当前所处结构名，不在任何结构内则为空 */
    public static String structure = "";
    private static long startedAt = 0L;

    public static final int HISTORY = 64;
    public static final int[] fpsHistory = new int[HISTORY];
    public static int historyCount = 0, historyHead = 0;

    private static int tickCounter = 0;
    private static long tickWindowStart = 0;
    private static long lastSample = 0;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        if (startedAt == 0L) startedAt = now;
        playSeconds = (int) ((now - startedAt) / 1000L);

        // FPS：26.x 起 Minecraft#getFps() 已经是公开 API，不用再开 accessor mixin
        try { fps = mc.getFps(); } catch (Throwable ignored) { }

        // 客户端 TPS
        tickCounter++;
        if (tickWindowStart == 0) tickWindowStart = now;
        if (now - tickWindowStart >= 1000) {
            tps = Math.round(tickCounter * 1000f / (now - tickWindowStart));
            tickCounter = 0;
            tickWindowStart = now;
        }

        // 内存
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory(), total = rt.totalMemory(), free = rt.freeMemory();
        memUsedMb = (total - free) / 1048576L;
        memMaxMb = max / 1048576L;
        memRatio = max <= 0 ? 0f : Anim.clamp((total - free) / (float) max, 0f, 1f);

        if (now - lastSample >= 200) {
            lastSample = now;
            fpsHistory[historyHead] = fps;
            historyHead = (historyHead + 1) % HISTORY;
            if (historyCount < HISTORY) historyCount++;
        }

        LocalPlayer p = mc.player;
        Level lv = mc.level;
        if (p == null || lv == null) return;

        // 实体 / 粒子数：卡顿归因用，一眼看出是生物太多还是粒子炸了
        try { entityCount = mc.level.getEntityCount(); } catch (Throwable t) { entityCount = 0; }
        try {
            // 1.20.1 的 countParticles() 返回的是字符串
            particleCount = Integer.parseInt(mc.particleEngine.countParticles().trim());
        } catch (Throwable t) { particleCount = 0; }
        structure = structureName(lv, p.blockPosition());


        BlockPos pos = p.blockPosition();
        coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        facing = facingName(p.getYRot());

        try {
            String key = lv.getBiome(pos).unwrapKey()
                    .map(k -> k.identifier().getPath()).orElse("");
            biome = key.isEmpty() ? "—" : key;
        } catch (Throwable t) {
            biome = "—";
        }

        try {
            dimension = lv.dimension().identifier().getPath();
        } catch (Throwable t) {
            dimension = "?";
        }

        long day = dayTicks(lv) % 24000L;
        long h = (day / 1000L + 6) % 24;
        long mi = (day % 1000L) * 60L / 1000L;
        timeStr = String.format("%02d:%02d", h, mi);

        double v = p.getDeltaMovement().horizontalDistance() * 20.0 * 3.6;
        speed = String.format("%.1f km/h", v);

        int light = 0;
        try { light = lv.getMaxLocalRawBrightness(pos); } catch (Throwable ignored) { }
        weather = (lv.isRaining() ? "雨 " : "晴 ") + "光照 " + light;

        if (ping < 0 || (now % 4000 < 60)) ping = queryPing(mc);
    }

    private static int queryPing(Minecraft mc) {
        try {
            if (mc.getConnection() == null || mc.player == null) return -1;
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            return info == null ? -1 : info.getLatency();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String facingName(float yRot) {
        float f = ((yRot % 360f) + 360f) % 360f;
        if (f >= 315 || f < 45) return "S 南";
        if (f < 135) return "W 西";
        if (f < 225) return "N 北";
        return "E 东";
    }

    /** 按时间顺序读出折线数据 */
    /**
     * 当前所处结构名。这个 API 各版本名字不一（getStructureAt / structureAt），
     * 编译期直接引用容易翻车，所以走反射；拿不到就返回空，信息条会自动跳过这项。
     */
    private static String structureName(Level lv, BlockPos pos) {
        try {
            Object sm = lv.getClass().getMethod("structureManager").invoke(lv);
            if (sm == null) return "";
            java.lang.reflect.Method hit = null;
            for (java.lang.reflect.Method m : sm.getClass().getMethods()) {
                String n = m.getName();
                if ((n.equals("getStructureAt") || n.equals("structureAt")) && m.getParameterCount() == 1) {
                    hit = m;
                    break;
                }
            }
            if (hit == null) return "";
            Object start = hit.invoke(sm, pos);
            if (start == null) return "";
            try {
                Object st = start.getClass().getMethod("getStructure").invoke(start);
                if (st == null) return "";
                // 结构注册表字段名各版本不一（STRUCTURE_TYPES / STRUCTURES），反射挑一个能用的
                Class<?> bir = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
                java.lang.reflect.Field regField = null;
                for (java.lang.reflect.Field f : bir.getFields()) {
                    String fn = f.getName();
                    if (fn.equals("STRUCTURE_TYPES") || fn.equals("STRUCTURES")
                            || fn.equals("STRUCTURE_REGISTRY")) { regField = f; break; }
                }
                if (regField != null) {
                    Object reg = regField.get(null);
                    Object id = reg.getClass().getMethod("getKey", Object.class).invoke(reg, st);
                    if (id != null) {
                        String s = String.valueOf(id);
                        int c = s.indexOf(':');
                        String path = c >= 0 ? s.substring(c + 1) : s;
                        int slash = path.lastIndexOf('/');
                        if (slash >= 0) path = path.substring(slash + 1);
                        return path.replace('_', ' ');
                    }
                }
            } catch (Throwable ignored) { }
            return "";
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 26.x 的昼夜时间：优先读 Overworld 时钟（服务端会同步到客户端），
     * 拿不到再退回维度默认时钟，最后兜底用游戏刻（/time set 之后会有偏差，仅应急）。
     */
    public static long dayTicks(Level lv) {
        if (lv == null) return 0L;
        try {
            var clock = lv.registryAccess().get(net.minecraft.world.clock.WorldClocks.OVERWORLD);
            if (clock.isPresent()) {
                long t = lv.clockManager().getTotalTicks(clock.get());
                if (t > 0L) return t;
            }
        } catch (Throwable ignored) { }
        try {
            long t = lv.getDefaultClockTime();
            if (t > 0L) return t;
        } catch (Throwable ignored) { }
        try { return lv.getGameTime(); } catch (Throwable ignored) { return 0L; }
    }

    public static int[] ordered(int[] out) {
        int n = historyCount;
        for (int i = 0; i < n; i++) {
            int idx = (historyHead - n + i + HISTORY * 2) % HISTORY;
            out[i] = fpsHistory[idx];
        }
        return out;
    }
}
