package com.oceania.mixin.perf;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.oceania.perf.DeepRuleSkip;
import com.oceania.perf.PerfFlags;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MaterialSystem.class)
public class MaterialSystemDeepSkipMixin {
	@WrapOperation(
		method = "buildSurface",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/material/rule/MaterialRule;compile(Lnet/minecraft/world/level/levelgen/material/MaterialRuleContext;)Lnet/minecraft/world/level/levelgen/material/rule/RuleEvaluator;"
		)
	)
	private RuleEvaluator oceania$wrapDeepSkip(final MaterialRule rule, final MaterialRuleContext context,
			final Operation<RuleEvaluator> original, @Local(argsOnly = true) final ChunkAccess protoChunk) {
		RuleEvaluator compiled = original.call(rule, context);
		if (!PerfFlags.SURFACE_DEEP_SKIP) {
			return compiled;
		}
		PerfFlags.DEEP_SKIP_CHUNKS.increment();
		return new DeepRuleSkip(compiled, context, protoChunk.getMinY());
	}
}
