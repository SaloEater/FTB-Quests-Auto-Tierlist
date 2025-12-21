package com.saloeater.ftbquests_tierlists.autotierlist.generation;

/**
 * Types of item groups in the tierlist.
 */
public enum GroupType {
    /**
     * A progression chain - items connected by crafting relationships.
     * Only used when progression alignment is enabled.
     */
    PROGRESSION_CHAIN,

    /**
     * Items grouped by an Armageddon tag.
     * Only used when progression alignment is disabled and Armageddon tags are configured.
     */
    TAG_GROUP,

    /**
     * Isolated items with no progression relationships or tag grouping.
     */
    ISOLATED
}
