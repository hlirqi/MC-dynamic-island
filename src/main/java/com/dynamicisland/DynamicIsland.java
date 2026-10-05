package com.dynamicisland;

import com.dynamicisland.client.ClientEvents;
import com.dynamicisland.client.IslandScreen;
import com.dynamicisland.client.KeyBinds;
import com.dynamicisland.client.RenderUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(DynamicIsland.MODID)
public class DynamicIsland {
    public static final String MODID = "dynamicisland";
    public static final Logger LOG = LoggerFactory.getLogger("DynamicIsland");

    /**
     * NeoForge 用构造器注入拿到 mod 事件总线与容器（不再有 FMLJavaModLoadingContext /
     * ModLoadingContext.registerConfig）。
     */
    public DynamicIsland(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            // 显式声明成 Supplier，避开 registerExtensionPoint 两个重载的歧义
            java.util.function.Supplier<IConfigScreenFactory> screen =
                    () -> (IConfigScreenFactory) (modContainer, modListScreen) -> new IslandScreen(modListScreen);
            container.registerExtensionPoint(IConfigScreenFactory.class, screen);

            modBus.addListener(this::onRegisterKeys);
            modBus.addListener(this::onRegisterPipelines);
            // 配置在 mod 构造之后才载入，所以 bake() 必须挂到配置事件上（构造器里读会抛异常）
            modBus.addListener(this::onConfigLoad);
            modBus.addListener(this::onConfigReload);

            ClientEvents.INSTANCE.init();
            NeoForge.EVENT_BUS.register(ClientEvents.INSTANCE);
        }
    }

    private void onConfigLoad(ModConfigEvent.Loading e) {
        if (e.getConfig().getModId().equals(MODID)) Config.bake();
    }

    private void onConfigReload(ModConfigEvent.Reloading e) {
        if (e.getConfig().getModId().equals(MODID)) Config.bake();
    }

    private void onRegisterKeys(RegisterKeyMappingsEvent e) {
        KeyBinds.register(e);
    }

    /** 自绘圆角矩形 / 圆环用到的三角形扇、三角形带管线 */
    private void onRegisterPipelines(RegisterRenderPipelinesEvent e) {
        RenderUtil.registerPipelines(e);
    }
}
