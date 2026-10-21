package com.oceania.perf;

import com.oceania.worldgen.field.FieldFunction;
import com.oceania.worldgen.field.OceanFields;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;

public final class DeepRuleSkip implements RuleEvaluator {
	private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();

	private static final int BASALT_STONE_DEPTH = 128;

	private static final int BEDROCK_MARGIN = 8;

	private final RuleEvaluator delegate;
	private final MaterialRuleContext ctx;
	private final OceanFields fields;
	private final int bedrockFloor;

	private int lastX = Integer.MIN_VALUE;
	private int lastZ = Integer.MIN_VALUE;
	private int floor;

	public DeepRuleSkip(final RuleEvaluator delegate, final MaterialRuleContext ctx, final int minY) {
		this.delegate = delegate;
		this.ctx = ctx;
		this.fields = OceanFields.of(FieldFunction.seedFrom(ctx.getOrCreateRandomFactory(FieldFunction.TYPE_ID)));
		this.bedrockFloor = minY + BEDROCK_MARGIN;
	}

	@Override
	public BlockState tryApply(final int x, final int y, final int z) {

		if (y >= bedrockFloor && ctx.stoneDepthAbove() > BASALT_STONE_DEPTH) {
			if (x != lastX || z != lastZ) {
				lastX = x;
				lastZ = z;
				floor = Mth.floor(fields.floorAt(x, z));
			}
			if (floor - y >= PerfFlags.SURFACE_DEEP_SKIP_DEPTH) {
				if (PerfFlags.SURFACE_DEEP_SKIP_VERIFY) {
					BlockState actual = delegate.tryApply(x, y, z);
					if (actual == DEEPSLATE) {
						PerfFlags.DEEP_SKIP_OK.increment();
					} else {
						PerfFlags.recordDeepSkipMismatch(x, y, z, actual);
					}
					return actual;
				}
				PerfFlags.DEEP_SKIP_HITS.increment();
				return DEEPSLATE;
			}
		}
		return delegate.tryApply(x, y, z);
	}
}
