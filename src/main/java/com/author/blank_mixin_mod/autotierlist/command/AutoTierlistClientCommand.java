package com.author.blank_mixin_mod.autotierlist.command;

import com.author.blank_mixin_mod.autotierlist.generation.TierlistGenerator;
import com.author.blank_mixin_mod.autotierlist.integration.EMIIntegration;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import dev.ftb.mods.ftbquests.command.FTBQuestsCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandRuntimeException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;


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
                .then(Commands.argument("mode", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        builder.suggest("crafting_progression");
                        builder.suggest("tier_progression");
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        String mode = StringArgumentType.getString(ctx, "mode");
                        if (mode.equals("crafting_progression")) {
                            return generateCraftingProgression(ctx);
                        } else if (mode.equals("tier_progression")) {
                            return generate(ctx);
                        }
                        throw new CommandRuntimeException(Component.translatable("blank_mixin_mod.command.autotierlist.invalid_mode"));
                    })))
            .then(Commands.literal("clear")
                .executes(AutoTierlistClientCommand::clear))
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
                Component.translatable("blank_mixin_mod.command.autotierlist.no_player")
            );
            return null;
        }

        if (!mc.hasSingleplayerServer()) {
            context.getSource().sendFailure(
                Component.translatable("blank_mixin_mod.command.autotierlist.singleplayer_only")
            );
            return null;
        }

        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            context.getSource().sendFailure(
                Component.translatable("blank_mixin_mod.command.autotierlist.no_server")
            );
            return null;
        }

        return server;
    }

    /**
     * Generate tierlists (default - tier progression mode).
     */
    private static int generate(CommandContext<CommandSourceStack> context) {
        return generateWithMode(context, false);
    }

    /**
     * Generate tierlists (crafting progression mode).
     */
    private static int generateCraftingProgression(CommandContext<CommandSourceStack> context) {
        return generateWithMode(context, true);
    }

    /**
     * Generate tierlists with specified progression mode.
     */
    private static int generateWithMode(CommandContext<CommandSourceStack> context, boolean enableCraftingProgression) {
        MinecraftServer server = getIntegratedServer(context);
        if (server == null) {
            return 0;
        }

        try {
            String modeKey = enableCraftingProgression ? "blank_mixin_mod.command.autotierlist.mode.crafting" : "blank_mixin_mod.command.autotierlist.mode.tier";
            context.getSource().sendSuccess(
                () -> Component.translatable("blank_mixin_mod.command.autotierlist.generate.starting", Component.translatable(modeKey)),
                true
            );

            // Initialize EMI integration
            EMIIntegration.initialize();

            // Run on server thread
            server.execute(() -> {
                try {
                    TierlistGenerator generator = new TierlistGenerator();
                    generator.generateAll(server, enableCraftingProgression);

                    context.getSource().sendSuccess(
                        () -> Component.translatable("blank_mixin_mod.command.autotierlist.generate.complete"),
                        true
                    );
                } catch (Exception e) {
                    context.getSource().sendFailure(
                        Component.translatable("blank_mixin_mod.command.autotierlist.generate.failed", e.getMessage())
                    );
                    e.printStackTrace();
                }
            });

            Minecraft.getInstance().player.connection.sendCommand("ftbquests reload");

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.translatable("blank_mixin_mod.command.autotierlist.generate.failed", e.getMessage())
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
                () -> Component.translatable("blank_mixin_mod.command.autotierlist.clear.starting"),
                true
            );

            // Run on server thread
            server.execute(() -> {
                try {
                    TierlistGenerator generator = new TierlistGenerator();
                    generator.clearAll(server);

                    context.getSource().sendSuccess(
                        () -> Component.translatable("blank_mixin_mod.command.autotierlist.clear.complete"),
                        true
                    );
                } catch (Exception e) {
                    context.getSource().sendFailure(
                        Component.translatable("blank_mixin_mod.command.autotierlist.clear.failed", e.getMessage())
                    );
                    e.printStackTrace();
                }
            });

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.translatable("blank_mixin_mod.command.autotierlist.clear.failed", e.getMessage())
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
                () -> Component.translatable("blank_mixin_mod.command.autotierlist.dump.starting"),
                true
            );

            // Run on server thread
            server.execute(() -> {
                try {
                    java.nio.file.Path outputPath = java.nio.file.Paths.get("excluded_weapons.txt");
                    java.util.List<String> lines = new java.util.ArrayList<>();

                    // Create item filter
                    com.author.blank_mixin_mod.autotierlist.config.ItemFilter filter =
                        new com.author.blank_mixin_mod.autotierlist.config.ItemFilter(
                            com.author.blank_mixin_mod.autotierlist.config.AutoTierlistConfig.USE_ATTRIBUTE_DETECTION.get());
                    filter.loadSkippedItems(com.author.blank_mixin_mod.autotierlist.config.AutoTierlistConfig.SKIPPED_ITEMS.get());
                    filter.loadWeaponTags(com.author.blank_mixin_mod.autotierlist.config.AutoTierlistConfig.WEAPON_TAGS.get());
                    filter.loadWeaponItems(com.author.blank_mixin_mod.autotierlist.config.AutoTierlistConfig.WEAPON_ITEMS.get());

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
                        () -> Component.translatable("blank_mixin_mod.command.autotierlist.dump.complete", finalTotalExcluded, outputPath.toAbsolutePath()),
                        true
                    );
                } catch (Exception e) {
                    context.getSource().sendFailure(
                        Component.translatable("blank_mixin_mod.command.autotierlist.dump.failed", e.getMessage())
                    );
                    e.printStackTrace();
                }
            });

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.translatable("blank_mixin_mod.command.autotierlist.dump.failed", e.getMessage())
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
            () -> Component.translatable("blank_mixin_mod.command.autotierlist.help.header")
                .append(Component.literal("\n"))
                .append(Component.translatable("blank_mixin_mod.command.autotierlist.help.generate"))
                .append(Component.literal("\n"))
                .append(Component.translatable("blank_mixin_mod.command.autotierlist.help.generate.crafting"))
                .append(Component.literal("\n"))
                .append(Component.translatable("blank_mixin_mod.command.autotierlist.help.generate.tier"))
                .append(Component.literal("\n"))
                .append(Component.translatable("blank_mixin_mod.command.autotierlist.help.clear"))
                .append(Component.literal("\n"))
                .append(Component.translatable("blank_mixin_mod.command.autotierlist.help.dump"))
                .append(Component.literal("\n"))
                .append(Component.translatable("blank_mixin_mod.command.autotierlist.help.config"))
                .append(Component.literal("\n"))
                .append(Component.translatable("blank_mixin_mod.command.autotierlist.help.note")),
            false
        );
        return Command.SINGLE_SUCCESS;
    }
}
