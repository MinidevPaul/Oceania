package com.oceania.mixin.village;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerBabyTradeMixin {
	private static final int SHELL_PRICE = 5;
	private static final int SHELL_USES = 2;

	@Invoker("startTrading")
	protected abstract void oceaniaStartTrading(Player player);

	@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
	private void oceaniaBabyTrade(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		Villager self = (Villager) (Object) this;
		if (!self.isBaby()) {
			return;
		}

		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.VILLAGER_SPAWN_EGG) || !self.isAlive() || self.isTrading() || self.isSleeping()) {
			return;
		}

		if (!self.level().isClientSide()) {

			MerchantOffers current = self.getOffers();
			if (current.isEmpty()) {
				current.add(new MerchantOffer(
					new ItemCost(Items.EMERALD, SHELL_PRICE),
					new ItemStack(Items.NAUTILUS_SHELL),
					SHELL_USES, 0, 0.05F));
			}

			this.oceaniaStartTrading(player);
		}

		cir.setReturnValue(InteractionResult.SUCCESS);
	}

	@Inject(method = "ageBoundaryReached", at = @At("TAIL"))
	private void oceaniaClearBabyOffer(CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (!self.isBaby()) {
			self.setOffers(null);
		}
	}
}
