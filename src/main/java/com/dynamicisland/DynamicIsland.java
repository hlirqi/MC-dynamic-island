package com.dynamicisland;

import com.dynamicisland.client.ClientEvents;
import com.dynamicisland.client.KeyBinds;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(DynamicIsland.MODID)
public class DynamicIsland {
    public static final String MODID = "dynamicisland";
    public static final Logger LOG = LoggerFactory.getLogger("DynamicIsland");

    public DynamicIsland() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
            modBus.addListener(this::onClientSetup);
            modBus.addListener(this::onRegisterKeys);
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new com.dynamicisland.client.IslandScreen(parent)));
        }
        MinecraftForge.EVENT_BUS.register(new ServerBridge());
    }

    private void onClientSetup(FMLClientSetupEvent e) {
        Config.bake();
        MinecraftForge.EVENT_BUS.register(ClientEvents.INSTANCE);
        ClientEvents.INSTANCE.init();
    }

    private void onRegisterKeys(RegisterKeyMappingsEvent e) {
        KeyBinds.register(e);
    }

    /** 服务端不需要任何逻辑，占位以满足 mods.toml 的两侧加载检查 */
    public static class ServerBridge { }
}
