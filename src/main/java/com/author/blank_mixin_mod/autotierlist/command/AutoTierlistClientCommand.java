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
            .then(Commands.literal("dump_excluded_weapons")
                .executes(AutoTierlistClientCommand::dumpExcludedWeapons))
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
     * Dump excluded weapons to file.
     */
    private static int dumpExcludedWeapons(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = getIntegratedServer(context);
        if (server == null) {
            return 0;
        }

        try {
            context.getSource().sendSuccess(
                () -> Component.literal("§e[Auto-Tierlist] Scanning for excluded weapons..."),
                true
            );

            // Run on server thread
            server.execute(() -> {
                try {
                    java.nio.file.Path outputPath = java.nio.file.Paths.get("excluded_weapons.txt");
                    java.util.List<String> lines = new java.util.ArrayList<>();

                    // Create item filter
                    com.author.blank_mixin_mod.autotierlist.config.ItemFilter filter =
                        new com.author.blank_mixin_mod.autotierlist.config.ItemFilter(useAttributeDetection);
                    filter.loadSkippedItems(SKIPPED_ITEMS.get());
                    filter.loadWeaponTags(WEAPON_TAGS.get());
                    filter.loadWeaponItems(WEAPON_ITEMS.get());

                    // Collect excluded weapons
                    java.util.Map<String, java.util.List<ExcludedWeaponInfo>> weaponsByMod = new java.util.TreeMap<>();

                    for (net.minecraft.world.item.Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
                        net.minecraft.resources.ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
                        if (itemId == null) continue;

                        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);

                        // Check if has attack damage
                        double attackDamage = stack.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                            .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                            .stream()
                            .mapToDouble(net.minecraft.world.entity.ai.attributes.AttributeModifier::getAmount)
                            .sum();

                        if (attackDamage > 0) {
                            // Check if excluded by filter
                            if (!filter.isWeapon(itemId, stack, true)) {
                                // Get tags
                                java.util.List<String> tags = stack.getTags()
                                    .map(tag -> tag.location().toString())
                                    .sorted()
                                    .toList();

                                String modId = itemId.getNamespace();
                                weaponsByMod.computeIfAbsent(modId, k -> new java.util.ArrayList<>())
                                    .add(new ExcludedWeaponInfo(itemId.toString(), attackDamage, tags));
                            }
                        }
                    }

                    // Write to file
                    lines.add("=== Excluded Weapons Report ===");
                    lines.add("Generated: " + java.time.LocalDateTime.now());
                    lines.add("Total mods with excluded weapons: " + weaponsByMod.size());
                    lines.add("");

                    int totalExcluded = 0;
                    for (java.util.Map.Entry<String, java.util.List<ExcludedWeaponInfo>> entry : weaponsByMod.entrySet()) {
                        String modId = entry.getKey();
                        java.util.List<ExcludedWeaponInfo> weapons = entry.getValue();
                        totalExcluded += weapons.size();

                        lines.add("=== Mod: " + modId + " (" + weapons.size() + " excluded weapons) ===");

                        for (ExcludedWeaponInfo info : weapons) {
                            lines.add("  " + info.itemId + " (Damage: " + String.format("%.1f", info.attackDamage) + ")");
                            if (info.tags.isEmpty()) {
                                lines.add("    Tags: <none>");
                            } else {
                                lines.add("    Tags: " + String.join(", ", info.tags));
                            }
                        }
                        lines.add("");
                    }

                    lines.add("=== Summary ===");
                    lines.add("Total excluded weapons: " + totalExcluded);

                    java.nio.file.Files.write(outputPath, lines);

                    int finalTotalExcluded = totalExcluded;
                    context.getSource().sendSuccess(
                        () -> Component.literal("§a[Auto-Tierlist] Dumped " + finalTotalExcluded + " excluded weapons to: " + outputPath.toAbsolutePath()),
                        true
                    );
                } catch (Exception e) {
                    context.getSource().sendFailure(
                        Component.literal("§c[Auto-Tierlist] Failed to dump: " + e.getMessage())
                    );
                    e.printStackTrace();
                }
            });

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[Auto-Tierlist] Failed to dump: " + e.getMessage())
            );
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Helper class for storing excluded weapon info.
     */
    private static class ExcludedWeaponInfo {
        final String itemId;
        final double attackDamage;
        final java.util.List<String> tags;

        ExcludedWeaponInfo(String itemId, double attackDamage, java.util.List<String> tags) {
            this.itemId = itemId;
            this.attackDamage = attackDamage;
            this.tags = tags;
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
                "§e/autotierlist dump_excluded_weapons §7- Export excluded weapons to file\n" +
                "§7Config: §fconfig/blank_mixin_mod-client.toml\n" +
                "§c§oNote: These commands only work in single-player worlds"),
            false
        );
        return Command.SINGLE_SUCCESS;
    }
}
