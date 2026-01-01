package com.saloeater.ftbquests_tierlists.autotierlist.mixin;

import dev.ftb.mods.ftbquests.command.FTBQuestsCommands;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = FTBQuestsCommands.class, remap = false)
public interface FTBQuestsCommandsAccessor {
    @Invoker("doReload")
    static int invokeDoReload(CommandSourceStack source) {
        throw new AssertionError();
    }
}
