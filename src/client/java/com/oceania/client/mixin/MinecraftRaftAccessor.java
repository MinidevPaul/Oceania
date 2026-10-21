package com.oceania.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftRaftAccessor {
	@Accessor("rightClickDelay")
	void oceania$setRightClickDelay(int delay);

	@Accessor("missTime")
	int oceania$missTime();
}
