package com.dynamicisland.command;

import com.dynamicisland.Config;
import com.dynamicisland.client.IslandRenderer;
import com.dynamicisland.client.Metrics;
import com.dynamicisland.client.Theme;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class IslandCommand {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("island")
                .then(Commands.literal("toggle").executes(c -> {
                    IslandRenderer.INSTANCE.visible = !IslandRenderer.INSTANCE.visible;
                    Config.enabled = IslandRenderer.INSTANCE.visible;
                    Config.save();
                    c.getSource().sendSuccess(() -> msg("dynamicisland.cmd.toggle",
                            Component.literal(String.valueOf(Config.enabled))), false);
                    return 1;
                }))
                .then(Commands.literal("expand").executes(c -> {
                    IslandRenderer.INSTANCE.forceExpand = !IslandRenderer.INSTANCE.forceExpand;
                    return 1;
                }))
                .then(Commands.literal("reset").executes(c -> {
                    Config.offsetX = 0; Config.offsetY = 8; Config.save();
                    c.getSource().sendSuccess(() -> msg("dynamicisland.cmd.reset"), false);
                    return 1;
                }))
                .then(Commands.literal("reload").executes(c -> {
                    Config.bake();
                    c.getSource().sendSuccess(() -> msg("dynamicisland.cmd.reload"), false);
                    return 1;
                }))
                .then(Commands.literal("info").executes(c -> {
                    c.getSource().sendSuccess(() -> msg("dynamicisland.cmd.info",
                            Component.literal(String.valueOf(Metrics.fps)),
                            Component.literal(String.valueOf((int) (Metrics.memRatio * 100))),
                            Component.literal(Config.theme.id)), false);
                    return 1;
                }))
                .then(Commands.literal("theme")
                        .executes(c -> {
                            StringBuilder sb = new StringBuilder();
                            for (Theme t : Theme.values()) sb.append(t.id).append(" ");
                            String list = sb.toString().trim();
                            c.getSource().sendSuccess(() -> Component.translatable("dynamicisland.cmd.theme.list",
                                    Component.literal(list)), false);
                            return 1;
                        })
                        .then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
                            String n = StringArgumentType.getString(c, "name");
                            Config.theme = Theme.byId(n);
                            Config.save();
                            String id = Config.theme.id;
                            c.getSource().sendSuccess(() -> Component.translatable("dynamicisland.cmd.theme",
                                    Component.literal(id)), false);
                            return 1;
                        })))
                .then(Commands.literal("pos")
                        .then(Commands.argument("x", IntegerArgumentType.integer(-2000, 2000))
                                .then(Commands.argument("y", IntegerArgumentType.integer(-2000, 2000))
                                        .executes(c -> {
                                            Config.offsetX = IntegerArgumentType.getInteger(c, "x");
                                            Config.offsetY = IntegerArgumentType.getInteger(c, "y");
                                            Config.save();
                                            return 1;
                                        }))))
                .executes(c -> {
                    c.getSource().sendSuccess(() -> msg("dynamicisland.cmd.usage"), false);
                    return 1;
                })
        );
    }

    private static Component msg(String key, Object... args) {
        try { return Component.translatable(key, args); }
        catch (Throwable t) { return Component.literal(key); }
    }
}
