package com.author.blank_mixin_mod.autotierlist.generation;

import com.author.blank_mixin_mod.autotierlist.config.AutoTierlistConfig;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Represents a group of related items in the tierlist.
 * Groups can be progression chains, tag-based groups, or isolated items.
 *
 * @param <T> The item data type (weapon or armor)
 */
public class ItemGroup<T> {
    private final GroupType type;
    private final List<T> items;
    private final Map<ResourceLocation, Integer> columnAssignments;

    // For TAG_GROUP type
    private final AutoTierlistConfig.TagEntry tagEntry;

    // For PROGRESSION_CHAIN type
    private final Set<ResourceLocation> chainItemIds;

    /**
     * Create a progression chain group.
     */
    public static <T> ItemGroup<T> progressionChain(List<T> items, Set<ResourceLocation> chainItemIds) {
        return new ItemGroup<>(GroupType.PROGRESSION_CHAIN, items, null, chainItemIds);
    }

    /**
     * Create a tag-based group.
     */
    public static <T> ItemGroup<T> tagGroup(List<T> items, AutoTierlistConfig.TagEntry tagEntry) {
        return new ItemGroup<>(GroupType.TAG_GROUP, items, tagEntry, null);
    }

    /**
     * Create an isolated items group.
     */
    public static <T> ItemGroup<T> isolated(List<T> items) {
        return new ItemGroup<>(GroupType.ISOLATED, items, null, null);
    }

    private ItemGroup(GroupType type, List<T> items,
                     AutoTierlistConfig.TagEntry tagEntry,
                     Set<ResourceLocation> chainItemIds) {
        this.type = type;
        this.items = new ArrayList<>(items);
        this.columnAssignments = new HashMap<>();
        this.tagEntry = tagEntry;
        this.chainItemIds = chainItemIds != null ? new HashSet<>(chainItemIds) : null;
    }

    public GroupType getType() {
        return type;
    }

    public List<T> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Map<ResourceLocation, Integer> getColumnAssignments() {
        return columnAssignments;
    }

    public void setColumnAssignment(ResourceLocation itemId, int column) {
        columnAssignments.put(itemId, column);
    }

    public AutoTierlistConfig.TagEntry getTagEntry() {
        return tagEntry;
    }

    public Set<ResourceLocation> getChainItemIds() {
        return chainItemIds != null ? Collections.unmodifiableSet(chainItemIds) : null;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    @Override
    public String toString() {
        return "ItemGroup{type=" + type + ", items=" + items.size() +
               (tagEntry != null ? ", tag=" + tagEntry.getLabel() : "") + "}";
    }
}
