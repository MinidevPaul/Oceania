package com.oceania.debug;

import com.oceania.Oceania;
import com.oceania.raft.RaftBlocks;
import java.util.ArrayList;
import net.minecraft.core.SectionPos;
import com.oceania.raft.RaftWorldLight;
import com.oceania.raft.RaftEntity;
import com.oceania.raft.RaftInteraction;
import com.oceania.raft.RaftItem;
import com.oceania.raft.RaftItems;
import com.oceania.raft.RaftLayouts;
import com.oceania.raft.RaftLevel;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public final class RaftSmoke {
	private static final boolean ENABLED = "raft".equals(System.getProperty("oceania.smoke", ""));
	private static final BlockPos CHEST = new BlockPos(2, 1, 2);
	private static final BlockPos FURNACE = new BlockPos(2, 1, 0);
	private static final BlockPos FENCE_A = new BlockPos(0, 1, 2);
	private static final BlockPos FENCE_B = new BlockPos(1, 1, 2);
	private static final BlockPos EXTRA = new BlockPos(3, 0, 1);
	private static final BlockPos TOO_FAR = new BlockPos(40, 0, 0);

	private static final BlockPos SPARE = new BlockPos(1, 1, 0);
	private static final BlockPos CORE = new BlockPos(1, 1, 1);

	private static final BlockPos CAMPFIRE = new BlockPos(0, 1, 1);
	private static final BlockPos DECK_HOPPER = new BlockPos(0, 2, 1);

	private static final BlockPos BREWING = new BlockPos(2, 1, 1);

	private static final BlockPos TORCH = new BlockPos(0, 1, 0);

	private static final BlockPos TOP_CHEST = new BlockPos(3, 3, 1);
	private static final BlockPos CHEST_HOPPER = new BlockPos(3, 2, 1);
	private static final BlockPos BOTTOM_CHEST = new BlockPos(3, 1, 1);
	private static ItemEntity deckItem;
	private static Mob sitter;

	private static RaftEntity raft;
	private static ItemEntity item;
	private static Vec3 startPos;
	private static Vec3 wallCentre;
	private static int tick = -1;
	private static int failures;
	private static Rider rider;
	private static Rider pufferRider;
	private static net.minecraft.world.entity.animal.fish.Pufferfish puffer;
	private static float pufferHealth;
	private static Vec3 riderStart;
	private static float riderYaw;

	private static final class Rider extends ServerPlayer {
		Rider(ServerLevel level) {
			super(level.getServer(), level, new GameProfile(UUID.nameUUIDFromBytes("oceania-rider".getBytes(StandardCharsets.UTF_8)), "oceania_rider"),
				ClientInformation.createDefault());
			this.connection = new ServerGamePacketListenerImpl(level.getServer(), new net.minecraft.network.Connection(PacketFlow.CLIENTBOUND), this,
					CommonListenerCookie.createInitial(this.getGameProfile(), false)) {
				@Override
				public void send(Packet<?> packet, ChannelFutureListener listener) {
				}
			};
		}
	}

	private RaftSmoke() {
	}

	public static void init() {
		if (!ENABLED) return;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			BlockPos spawn = findOpenWater(level, level.getRespawnData().pos());
			if (spawn == null) {
				Oceania.LOGGER.error("[smoke] no open water near spawn {}; run on the deep1024 world (-Doceania.height=1024)", level.getRespawnData().pos());
				server.halt(false);
				return;
			}
			double surface = waterSurface(level, spawn);
			surfaceY = surface;

			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) level.setChunkForced((spawn.getX() >> 4) + dx, (spawn.getZ() >> 4) + dz, true);
			}
			startPos = new Vec3(spawn.getX() + 0.5, surface - RaftEntity.DRAFT, spawn.getZ() + 0.5);
			raft = RaftItem.spawn(level, startPos, 0, RaftLayouts.starter());
			Oceania.LOGGER.info("[smoke] spawned raft {} at {} bb={}", raft.getId(), raft.position(), raft.getBoundingBox());
			Vec3 deck = raft.toWorld(new Vec3(0.5, 2.0, 0.5));
			item = new ItemEntity(level, deck.x, deck.y, deck.z, new ItemStack(Items.OAK_PLANKS));
			item.setDeltaMovement(Vec3.ZERO);
			level.addFreshEntity(item);
			com.oceania.raft.RaftCollider.debug = true;
			tick = 0;
		});
		ServerTickEvents.END_SERVER_TICK.register(RaftSmoke::tick);
	}

	private static void tick(MinecraftServer server) {
		if (tick < 0 || raft == null) return;
		tick++;
		if (tick <= 20) {
			raft.setDebugInput(0, 0);
		} else if (tick <= 80) {
			raft.setDebugInput(0, 1);
		} else if (tick <= 120) {
			raft.setDebugInput(1, 0);
		} else if (tick <= 240) {
			raft.setDebugInput(0, 0);
		} else if (tick <= 300) {
			raft.setDebugInput(0, 1);
		} else {
			raft.setDebugInput(0, 0);
		}
		if (tick <= 40 && tick % 5 == 0) {
			Oceania.LOGGER.info("[smoke] t={} item world={} local={} onGround={} raft={}", tick, item.position(), raft.toLocal(item.position()), item.onGround(), raft.position());
		}
		if (tick == 20) {
			Vec3 local = raft.toLocal(item.position());
			check(Math.abs(local.y - 1.0) < 0.3 && item.onGround(), String.format("static deck holds item: local %s onGround=%s", local, item.onGround()));
			com.oceania.raft.RaftCollider.debug = false;
			build();
		}
		if (tick == 60) {
			ItemStack moved = level(BOTTOM_CHEST) instanceof ChestBlockEntity bottom ? bottom.getItem(0) : ItemStack.EMPTY;
			ItemStack left = level(TOP_CHEST) instanceof ChestBlockEntity top ? top.getItem(4) : ItemStack.EMPTY;
			check(moved.is(Items.COBBLESTONE) && left.isEmpty(), "hopper moved the cobblestone chest to chest while sailing: bottom " + moved + ", top " + left);
			boolean sucked = false;
			if (level(DECK_HOPPER) instanceof HopperBlockEntity hopper) {
				for (int slot = 0; slot < hopper.getContainerSize() && !sucked; slot++) sucked = hopper.getItem(slot).is(Items.FLINT);
			}
			check(sucked && deckItem.isRemoved(), "hopper took the flint off the deck: sucked=" + sucked + " item removed=" + deckItem.isRemoved());
		}
		if (tick == 340) {

			puffer = EntityTypes.PUFFERFISH.create(server.overworld(), EntitySpawnReason.COMMAND);
			puffer.setPuffState(2);
			pufferRider = new Rider(server.overworld());
			pufferRider.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			Vec3 seat = raft.toWorld(new Vec3(CORE.getX() + 0.5, CORE.getY() + 1.0, CORE.getZ() + 0.5));
			pufferRider.setPos(seat.x, seat.y, seat.z);
			check(raft.sit(pufferRider, CORE), "puffer test: rider seated at the helm");

			Vec3 alongside = raft.toWorld(new Vec3(1.5, 0.3, -0.6));
			puffer.setPos(alongside.x, alongside.y, alongside.z);
			server.overworld().addFreshEntity(puffer);

			AABB mine = pufferRider.getBoundingBox().inflate(1.0, 0.0, 1.0);
			AABB union = pufferRider.getBoundingBox().minmax(raft.getBoundingBox()).inflate(1.0, 0.0, 1.0);
			check(union.intersects(puffer.getBoundingBox()) && !mine.intersects(puffer.getBoundingBox()),
				"puffer test: a hull fish is inside the old rider+raft box and outside the rider's own box");
			pufferHealth = pufferRider.getHealth();
			pufferRider.aiStep();
		}
		if (tick == 342) {
			check(pufferRider.getHealth() == pufferHealth,
				String.format("a pufferfish against the HULL does not touch the driver: health %.1f, was %.1f",
					pufferRider.getHealth(), pufferHealth));

			Vec3 lap = pufferRider.position();
			puffer.setPos(lap.x, lap.y + 0.5, lap.z);
			AABB mine = pufferRider.getBoundingBox().inflate(1.0, 0.0, 1.0);
			check(mine.intersects(puffer.getBoundingBox()),
				"a pufferfish in the driver's own box is still inside the touch box: " + mine.intersects(puffer.getBoundingBox()));
			pufferRider.stopRiding();
			pufferRider.discard();
			puffer.discard();
		}
		if (tick == 440) {
			ItemStack bottle = level(BREWING) instanceof BrewingStandBlockEntity stand ? stand.getItem(0) : ItemStack.EMPTY;
			PotionContents contents = bottle.get(DataComponents.POTION_CONTENTS);
			check(contents != null && contents.is(Potions.AWKWARD), "brewing stand brewed an awkward potion while sailing: " + bottle + " " + contents);
			BlockState stand = raft.raftLevel().getBlockState(BREWING);
			check(stand.hasProperty(net.minecraft.world.level.block.BrewingStandBlock.HAS_BOTTLE[0]) && stand.getValue(net.minecraft.world.level.block.BrewingStandBlock.HAS_BOTTLE[0]),
				"the stand shows its bottle: " + stand);
		}
		if (tick == 640) {
			List<ItemEntity> near = server.overworld().getEntitiesOfClass(ItemEntity.class, raft.getBoundingBox().inflate(6.0));
			long cooked = near.stream().filter(d -> d.getItem().is(Items.COOKED_COD)).count();
			boolean empty = level(CAMPFIRE) instanceof CampfireBlockEntity campfire && campfire.getItems().get(0).isEmpty();
			check(cooked == 1 && empty, "campfire cooked the cod and dropped it at the raft: " + cooked + " cooked cod near, slot empty=" + empty);
		}
		if (tick == 30) {

			checkWorldLight(server.overworld(), "sailing");
		}
		if (tick == 320) {
			check(raft.raftLevel().setBlock(TORCH, Blocks.TORCH.defaultBlockState(), 3), "torch placed on the deck");
		}
		if (tick == 325) {
			checkWorldLight(server.overworld(), "moored, with a deck torch");
		}
		if (tick == 80) {
			double moved = raft.position().distanceTo(startPos);
			double speed = raft.getDeltaMovement().horizontalDistance() * 20;
			check(moved > 10, String.format("forward: moved %.1f blocks in 60 ticks, speed %.2f b/s, pos %s", moved, speed, raft.position()));
			check(Math.abs(raft.getYRot()) < 1, "yaw unchanged while going straight: " + raft.getYRot());
			BlockState furnace = raft.raftLevel().getBlockState(FURNACE);
			check(furnace.hasProperty(AbstractFurnaceBlock.LIT) && furnace.getValue(AbstractFurnaceBlock.LIT), "furnace lit while sailing: " + furnace);
		}
		if (tick == 120) {
			check(Math.abs(raft.getYRot()) > 30, String.format("turn: yaw %.1f after 40 ticks of strafe", raft.getYRot()));
			Vec3 local = raft.toLocal(item.position());
			boolean aboard = local.x >= 0 && local.x <= 3 && local.z >= 0 && local.z <= 3 && Math.abs(local.y - 1.0) < 0.3;
			check(aboard && item.onGround(), String.format("item aboard: local %s onGround=%s", local, item.onGround()));
			check(!Double.isNaN(waterSurface(server.overworld(), BlockPos.containing(raft.position()))), "raft still over water");
		}
		if (tick == 30) {

			net.minecraft.world.phys.AABB probe = raft.getBoundingBox().deflate(0.1);
			StringBuilder shapes = new StringBuilder();
			int count = 0;
			for (net.minecraft.world.phys.shapes.VoxelShape shape : server.overworld().getCollisions(item, probe)) {
				count++;
				shapes.append(' ').append(shape.isEmpty() ? "empty" : shape.bounds());
			}
			int entityShapes = server.overworld().getEntityCollisions(item, probe).size();
			Oceania.LOGGER.info("[smoke] collision probe: raft canBeCollidedWith={} item.canCollideWith(raft)={} getEntityCollisions={} getCollisions={}:{}",
				raft.canBeCollidedWith(item), item.canCollideWith(raft), entityShapes, count, shapes);
			check(entityShapes == 0 && count == 0, "the raft entity is not a collision shape for the server move check: " + count + " shape(s)" + shapes);
		}
		if (tick == 150) {

			check(placeBed(), "bed placed on the deck (foot and head)");
		}
		if (tick == 160) {
			raft.raftLevel().removeBlock(BED_FOOT, false);
		}
		if (tick == 170) {
			check(raft.raftLevel().getBlockState(BED_HEAD).isAir(), "breaking the foot took the head with it");
			check(bedDrops() == 1, "breaking the foot dropped one bed: " + bedDrops());
			clearBedDrops();
			check(placeBed(), "bed placed again");
		}
		if (tick == 180) {
			raft.raftLevel().destroyBlock(BED_HEAD, true, null, 512);
		}
		if (tick == 190) {
			check(raft.raftLevel().getBlockState(BED_FOOT).isAir(), "breaking the head took the foot with it");
			check(bedDrops() == 1, "breaking the head dropped one bed: " + bedDrops());
			clearBedDrops();
		}
		if (tick == 140) {

			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.overworld().registryAccess());
			raft.saveWithoutId(out);
			CompoundTag tag = out.buildResult();
			RaftBlocks reread = RaftBlocks.read(tag.getCompoundOrEmpty("Blocks"), server.overworld().holderLookup(Registries.BLOCK));
			check(reread.size() == raft.blocks().size() && reread.seats().size() == 1, "nbt round trip: " + reread.size() + " blocks, seats " + reread.seats());
			Map<BlockPos, CompoundTag> nbt = reread.takePendingNbt();
			CompoundTag chest = nbt.get(CHEST);
			int items = chest != null ? chest.getList("Items").map(ListTag::size).orElse(-1) : -1;
			check(items == 1, "chest contents saved with the raft: " + items + " stacks, tags for " + nbt.keySet());

			int[] lights = tag.getIntArray("WorldLights").orElse(new int[0]);
			check(lights.length > 0 && lights.length == raft.worldLights().size() * 4,
				"world light positions saved with the raft for crash cleanup: " + lights.length / 4 + " saved, " + raft.worldLights().size() + " owned");
		}
		if (tick == 200) {

			RaftLevel level = raft.raftLevel();
			check(level.setBlock(SPARE, com.oceania.raft.RaftHelmBlock.withWood(Blocks.JUNGLE_PLANKS), 3), "spare helm placed");
			check(raft.blocks().seats().size() == 2 && raft.blocks().core().equals(CORE), "two seats, core unchanged: " + raft.blocks().seats() + " core " + raft.blocks().core());
			sitter = EntityTypes.PIG.create(raft.level(), EntitySpawnReason.COMMAND);
			Vec3 at = raft.toWorld(new Vec3(0.5, 1.0, 2.5));
			sitter.setPos(at.x, at.y, at.z);
			raft.level().addFreshEntity(sitter);
			check(raft.sit(sitter, SPARE), "pig seated at the spare helm");
			check(SPARE.equals(raft.seatOf(sitter)) && raft.isSeatOccupied(SPARE) && !raft.isSeatOccupied(CORE), "the seat is the spare helm: " + raft.seatOf(sitter));
			check(raft.getControllingPassenger() == sitter, "the first to sit drives, whichever helm");
			check(!raft.hasExactlyOnePlayerPassenger(), "the raft is never a one-player mount");
			check(RaftInteraction.problem(raft, SPARE) != null && RaftInteraction.problem(raft, CORE) == null, "an occupied helm may not be mined, the empty one may");
		}
		if (tick == 205) {
			Vec3 local = raft.toLocal(sitter.position());
			Vec3 want = new Vec3(SPARE.getX() + 0.5, SPARE.getY() + 0.5, SPARE.getZ() + 0.5);
			check(local.distanceTo(want) < 0.2, "the pig rides on top of the spare helm: local " + local);
			sitter.stopRiding();
		}
		if (tick == 206) {
			Vec3 local = raft.toLocal(sitter.position());
			check(Math.abs(local.x - (SPARE.getX() + 0.5)) < 1.0 && Math.abs(local.z - (SPARE.getZ() + 0.5)) < 1.0 && local.y > SPARE.getY() + 0.4,
				"got up from the spare helm, not the first: local " + local);
			check(raft.getPassengers().isEmpty() && raft.seatOf(sitter) == null, "seat released");
			sitter.discard();
		}
		if (tick == 207) {

			rider = new Rider(server.overworld());
			Vec3 at = raft.toWorld(new Vec3(CORE.getX() + 0.5, CORE.getY() + 1.0, CORE.getZ() + 0.5));
			rider.setPos(at.x, at.y, at.z);
			check(raft.sit(rider, CORE), "rider seated at the core helm");
			check(raft.getControllingPassenger() == rider, "the rider drives");
			rider.setLastClientInput(new Input(true, false, false, false, false, false, false));
			riderStart = raft.position();
			riderYaw = raft.getYRot();
		}
		if (tick == 237) {
			double moved = raft.position().distanceTo(riderStart);
			check(moved > 2, String.format("rider input: forward held 30 ticks moved the raft %.1f blocks (speed %.2f b/s)", moved,
				raft.getDeltaMovement().horizontalDistance() * 20));
			check(Math.abs(Mth.wrapDegrees(raft.getYRot() - riderYaw)) < 1,
				String.format("rider input: no strafe, no turn: yaw %.1f, was %.1f", raft.getYRot(), riderYaw));
			rider.setLastClientInput(Input.EMPTY);
			rider.stopRiding();
			check(raft.getPassengers().isEmpty() && raft.getControllingPassenger() == null, "rider up, helm free");
		}
		if (tick == 210) {

			check(RaftInteraction.removeHelm(raft, CORE, null), "removing the core helm with a spare keeps the raft");
			check(!raft.isRemoved() && raft.blocks().get(CORE) == null, "raft alive, old core helm gone");
			check(SPARE.equals(raft.blocks().core()), "the spare helm is the core now: " + raft.blocks().core());
			check(RaftInteraction.problem(raft, new BlockPos(0, 0, 2)) == null, "deck once under the old core may be mined");
			check(RaftInteraction.problem(raft, new BlockPos(0, 0, 0)) != null, "deck under the new core may not");
			check(houseboatDrops() == 1, "the mined helm came up as a houseboat item: " + houseboatDrops());
		}
		if (tick == 215) {

			check(raft.raftLevel().setBlock(CORE, com.oceania.raft.RaftHelmBlock.withWood(Blocks.JUNGLE_PLANKS), 3), "helm placed again at the old core");
			check(RaftInteraction.removeHelm(raft, SPARE, null), "removing the spare (now core) keeps the raft");
			check(CORE.equals(raft.blocks().core()) && raft.blocks().seats().size() == 1, "core back where it started: " + raft.blocks().core());
			check(houseboatDrops() == 2, "second helm came up as an item: " + houseboatDrops());
			for (ItemEntity drop : server.overworld().getEntitiesOfClass(ItemEntity.class, raft.getBoundingBox().inflate(6.0), d -> d.getItem().is(RaftItems.RAFT))) drop.discard();
		}
		if (tick == 240) {
			AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) raft.raftLevel().getBlockEntity(FURNACE);
			ItemStack output = furnace != null ? furnace.getItem(2) : ItemStack.EMPTY;
			check(output.is(Items.IRON_INGOT), "furnace smelted while sailing: output " + output);
			buildWall(server.overworld());
		}
		if (tick > 240 && tick <= 300 && tick % 20 == 0) {
			Oceania.LOGGER.info("[smoke] t={} raft={} yaw={} v={}", tick, raft.position(), raft.getYRot(), raft.getDeltaMovement());
		}
		if (tick == 300) {
			double speed = raft.getDeltaMovement().horizontalDistance() * 20;

			double wallLocalZ = raft.toLocal(wallCentre).z;
			double edge = raft.blocks().localBounds().maxZ;
			check(speed < 0.5 && wallLocalZ > edge - 0.1 && wallLocalZ < edge + 1.5,
				String.format("grounded on the wall: speed %.2f b/s, wall centre %.2f blocks past the deck edge", speed, wallLocalZ - edge));
		}
		if (tick == 660) {
			BlockPos helm = raft.blocks().seats().get(0);
			check(RaftInteraction.problem(raft, helm) == null, "the core helm may come up");
			check(RaftInteraction.problem(raft, new BlockPos(0, 0, 0)) != null, "a core deck block may not be mined");
			check(!raft.raftLevel().setBlock(new BlockPos(0, 0, 0), Blocks.AIR.defaultBlockState(), 3), "a core deck block cannot be removed");
			check(RaftInteraction.problem(raft, EXTRA) == null, "a block beside the core may be mined");
			check(raft.raftLevel().isUnobstructed(Blocks.CHEST.defaultBlockState(), new BlockPos(1, 1, 0), net.minecraft.world.phys.shapes.CollisionContext.empty()), "a free deck cell is clear to place on");
			lastRaftPos = raft.position();

			preDrops = server.overworld().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(lastRaftPos, lastRaftPos).inflate(8.0))
				.stream().map(ItemEntity::getId).collect(java.util.stream.Collectors.toSet());
			RaftInteraction.dismantle(raft, helm, null);
		}
		if (tick == 666) {

			List<BlockPos> left = new ArrayList<>();
			int chunks = RaftLightCommand.scan(server.overworld(), SectionPos.blockToSectionCoord(lastRaftPos.x),
				SectionPos.blockToSectionCoord(lastRaftPos.z), 4, left);
			Oceania.LOGGER.info("[smoke] world light after dismantle: {} light block(s) in {} loaded chunk(s){}",
				left.size(), chunks, left.isEmpty() ? "" : " at " + left);
			Oceania.LOGGER.info("[smoke] world light cost: {}", RaftWorldLight.costReport());
			check(left.isEmpty(), "the dismantled raft took its world light blocks with it: " + left.size() + " left behind");
		}
		if (tick == 665) {
			check(raft.isRemoved(), "raft entity gone after dismantling");
			List<ItemEntity> drops = server.overworld().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(lastRaftPos, lastRaftPos).inflate(8.0));
			long houseboats = drops.stream().filter(d -> d.getItem().is(RaftItems.RAFT)).count();
			long planks = drops.stream().filter(d -> d.getItem().is(Items.JUNGLE_PLANKS)).count();
			long diamonds = drops.stream().filter(d -> d.getItem().is(Items.DIAMOND)).count();
			check(houseboats == 1, "one houseboat item dropped: " + houseboats);
			check(planks == 0, "the nine deck planks were absorbed, none dropped: " + planks);
			check(diamonds == 1, "the chest spilled its diamonds: " + diamonds);
			check(drops.size() >= 7, "extra blocks dropped: " + drops.size() + " item entities");
			trackedDrops = drops.stream().filter(d -> d != item && !preDrops.contains(d.getId())).toList();

			control = new ItemEntity(server.overworld(), lastRaftPos.x + 12.0, surfaceY + 1.0, lastRaftPos.z, new ItemStack(Items.OAK_PLANKS));
			server.overworld().addFreshEntity(control);
		}
		if (tick > 665 && tick <= 865) {
			for (ItemEntity drop : trackedDrops) {
				dropMinY = Math.min(dropMinY, drop.getY());
				dropMin.merge(drop.getId(), drop.getY(), Math::min);
			}
			controlMinY = Math.min(controlMinY, control.getY());
		}
		if (tick == 865) {
			double lowestNow = trackedDrops.stream().mapToDouble(ItemEntity::getY).min().orElse(Double.NaN);
			Oceania.LOGGER.info("[smoke] buoyancy: surface {}; raft drops lowest {} ({} under), now {}; vanilla control lowest {} ({} under), now {}",
				f(surfaceY), f(dropMinY), f(surfaceY - dropMinY), f(lowestNow), f(controlMinY), f(surfaceY - controlMinY), f(control.getY()));
			for (ItemEntity drop : trackedDrops) {
				Oceania.LOGGER.info("[smoke]   drop {} x{}: lowest {} under, now {} under, inWater={} vel={}", drop.getItem().getItem(), drop.getItem().getCount(),
					f(surfaceY - dropMin.getOrDefault(drop.getId(), drop.getY())), f(surfaceY - drop.getY()), drop.isInWater(), drop.getDeltaMovement());
			}
			check(surfaceY - dropMinY <= 1.0, "raft drops never went more than a block under: " + f(surfaceY - dropMinY));
			check(surfaceY - lowestNow <= 0.6, "raft drops ride at the surface ten seconds on: " + f(surfaceY - lowestNow) + " under");
		}
		if (tick == 870) {
			Oceania.LOGGER.info("[smoke] DONE failures={}", failures);
			server.halt(false);
		}
	}

	private static final BlockPos BED_FOOT = new BlockPos(0, 1, 0);
	private static final BlockPos BED_HEAD = new BlockPos(1, 1, 0);

	private static void checkWorldLight(ServerLevel level, String when) {
		java.util.Set<BlockPos> owned = raft.worldLights();
		List<RaftBlocks.Emitter> emitters = raft.blocks().emitters();
		int lit = 0, blocked = 0, missing = 0;
		StringBuilder detail = new StringBuilder();
		for (RaftBlocks.Emitter e : emitters) {
			BlockPos cell = e.pos();
			BlockPos at = BlockPos.containing(raft.toWorld(new Vec3(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5)));
			BlockState world = level.getBlockState(at);
			if (world.is(Blocks.LIGHT) && owned.contains(at)) {
				lit++;
				detail.append(' ').append(cell.toShortString()).append(" emits ").append(e.level())
					.append(" -> ").append(at.toShortString()).append(" light ")
					.append(world.getValue(net.minecraft.world.level.block.LightBlock.LEVEL));
			} else if (!world.isAir() && !world.is(Blocks.WATER)) {
				blocked++;
				detail.append(' ').append(cell.toShortString()).append(" refused, ").append(at.toShortString())
					.append(" holds ").append(world.getBlock().getName().getString());
			} else {
				missing++;
				detail.append(" MISSING ").append(cell.toShortString()).append(" -> ").append(at.toShortString())
					.append(" holds ").append(world.getBlock().getName().getString());
			}
		}
		Oceania.LOGGER.info("[smoke] world light {}: {} emitter(s) on the deck, {} lit in the world, {} refused, {} missing;{}",
			when, emitters.size(), lit, blocked, missing, detail);
		check(!emitters.isEmpty() && lit > 0 && missing == 0, String.format(
			"world light %s: %d of %d deck emitter(s) lit the real world, %d missing", when, lit, emitters.size(), missing));
	}

	private static boolean placeBed() {
		RaftLevel level = raft.raftLevel();
		BlockState foot = Blocks.BED.red().defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST)
			.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT);
		BlockState head = foot.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD);
		return level.setBlock(BED_FOOT, foot, 3) && level.setBlock(BED_HEAD, head, 3);
	}

	private static int houseboatDrops() {
		return raft.level().getEntitiesOfClass(ItemEntity.class, raft.getBoundingBox().inflate(6.0), d -> d.getItem().is(RaftItems.RAFT)).size();
	}

	private static List<ItemEntity> bedDropList() {
		return raft.level().getEntitiesOfClass(ItemEntity.class, raft.getBoundingBox().inflate(6.0), d -> d.getItem().is(Items.BED.red()));
	}

	private static net.minecraft.world.level.block.entity.BlockEntity level(BlockPos cell) {
		return raft.raftLevel().getBlockEntity(cell);
	}

	private static int bedDrops() {
		return bedDropList().stream().mapToInt(d -> d.getItem().getCount()).sum();
	}

	private static void clearBedDrops() {
		bedDropList().forEach(ItemEntity::discard);
	}

	private static String f(double v) {
		return String.format("%.2f", v);
	}

	private static Vec3 lastRaftPos;
	private static List<ItemEntity> trackedDrops = List.of();
	private static java.util.Set<Integer> preDrops = java.util.Set.of();
	private static ItemEntity control;
	private static double surfaceY;
	private static double dropMinY = Double.MAX_VALUE;
	private static double controlMinY = Double.MAX_VALUE;
	private static final java.util.Map<Integer, Double> dropMin = new java.util.HashMap<>();

	private static void build() {
		RaftLevel level = raft.raftLevel();

		Mob pig = EntityTypes.PIG.create(raft.level(), EntitySpawnReason.COMMAND);
		if (pig != null) {
			Vec3 at = raft.toWorld(new Vec3(0.5, 1.0, 0.5));
			pig.setPos(at.x, at.y, at.z);
			raft.level().addFreshEntity(pig);
			check(!level.isUnobstructed(Blocks.CHEST.defaultBlockState(), new BlockPos(0, 1, 0), net.minecraft.world.phys.shapes.CollisionContext.empty()), "block refused where a pig stands");
			pig.discard();
		}
		check(level.setBlock(CHEST, Blocks.CHEST.defaultBlockState(), 3), "placed a chest");
		if (level.getBlockEntity(CHEST) instanceof ChestBlockEntity chest) {
			chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
			check(true, "chest has a block entity");
		} else {
			check(false, "chest has a block entity: " + level.getBlockEntity(CHEST));
		}
		check(level.setBlock(FURNACE, Blocks.FURNACE.defaultBlockState(), 3), "placed a furnace");
		if (level.getBlockEntity(FURNACE) instanceof AbstractFurnaceBlockEntity furnace) {
			furnace.setItem(0, new ItemStack(Items.RAW_IRON));
			furnace.setItem(1, new ItemStack(Items.COAL));
			check(true, "furnace has a block entity");
		} else {
			check(false, "furnace has a block entity: " + level.getBlockEntity(FURNACE));
		}
		level.setBlock(FENCE_A, Blocks.OAK_FENCE.defaultBlockState(), 3);
		level.setBlock(FENCE_B, Blocks.OAK_FENCE.defaultBlockState(), 3);
		BlockState fence = level.getBlockState(FENCE_A);
		check(fence.hasProperty(CrossCollisionBlock.EAST) && fence.getValue(CrossCollisionBlock.EAST), "fences connected through neighbour updates: " + fence);
		check(!level.setBlock(TOO_FAR, Blocks.STONE.defaultBlockState(), 3), "block past the 32-block footprint refused");
		check(level.setBlock(EXTRA, Blocks.STONE.defaultBlockState(), 3), "block beside the deck accepted");

		check(level.setBlock(CAMPFIRE, Blocks.CAMPFIRE.defaultBlockState(), 3), "placed a lit campfire");
		if (level.getBlockEntity(CAMPFIRE) instanceof CampfireBlockEntity campfire) {
			check(campfire.placeFood((ServerLevel) raft.level(), null, new ItemStack(Items.COD)), "a cod went on the campfire");
		} else {
			check(false, "campfire has a block entity: " + level.getBlockEntity(CAMPFIRE));
		}
		check(level.setBlock(BREWING, Blocks.BREWING_STAND.defaultBlockState(), 3), "placed a brewing stand");
		if (level.getBlockEntity(BREWING) instanceof BrewingStandBlockEntity stand) {
			stand.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.WATER));
			stand.setItem(3, new ItemStack(Items.NETHER_WART));
			stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
			check(true, "brewing stand loaded: water bottle, nether wart, blaze powder");
		} else {
			check(false, "brewing stand has a block entity: " + level.getBlockEntity(BREWING));
		}
		check(level.setBlock(BOTTOM_CHEST, Blocks.CHEST.defaultBlockState(), 3)
			&& level.setBlock(CHEST_HOPPER, Blocks.HOPPER.defaultBlockState(), 3)
			&& level.setBlock(TOP_CHEST, Blocks.CHEST.defaultBlockState(), 3), "placed chest, hopper, chest");
		if (level.getBlockEntity(TOP_CHEST) instanceof ChestBlockEntity top) top.setItem(4, new ItemStack(Items.COBBLESTONE));
		check(level.setBlock(DECK_HOPPER, Blocks.HOPPER.defaultBlockState(), 3), "placed a hopper over the campfire");
		Vec3 above = raft.toWorld(new Vec3(DECK_HOPPER.getX() + 0.5, DECK_HOPPER.getY() + 1.1, DECK_HOPPER.getZ() + 0.5));
		deckItem = new ItemEntity(raft.level(), above.x, above.y, above.z, new ItemStack(Items.FLINT));
		deckItem.setDeltaMovement(Vec3.ZERO);
		raft.level().addFreshEntity(deckItem);
		check(raft.blocks().size() == 21, "block count after building: " + raft.blocks().size());
		check(!raft.getBoundingBox().equals(startBox()), "bounding box grew with the deck: " + raft.getBoundingBox());
	}

	private static net.minecraft.world.phys.AABB startBox() {
		return new net.minecraft.world.phys.AABB(startPos.x - 1.5, startPos.y, startPos.z - 1.5, startPos.x + 1.5, startPos.y + 2, startPos.z + 1.5);
	}

	private static void buildWall(ServerLevel level) {
		Vec3 bow = raft.toWorld(new Vec3(1.5, 0, 3.5));
		Vec3 forward = raft.rotateToWorld(new Vec3(0, 0, 1));
		Vec3 across = raft.rotateToWorld(new Vec3(1, 0, 0));
		wallCentre = bow.add(forward.scale(6));
		int y = Mth.floor(raft.getY() + RaftEntity.DRAFT);
		for (int i = -3; i <= 3; i++) {
			Vec3 p = wallCentre.add(across.scale(i));
			level.setBlock(new BlockPos(Mth.floor(p.x), y, Mth.floor(p.z)), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(new BlockPos(Mth.floor(p.x), y - 1, Mth.floor(p.z)), Blocks.STONE.defaultBlockState(), 3);
		}
		Oceania.LOGGER.info("[smoke] wall at {} across heading {}", wallCentre, forward);
	}

	private static void check(boolean ok, String what) {
		if (ok) {
			Oceania.LOGGER.info("[smoke] PASS {}", what);
		} else {
			failures++;
			Oceania.LOGGER.error("[smoke] FAIL {}", what);
		}
	}

	static BlockPos findOpenWater(ServerLevel level, BlockPos around) {
		for (int r = 0; r <= 32; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					int x = around.getX() + dx * 8;
					int z = around.getZ() + dz * 8;
					boolean clear = true;
					for (int ox = -2; ox <= 2 && clear; ox++) {
						for (int oz = -2; oz <= 2 && clear; oz++) {
							if (!level.getFluidState(new BlockPos(x + ox, 62, z + oz)).is(FluidTags.WATER)) clear = false;
							for (int y = 63; y <= 70 && clear; y++) {
								if (!level.getBlockState(new BlockPos(x + ox, y, z + oz)).isAir()) clear = false;
							}
						}
					}
					if (clear) return new BlockPos(x, 62, z);
				}
			}
		}
		return null;
	}

	static double waterSurface(ServerLevel level, BlockPos around) {
		for (int y = 70; y >= 40; y--) {
			BlockPos pos = new BlockPos(around.getX(), y, around.getZ());
			FluidState fluid = level.getFluidState(pos);
			if (fluid.is(FluidTags.WATER)) return y + fluid.getHeight(level, pos);
		}
		return Double.NaN;
	}
}
