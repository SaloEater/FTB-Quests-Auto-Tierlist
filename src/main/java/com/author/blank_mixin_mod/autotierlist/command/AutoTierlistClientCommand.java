package com.author.blank_mixin_mod.autotierlist.command;

import com.author.blank_mixin_mod.autotierlist.generation.TierlistGenerator;
import com.author.blank_mixin_mod.autotierlist.integration.EMIIntegration;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import dev.ftb.mods.ftbquests.command.FTBQuestsCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import static com.author.blank_mixin_mod.autotierlist.config.AutoTierlistConfig.*;

/**
 * Client commands for managing Auto-Tierlist generation.
 * These commands only work in single-player worlds.
 */
public class AutoTierlistClientCommand {

    /**
     * Register the /autotierlist client command.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("autotierlist")
            .then(Commands.literal("generate")
                .executes(AutoTierlistClientCommand::generate))
            .then(Commands.literal("clear")
                .executes(AutoTierlistClientCommand::clear))
            .then(Commands.literal("reload")
                .executes(AutoTierlistClientCommand::reload))
            .executes(AutoTierlistClientCommand::help)
        );
    }

    /**
     * Get the integrated server from the client.
     * Returns null if not in single-player or server is not available.
     */
    private static MinecraftServer getIntegratedServer(CommandContext<CommandSourceStack> context) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null) {
            context.getSource().sendFailure(
                Component.literal("§c[Auto-Tierlist] No player found")
            );
            return null;
        }

        if (!mc.hasSingleplayerServer()) {
            context.getSource().sendFailure(
                Component.literal("§c[Auto-Tierlist] This command only works in single-player worlds")
            );
            return null;
        }

        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            context.getSource().sendFailure(
                Component.literal("§c[Auto-Tierlist] Failed to access integrated server")
            );
            return null;
        }

        return server;
    }

    /**
     * Generate tierlists.
     */
    private static int generate(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = getIntegratedServer(context);
        if (server == null) {
            return 0;
        }

        try {
            context.getSource().sendSuccess(
                () -> Component.literal("§e[Auto-Tierlist] Starting generation..."),
                true
            );

            // Initialize EMI integration
            EMIIntegration.initialize();

            // Run on server thread
            server.execute(() -> {
                try {
                    TierlistGenerator generator = new TierlistGenerator();
                    generator.generateAll(server);

                    context.getSource().sendSuccess(
                        () -> Component.literal("§a[Auto-Tierlist] Generation complete! Check the FTBQuests menu."),
                        true
                    );
                } catch (Exception e) {
                    context.getSource().sendFailure(
                        Component.literal("§c[Auto-Tierlist] Failed to generate: " + e.getMessage())
                    );
                    e.printStackTrace();
                }
            });

            Minecraft.getInstance().player.connection.sendCommand("ftbquests reload");

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[Auto-Tierlist] Failed to generate: " + e.getMessage())
            );
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Clear all generated tierlists.
     */
    private static int clear(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = getIntegratedServer(context);
        if (server == null) {
            return 0;
        }

        try {
            context.getSource().sendSuccess(
                () -> Component.literal("§e[Auto-Tierlist] Clearing existing chapters..."),
                true
            );

            // Run on server thread
            server.execute(() -> {
                try {
                    TierlistGenerator generator = new TierlistGenerator();
                    generator.clearAll(server);

                    context.getSource().sendSuccess(
                        () -> Component.literal("§a[Auto-Tierlist] Cleared! Quest files should be removed."),
                        true
                    );
                } catch (Exception e) {
                    context.getSource().sendFailure(
                        Component.literal("§c[Auto-Tierlist] Failed to clear: " + e.getMessage())
                    );
                    e.printStackTrace();
                }
            });

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[Auto-Tierlist] Failed to clear: " + e.getMessage())
            );
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Reload config and regenerate.
     */
    private static int reload(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = getIntegratedServer(context);
        if (server == null) {
            return 0;
        }

        try {
            context.getSource().sendSuccess(
                () -> Component.literal("§e[Auto-Tierlist] Reloading config..."),
                true
            );

            // Refresh cached config values
            // Note: Config file changes are automatically detected by Forge
            // This ensures our cached values match the current config state
            refreshCachedValues();

            context.getSource().sendSuccess(
                () -> Component.literal("§e[Auto-Tierlist] Clearing old chapters and regenerating..."),
                true
            );

            // Run on server thread
            server.execute(() -> {
                try {
                    // Initialize EMI integration
                    EMIIntegration.initialize();

                    // Regenerate with new config values
                    TierlistGenerator generator = new TierlistGenerator();
                    generator.generateAll(server);

                    context.getSource().sendSuccess(
                        () -> Component.literal("§a[Auto-Tierlist] Reload complete! Config applied and tierlists regenerated."),
                        true
                    );
                } catch (Exception e) {
                    context.getSource().sendFailure(
                        Component.literal("§c[Auto-Tierlist] Failed to reload: " + e.getMessage())
                    );
                    e.printStackTrace();
                }
            });

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[Auto-Tierlist] Failed to reload: " + e.getMessage())
            );
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Show help message.
     */
    private static int help(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
            () -> Component.literal("§6=== Auto-Tierlist Commands (Client) ===\n" +
                "§e/autotierlist generate §7- Generate weapon and armor tierlists\n" +
                "§e/autotierlist clear §7- Remove generated tierlist chapters\n" +
                "§e/autotierlist reload §7- Clear and regenerate with current config\n" +
                "§7Config: §fconfig/blank_mixin_mod-client.toml\n" +
                "§c§oNote: These commands only work in single-player worlds"),
            false
        );
        return Command.SINGLE_SUCCESS;
    }
}
