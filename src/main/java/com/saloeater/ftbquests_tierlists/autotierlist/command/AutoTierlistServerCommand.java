package com.saloeater.ftbquests_tierlists.autotierlist.command;

import com.saloeater.ftbquests_tierlists.autotierlist.generation.TierlistGenerator;
import com.saloeater.ftbquests_tierlists.autotierlist.integration.EMIIntegration;
import com.saloeater.ftbquests_tierlists.autotierlist.mixin.FTBQuestsCommandsAccessor;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;


/**
 * Server commands for managing Auto-Tierlist generation.
 */
public class AutoTierlistServerCommand {

    /**
     * Register the /autotierlist server command.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("autotierlist")
            .requires(source -> source.hasPermission(2)) // Require OP permission
            .then(Commands.literal("generate")
                .then(Commands.argument("skipRecipes", StringArgumentType.greedyString())
                    .executes(context -> generate(context, StringArgumentType.getString(context, "skipRecipes"))))
                .executes(AutoTierlistServerCommand::generate))
            .then(Commands.literal("clear")
                .executes(AutoTierlistServerCommand::clear))
            .then(Commands.literal("dump_excluded_weapons")
                .executes(AutoTierlistServerCommand::dumpExcludedWeapons))
            .then(Commands.literal("export_missing_weapons")
                .executes(AutoTierlistServerCommand::exportMissingWeapons))
            .then(Commands.literal("connections")
                .then(Commands.argument("item", ResourceLocationArgument.id())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                        net.minecraftforge.registries.ForgeRegistries.ITEMS.getKeys(), builder))
                    .executes(AutoTierlistServerCommand::connections)))
            .executes(AutoTierlistServerCommand::help)
        );
    }

    /**
     * Generate tierlists.
     */
    private static int generate(CommandContext<CommandSourceStack> context) {
        return generate(context, null);
    }

