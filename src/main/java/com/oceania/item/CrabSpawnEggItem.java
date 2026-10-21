package com.oceania.item;

import com.oceania.registry.OceaniaEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;

public class CrabSpawnEggItem extends MultiTypeSpawnEggItem {
	public CrabSpawnEggItem(Item.Properties properties) {
		super(properties);
	}

	public static EntityType<? extends Mob> crabFor(LevelReader level, BlockPos pos) {
		String biome = level.getBiome(pos).unwrapKey()
			.map(key -> key.identifier().getPath()).orElse("");
		return switch (biome) {
			case "reef", "lagoon", "sandy_shelf", "kelp_forest" -> OceaniaEntities.REEF_CRAB;
			case "abyssal_plain", "ridge", "trench" -> OceaniaEntities.KING_CRAB;
			case "island_sand", "island_jungle", "island_cliff", "island_barren" ->
				OceaniaEntities.COCONUT_CRAB;
			default -> byDepth(level, pos);
		};
	}

	private static EntityType<? extends Mob> byDepth(LevelReader level, BlockPos pos) {
		if (!level.getFluidState(pos).is(FluidTags.WATER)) {
			return OceaniaEntities.COCONUT_CRAB;
		}

		return pos.getY() < level.getSeaLevel() - 40
			? OceaniaEntities.KING_CRAB
			: OceaniaEntities.REEF_CRAB;
	}

	@Override
	protected EntityType<? extends Mob> chooseType(LevelAccessor level, BlockPos pos) {
		return crabFor(level, pos);
	}
}
