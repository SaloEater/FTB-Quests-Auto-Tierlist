package com.author.blank_mixin_mod.autotierlist.mixin;

import dev.ftb.mods.ftbquests.quest.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Quest.class)
public interface QuestAccessor {
    @Accessor("invisibleUntilCompleted")
    boolean getInvisibleUntilCompleted();

    @Accessor("invisibleUntilCompleted")
    void setInvisibleUntilCompleted(boolean value);
}