    /**
     * Generate tierlists, optionally skipping extra recipes for this run only.
     *
     * @param skipRecipes Comma-separated recipe ID patterns to skip on top of the
     *                    configured skippedRecipePatterns, or null for none
     */
    private static int generate(CommandContext<CommandSourceStack> context, String skipRecipes) {
        MinecraftServer server = context.getSource().getServer();

        try {
            // Initialize EMI integration
            EMIIntegration.initialize();

            java.util.List<String> extraPatterns = new java.util.ArrayList<>();
            if (skipRecipes != null) {
                for (String pattern : skipRecipes.split(",")) {
                    String trimmed = pattern.trim();
                    if (!trimmed.isEmpty()) {
                        extraPatterns.add(trimmed);
                    }
                }
            }

            // Run on server thread
            server.execute(() -> {
                try {
                    if (!extraPatterns.isEmpty()) {
                        int accepted = EMIIntegration.setExtraSkippedRecipePatterns(extraPatterns);
                        context.getSource().sendSuccess(
                            () -> Component.literal("[Auto-Tierlist] Also skipping recipes matching "
                                + accepted + " pattern(s) for this run"),
                            true
                        );
                    }

                    TierlistGenerator generator = new TierlistGenerator();
                    generator.generateAll(server);

                    // Reload FTBQuests using mixin invoker
                    FTBQuestsCommandsAccessor.invokeDoReload(context.getSource());

                    context.getSource().sendSuccess(
                        () -> Component.literal("[Auto-Tierlist] Generation complete! Check the FTBQuests menu."),
                        true
                    );
                } catch (Exception e) {
                    context.getSource().sendFailure(
                        Component.literal("[Auto-Tierlist] Failed to generate: " + e.getMessage())
                    );
                    e.printStackTrace();
                } finally {
                    EMIIntegration.clearExtraSkippedRecipePatterns();
                }
            });

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("[Auto-Tierlist] Failed to generate: " + e.getMessage())
            );
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Clear all generated tierlists.
     */
    private static int clear(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();

        try {
            context.getSource().sendSuccess(
                () -> Component.literal("[Auto-Tierlist] Clearing existing chapters..."),
                true
            );

            // Run on server thread
            server.execute(() -> {
                try {
                    TierlistGenerator generator = new TierlistGenerator();
                    generator.clearAll(server);

                    context.getSource().sendSuccess(
                        () -> Component.literal("[Auto-Tierlist] Cleared! Quest files should be removed."),
                        true
                    );
                } catch (Exception e) {
                    context.getSource().sendFailure(
                        Component.literal("[Auto-Tierlist] Failed to clear: " + e.getMessage())
                    );
                    e.printStackTrace();
                }
            });

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("[Auto-Tierlist] Failed to clear: " + e.getMessage())
            );
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Dump excluded weapons to file.
     */
    private static int dumpExcludedWeapons(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();

        try {
            context.getSource().sendSuccess(
                () -> Component.literal("[Auto-Tierlist] Scanning for excluded weapons..."),
                true
            );

            // Run on server thread
            server.execute(() -> {
                try {
                    java.nio.file.Path outputPath = java.nio.file.Paths.get("excluded_weapons.txt");
                    java.util.List<String> lines = new java.util.ArrayList<>();

                    // Create item filter
                    com.saloeater.ftbquests_tierlists.autotierlist.config.ItemFilter filter =
                        new com.saloeater.ftbquests_tierlists.autotierlist.config.ItemFilter(
                            com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig.USE_ATTRIBUTE_DETECTION.get());
                    filter.loadSkippedItems(com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig.SKIPPED_ITEMS.get());
                    filter.loadWeaponTags(com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig.WEAPON_TAGS.get());
                    filter.loadWeaponItems(com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig.WEAPON_ITEMS.get());

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
                        () -> Component.literal("[Auto-Tierlist] Dumped " + finalTotalExcluded + " excluded weapons to: " + outputPath.toAbsolutePath()),
                        true
                    );
                } catch (Exception e) {
                    context.getSource().sendFailure(
                        Component.literal("[Auto-Tierlist] Failed to dump: " + e.getMessage())
                    );
                    e.printStackTrace();
                }
            });

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("[Auto-Tierlist] Failed to dump: " + e.getMessage())
            );
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Export untagged melee weapons as a KubeJS tag script and a DPS CSV.
     * A weapon is "missing" if it has attack damage, is not a pickaxe/shovel/hoe,
     * is not already in c:tools/melee_weapons, and is not covered by configured
     * weapon tags/items or the skip list.
     */
    private static int exportMissingWeapons(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();

        try {
            context.getSource().sendSuccess(
                () -> Component.literal("[Auto-Tierlist] Scanning for missing weapons..."),
                true
            );

            // Run on server thread
            server.execute(() -> {
                try {
                    // Filter with attribute detection off, so isWeapon reflects only explicit config
                    com.saloeater.ftbquests_tierlists.autotierlist.config.ItemFilter filter =
                        new com.saloeater.ftbquests_tierlists.autotierlist.config.ItemFilter(false);
                    filter.loadSkippedItems(com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig.SKIPPED_ITEMS.get());
                    filter.loadWeaponTags(com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig.WEAPON_TAGS.get());
                    filter.loadWeaponItems(com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig.WEAPON_ITEMS.get());

                    java.util.Map<String, java.util.List<com.saloeater.ftbquests_tierlists.autotierlist.analysis.ItemData.WeaponData>> missingByMod =
                        new java.util.TreeMap<>();

                    for (net.minecraft.world.item.Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
                        net.minecraft.resources.ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
                        if (itemId == null) continue;

                        // Skip non-handheld item types (placeable blocks, spawn eggs)
                        if (item instanceof net.minecraft.world.item.BlockItem
                            || item instanceof net.minecraft.world.item.SpawnEggItem) continue;

                        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
                        var modifiers = stack.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                        double damageModifier = modifiers
                            .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                            .stream()
                            .mapToDouble(net.minecraft.world.entity.ai.attributes.AttributeModifier::getAmount)
                            .sum();
                        if (damageModifier <= 0) continue;

                        if (filter.isSkipped(itemId)) continue;
                        if (filter.isWeapon(itemId, stack, true)) continue;
                        if (stack.is(com.saloeater.ftbquests_tierlists.autotierlist.analysis.WeaponClassifier.MELEE_WEAPONS_TAG)) continue;
                        if (com.saloeater.ftbquests_tierlists.autotierlist.analysis.WeaponClassifier.isToolLike(stack)) continue;

                        double speedModifier = modifiers
                            .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)
                            .stream()
                            .mapToDouble(net.minecraft.world.entity.ai.attributes.AttributeModifier::getAmount)
                            .sum();
                        double damage = net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE.getDefaultValue() + damageModifier;
                        double attackSpeed = net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED.getDefaultValue() + speedModifier;

                        missingByMod.computeIfAbsent(itemId.getNamespace(), k -> new java.util.ArrayList<>())
                            .add(new com.saloeater.ftbquests_tierlists.autotierlist.analysis.ItemData.WeaponData(itemId, stack, damage, attackSpeed));
                    }

                    for (var weapons : missingByMod.values()) {
                        weapons.sort(java.util.Comparator.comparingDouble(
                            com.saloeater.ftbquests_tierlists.autotierlist.analysis.ItemData.WeaponData::getDPS));
                    }

                    // Write KubeJS tag script
                    java.util.List<String> jsLines = new java.util.ArrayList<>();
                    jsLines.add("ServerEvents.tags(\"item\", e => {");
                    jsLines.add("    e.add(\"c:tools/melee_weapons\", [");
                    for (var entry : missingByMod.entrySet()) {
                        jsLines.add("        // " + entry.getKey());
                        for (var weapon : entry.getValue()) {
                            jsLines.add("        \"" + weapon.id() + "\", //" + (int) Math.floor(weapon.getDPS()));
                        }
                    }
                    jsLines.add("    ]);");
                    jsLines.add("})");

                    java.nio.file.Path jsPath = java.nio.file.Paths.get("kubejs", "server_scripts", "tags_tierlist.js");
                    java.nio.file.Files.createDirectories(jsPath.getParent());
                    java.nio.file.Files.write(jsPath, jsLines);

                    // Write DPS CSV
                    java.util.List<String> csvLines = new java.util.ArrayList<>();
                    csvLines.add("id,dps");
                    for (var weapons : missingByMod.values()) {
                        for (var weapon : weapons) {
                            csvLines.add(weapon.id() + "," + (int) Math.floor(weapon.getDPS()));
                        }
                    }
                    java.nio.file.Path csvPath = java.nio.file.Paths.get("missing_weapons_damage.csv");
                    java.nio.file.Files.write(csvPath, csvLines);

                    int totalMissing = missingByMod.values().stream().mapToInt(java.util.List::size).sum();
                    context.getSource().sendSuccess(
                        () -> Component.literal("[Auto-Tierlist] Exported " + totalMissing + " missing weapons from "
                            + missingByMod.size() + " mods to: " + jsPath.toAbsolutePath() + " and " + csvPath.toAbsolutePath()),
                        true
                    );
                } catch (Exception e) {
                    context.getSource().sendFailure(
                        Component.literal("[Auto-Tierlist] Failed to export: " + e.getMessage())
                    );
                    e.printStackTrace();
                }
            });

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("[Auto-Tierlist] Failed to export: " + e.getMessage())
            );
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * List the crafting-chain connections of a single item, with the recipe ID behind each one.
     * Recipes filtered out by skippedEmiCategories or skippedRecipePatterns are listed too,
     * marked as skipped, so the effect of those filters is visible.
     */
    private static int connections(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ResourceLocation itemId = ResourceLocationArgument.getId(context, "item");

        if (!net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(itemId)) {
            context.getSource().sendFailure(Component.literal("[Auto-Tierlist] Unknown item: " + itemId));
            return 0;
        }

        try {
            // Run on server thread
            server.execute(() -> {
                try {
                    EMIIntegration.initialize();
                    if (!EMIIntegration.isAvailable()) {
                        context.getSource().sendFailure(
                            Component.literal("[Auto-Tierlist] EMI is not available, cannot query recipes"));
                        return;
                    }

                    java.util.Set<ResourceLocation> pool = collectPoolItems();

                    var producedBy = EMIIntegration.getConnectionsForOutput(itemId, pool);
                    var usedIn = EMIIntegration.getConnectionsForInput(itemId, pool);

                    context.getSource().sendSuccess(
                        () -> Component.literal("§6=== Connections for " + itemId + " ===§r\n"
                            + "§7In tierlist pool: §f" + (pool.contains(itemId) ? "yes" : "no")
                            + " §7(pool size: §f" + pool.size() + "§7)"),
                        false
                    );

                    sendConnections(context, "Produced by", producedBy);
                    sendConnections(context, "Used in", usedIn);
                } catch (Exception e) {
                    context.getSource().sendFailure(
                        Component.literal("[Auto-Tierlist] Failed to list connections: " + e.getMessage())
                    );
                    e.printStackTrace();
                }
            });

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("[Auto-Tierlist] Failed to list connections: " + e.getMessage())
            );
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Print one direction of connections, one line per recipe.
     */
    private static void sendConnections(CommandContext<CommandSourceStack> context, String heading,
                                        java.util.List<EMIIntegration.RecipeConnection> connections) {
        long withPool = connections.stream().filter(c -> !c.items().isEmpty()).count();
        long linking = connections.stream().filter(c -> !c.items().isEmpty() && !c.skipped()).count();
        context.getSource().sendSuccess(
            () -> Component.literal("§e" + heading + ": §f" + withPool + "§f of " + connections.size()
                + " recipe(s) link to pool items, §f" + linking + "§f of those not skipped"),
            false
        );

        for (EMIIntegration.RecipeConnection connection : connections) {
            // Recipes that link to nothing in the pool create no connection, so they are not listed
            if (connection.items().isEmpty()) continue;

            String recipeId = connection.recipeId() == null ? "<no id>" : connection.recipeId().toString();
            String items = "§f" + connection.items().stream().map(ResourceLocation::toString)
                .collect(java.util.stream.Collectors.joining(", "));
            String status = connection.skipped() ? "§c[skipped] " : "";
            context.getSource().sendSuccess(
                () -> Component.literal("  " + status + "§b" + recipeId + " §7(" + connection.categoryId() + ")\n"
                    + "    " + items),
                false
            );
        }
    }

    /**
     * Collect the item IDs that make up the tierlist pool, using the same filters and
     * scanner the generators use, so connections match what generation would see.
     */
    private static java.util.Set<ResourceLocation> collectPoolItems() {
        com.saloeater.ftbquests_tierlists.autotierlist.config.ItemFilter filter =
            new com.saloeater.ftbquests_tierlists.autotierlist.config.ItemFilter(
                com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig.USE_ATTRIBUTE_DETECTION.get());
        filter.loadSkippedItems(com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig.SKIPPED_ITEMS.get());
        filter.loadWeaponTags(com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig.WEAPON_TAGS.get());
        filter.loadWeaponItems(com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig.WEAPON_ITEMS.get());
        filter.loadArmorTags(com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig.ARMOR_TAGS.get());
        filter.loadArmorItems(com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig.ARMOR_ITEMS.get());

        com.saloeater.ftbquests_tierlists.autotierlist.analysis.ItemScanner scanner =
            new com.saloeater.ftbquests_tierlists.autotierlist.analysis.ItemScanner(filter);

        java.util.Set<ResourceLocation> pool = new java.util.HashSet<>();
        scanner.scanWeapons().forEach(weapon -> pool.add(weapon.id()));
        scanner.scanArmor().forEach(armor -> pool.add(armor.id()));
        return pool;
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
            () -> Component.literal("§6=== Auto-Tierlist Commands (Server) ===\n")
                .append(Component.literal("§e/autotierlist generate [recipeId,recipeId...] §7- Generate tierlists, optionally skipping extra recipe IDs for this run\n"))
                .append(Component.literal("§e/autotierlist clear §7- Remove generated tierlist chapters\n"))
                .append(Component.literal("§e/autotierlist dump_excluded_weapons §7- Export excluded weapons to file\n"))
                .append(Component.literal("§e/autotierlist export_missing_weapons §7- Write KubeJS melee weapon tags + DPS csv\n"))
                .append(Component.literal("§e/autotierlist connections <item> §7- List an item's crafting-chain connections and their recipe IDs\n"))
                .append(Component.literal("§7Config: §fconfig/ftbquests_tierlists-common.toml")),
            false
        );
        return Command.SINGLE_SUCCESS;
    }
}
