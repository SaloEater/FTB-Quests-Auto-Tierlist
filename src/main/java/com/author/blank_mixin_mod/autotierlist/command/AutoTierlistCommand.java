package com.author.blank_mixin_mod.autotierlist.command;

import com.author.blank_mixin_mod.autotierlist.generation.TierlistGenerator;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Commands for managing Auto-Tierlist generation.
 */
public class AutoTierlistCommand {

    /**
     * Register the /autotierlist command.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("autotierlist")
            .requires(source -> source.hasPermission(2)) // Requires OP level 2
            .then(Commands.literal("generate")
                .executes(AutoTierlistCommand::generate))
            .then(Commands.literal("clear")
                .executes(AutoTierlistCommand::clear))
            .then(Commands.literal("reload")
                .executes(AutoTierlistCommand::reload))
            .executes(AutoTierlistCommand::help)
        );
    }

    /**
     * Generate tierlists.
     */
    private static int generate(CommandContext<CommandSourceStack> context) {
        try {
            context.getSource().sendSuccess(
                () -> Component.literal("§e[Auto-Tierlist] Starting generation..."),
                true
            );

            TierlistGenerator generator = new TierlistGenerator();
            generator.generateAll(context.getSource().getServer());

            context.getSource().sendSuccess(
                () -> Component.literal("§a[Auto-Tierlist] Generation complete! Check the FTBQuests menu."),
                true
            );

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
        try {
            context.getSource().sendSuccess(
                () -> Component.literal("§e[Auto-Tierlist] Clearing existing chapters..."),
                true
            );

            TierlistGenerator generator = new TierlistGenerator();
            generator.clearAll(context.getSource().getServer());

            context.getSource().sendSuccess(
                () -> Component.literal("§a[Auto-Tierlist] Cleared! Quest files should be removed."),
                true
            );

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
        try {
            context.getSource().sendSuccess(
                () -> Component.literal("§e[Auto-Tierlist] Clearing old chapters and regenerating..."),
                true
            );

            // Config is automatically reloaded by Forge when modified
            // Just regenerate (which includes cleanup)
            TierlistGenerator generator = new TierlistGenerator();
            generator.generateAll(context.getSource().getServer());

            context.getSource().sendSuccess(
                () -> Component.literal("§a[Auto-Tierlist] Reload complete! Config applied and tierlists regenerated."),
                true
            );

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
            () -> Component.literal("§6=== Auto-Tierlist Commands ===\n" +
                "§e/autotierlist generate §7- Generate weapon and armor tierlists\n" +
                "§e/autotierlist clear §7- Remove generated tierlist chapters\n" +
                "§e/autotierlist reload §7- Clear and regenerate with current config\n" +
                "§7Config: §fconfig/blank_mixin_mod-common.toml"),
            false
        );
        return Command.SINGLE_SUCCESS;
    }
}
