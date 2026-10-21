package com.oceania.item;

import com.oceania.registry.OceaniaEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.LevelAccessor;

public class JellyfishSpawnEggItem extends MultiTypeSpawnEggItem {
	private static final EntityType<? extends Mob>[] SPECIES = types();

	@SuppressWarnings("unchecked")
	private static EntityType<? extends Mob>[] types() {
		return new EntityType[]{
			OceaniaEntities.MOON_JELLY,
			OceaniaEntities.NETTLE_JELLY,
			OceaniaEntities.CROWN_JELLY,
		};
	}

	public JellyfishSpawnEggItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	protected EntityType<? extends Mob> chooseType(LevelAccessor level, BlockPos pos) {
		return SPECIES[level.getRandom().nextInt(SPECIES.length)];
	}
}
