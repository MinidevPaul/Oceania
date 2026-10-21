package com.oceania.raft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.WeightedList;
import java.util.Collection;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.TickRateManager;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class RaftLevel extends Level {

	private static final int MAX_CHAINED_NEIGHBOR_UPDATES = 1_000_000;

	private final RaftEntity raft;
	private final Level real;
	private final Map<BlockPos, BlockEntity> blockEntities = new LinkedHashMap<>();
	private final Map<BlockPos, BlockEntityTicker<BlockEntity>> tickers = new HashMap<>();
	private final RaftTicks ticks = new RaftTicks();
	private @Nullable Lighting lighting;

	public interface Lighting {
		int get(LightLayer layer, BlockPos pos);

		void markDirty();
	}

	public RaftLevel(RaftEntity raft, Level real) {
		super(
			(WritableLevelData) real.getLevelData(),
			real.dimension(),
			real.registryAccess(),
			real.dimensionTypeRegistration(),
			real.isClientSide(),
			false,
			0L,
			MAX_CHAINED_NEIGHBOR_UPDATES
		);
		this.raft = raft;
		this.real = real;
	}

	public RaftEntity raft() {
		return raft;
	}

	public Level real() {
		return real;
	}

	public void setLighting(@Nullable Lighting lighting) {
		this.lighting = lighting;
	}

	public boolean hasLighting() {
		return lighting != null;
	}

	public void markLightDirty() {
		if (lighting != null) lighting.markDirty();
	}

	public BlockPos worldBlockPos(BlockPos local) {
		return BlockPos.containing(raft.toWorld(Vec3.atCenterOf(local)));
	}

	public AABB worldBounds(AABB local) {
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			Vec3 c = raft.toWorld(new Vec3((i & 1) == 0 ? local.minX : local.maxX, (i & 2) == 0 ? local.minY : local.maxY, (i & 4) == 0 ? local.minZ : local.maxZ));
			minX = Math.min(minX, c.x); maxX = Math.max(maxX, c.x);
			minY = Math.min(minY, c.y); maxY = Math.max(maxY, c.y);
			minZ = Math.min(minZ, c.z); maxZ = Math.max(maxZ, c.z);
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public boolean hasBlock(BlockPos pos) {
		return raft.blocks().has(pos);
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		BlockState state = raft.blocks().get(pos);
		return state != null ? state : Blocks.AIR.defaultBlockState();
	}

	@Override
	public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Override
	public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
		BlockState old = getBlockState(pos);
		if (old == state) return false;
		if (old.isAir() && !state.isAir() && !raft.blocks().fits(pos)) return false;

		if (raft.blocks().isCore(pos) && (state.isAir() || !state.is(old.getBlock()))) return false;
		boolean moving = (flags & 64) != 0;
		if (state.isAir()) {
			raft.blocks().remove(pos);
		} else {
			raft.blocks().put(pos, state);
		}
		boolean blockChanged = !old.is(state.getBlock());
		if (blockChanged && old.hasBlockEntity() && !state.shouldChangedStateKeepBlockEntity(old)) {
			if (!isClientSide()) {
				BlockEntity leaving = getBlockEntity(pos);
				if (real instanceof ServerLevel server && leaving instanceof AbstractFurnaceBlockEntity furnace) {

					Vec3 centre = raft.toWorld(Vec3.atCenterOf(pos));
					for (int i = 0; i < furnace.getContainerSize(); i++) {
						ItemStack held = furnace.getItem(i);
						if (!held.isEmpty()) RaftInteraction.spawnItem(server, raft, centre, held.copy());
					}
					furnace.clearContent();
					furnace.getRecipesToAwardAndPopExperience(server, centre);
				}

				if (leaving != null) leaving.preRemoveSideEffects(pos, old);
			}
			removeBlockEntity(pos);
		}

		if (!getBlockState(pos).is(state.getBlock())) {
			return false;
		}
		if (!isClientSide()) {
			state.onPlace(this, pos, old, moving);
		}
		if (state.hasBlockEntity()) {
			BlockEntity be = blockEntities.get(pos);
			if (be != null && !be.isValidBlockState(state)) {
				removeBlockEntity(pos);
				be = null;
			}
			if (be == null) {
				be = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
				if (be != null) addBlockEntity(be);
			} else {
				be.setBlockState(state);
				refreshTicker(be);
			}
		} else if (old.hasBlockEntity()) {
			removeBlockEntity(pos);
		}
		raft.onBlockChanged(pos, old, state);
		if ((flags & 2) != 0 && (!isClientSide() || (flags & 4) == 0)) {
			sendBlockUpdated(pos, old, state, flags);
		}
		if ((flags & 1) != 0) {
			updateNeighborsAt(pos, old.getBlock());
			if (!isClientSide() && state.hasAnalogOutputSignal()) {
				updateNeighbourForOutputSignal(pos, state.getBlock());
			}
		}
		if ((flags & 16) == 0 && recursionLeft > 0) {
			int k = flags & -34;
			old.updateIndirectNeighbourShapes(this, pos, k, recursionLeft - 1);
			state.updateNeighbourShapes(this, pos, k, recursionLeft - 1);
			state.updateIndirectNeighbourShapes(this, pos, k, recursionLeft - 1);
		}
		return true;
	}

	@Override
	public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
		if (!isClientSide()) raft.syncBlock(pos);
	}

	@Override
	public void setBlocksDirty(BlockPos pos, BlockState oldState, BlockState newState) {
		if (lighting != null) lighting.markDirty();
	}

	@Override
	public void blockEntityChanged(BlockPos pos) {

	}

	@Override
	public void blockEvent(BlockPos pos, Block block, int type, int data) {
		boolean handled = getBlockState(pos).triggerEvent(this, pos, type, data);
		if (!isClientSide() && handled) raft.syncBlockEvent(pos, type, data);
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return blockEntities.get(pos);
	}

	public Map<BlockPos, BlockEntity> blockEntities() {
		return blockEntities;
	}

	@Override
	public void setBlockEntity(BlockEntity be) {
		addBlockEntity(be);
	}

	@Override
	public void removeBlockEntity(BlockPos pos) {
		BlockEntity be = blockEntities.remove(pos);
		if (be != null) {
			be.setRemoved();
			tickers.remove(pos);
		}
	}

	private void addBlockEntity(BlockEntity be) {
		BlockPos pos = be.getBlockPos();
		BlockEntity old = blockEntities.put(pos, be);
		if (old != null && old != be) old.setRemoved();
		be.setLevel(this);
		be.clearRemoved();
		refreshTicker(be);
	}

	@SuppressWarnings("unchecked")
	private void refreshTicker(BlockEntity be) {
		BlockPos pos = be.getBlockPos();
		BlockState state = getBlockState(pos);

		BlockEntityTicker<BlockEntity> ticker = null;
		if (!isClientSide() && real instanceof ServerLevel server) ticker = RaftTickers.serverOnly(this, server, be);
		if (ticker == null) ticker = state.getTicker(this, (BlockEntityType<BlockEntity>) be.getType());
		if (ticker != null) {
			tickers.put(pos, ticker);
		} else {
			tickers.remove(pos);
		}
	}

	public void loadBlockEntities(Map<BlockPos, CompoundTag> nbt) {
		for (BlockEntity be : blockEntities.values()) be.setRemoved();
		blockEntities.clear();
		tickers.clear();
		for (Map.Entry<BlockPos, BlockState> e : raft.blocks().all().entrySet()) {
			BlockState state = e.getValue();
			if (!state.hasBlockEntity()) continue;
			BlockEntity be = ((EntityBlock) state.getBlock()).newBlockEntity(e.getKey(), state);
			if (be == null) continue;
			CompoundTag tag = nbt.get(e.getKey());
			if (tag != null) be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), tag));
			addBlockEntity(be);
		}
	}

	public void applyBlockEntityNbt(BlockPos pos, CompoundTag tag) {
		BlockEntity be = blockEntities.get(pos);
		if (be != null) be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), tag));
	}

	public @Nullable CompoundTag saveNbt(BlockPos pos) {
		BlockEntity be = blockEntities.get(pos);
		return be != null ? be.saveWithoutMetadata(registryAccess()) : null;
	}

	public @Nullable CompoundTag syncNbt(BlockPos pos) {
		BlockEntity be = blockEntities.get(pos);
		return be != null ? be.getUpdateTag(registryAccess()) : null;
	}

	public void tickBlocks() {
		if (!tickers.isEmpty()) {
			for (Map.Entry<BlockPos, BlockEntityTicker<BlockEntity>> e : new ArrayList<>(tickers.entrySet())) {
				BlockEntity be = blockEntities.get(e.getKey());
				if (be == null || be.isRemoved()) continue;
				e.getValue().tick(this, e.getKey(), getBlockState(e.getKey()), be);
			}
		}
		if (!isClientSide()) {
			for (ScheduledTick<Block> t : ticks.due(getGameTime())) {
				runScheduledTick(t);
			}
		}
	}

	private void runScheduledTick(ScheduledTick<Block> tick) {
		BlockPos pos = tick.pos();
		BlockState state = getBlockState(pos);
		if (!state.is(tick.type())) return;
		BlockEntity be = getBlockEntity(pos);
		if (be instanceof ChestBlockEntity chest) {
			chest.recheckOpen();
		} else if (be instanceof BarrelBlockEntity barrel) {
			barrel.recheckOpen();
		} else if (state.getBlock() instanceof ButtonBlock && state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED)) {
			setBlock(pos, state.setValue(BlockStateProperties.POWERED, false), 3);
		} else if (state.getBlock() instanceof BaseFireBlock) {
			removeBlock(pos, false);
		}
	}

	@Override
	public void playSeededSound(@Nullable Entity player, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {
		Vec3 w = raft.toWorld(new Vec3(x, y, z));

		real.playSeededSound(player, w.x, w.y, w.z, sound, source, volume, pitch, seed);
	}

	@Override
	public void playSeededSound(@Nullable Entity player, Entity entity, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {
		real.playSeededSound(player, entity, sound, source, volume, pitch, seed);
	}

	@Override
	public void levelEvent(@Nullable Entity player, int type, BlockPos pos, int data) {

		real.levelEvent(null, type, worldBlockPos(pos), data);
	}

	@Override
	public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
		real.gameEvent(event, raft.toWorld(pos), context);
	}

	@Override
	public void addParticle(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
		Vec3 w = raft.toWorld(new Vec3(x, y, z));
		Vec3 v = raft.rotateToWorld(new Vec3(vx, vy, vz));
		real.addParticle(particle, w.x, w.y, w.z, v.x, v.y, v.z);
	}

	@Override
	public void addParticle(ParticleOptions particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, double vx, double vy, double vz) {
		Vec3 w = raft.toWorld(new Vec3(x, y, z));
		Vec3 v = raft.rotateToWorld(new Vec3(vx, vy, vz));
		real.addParticle(particle, overrideLimiter, alwaysShow, w.x, w.y, w.z, v.x, v.y, v.z);
	}

	@Override
	public boolean destroyBlock(BlockPos pos, boolean drop, @Nullable Entity entity, int recursionLeft) {
		BlockState state = getBlockState(pos);
		if (state.isAir()) return false;
		if (!(state.getBlock() instanceof BaseFireBlock)) levelEvent(2001, pos, Block.getId(state));
		if (drop && real instanceof ServerLevel server) {
			BlockEntity blockEntity = state.hasBlockEntity() ? getBlockEntity(pos) : null;
			BlockPos worldPos = worldBlockPos(pos);
			Vec3 centre = raft.toWorld(Vec3.atCenterOf(pos));
			for (ItemStack stack : Block.getDrops(state, server, worldPos, blockEntity, entity, ItemStack.EMPTY)) {
				RaftInteraction.spawnItem(server, raft, centre, stack);
			}
			state.spawnAfterBreak(server, worldPos, ItemStack.EMPTY, true);
		}
		boolean changed = setBlock(pos, Blocks.AIR.defaultBlockState(), 3, recursionLeft);
		if (changed) gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(entity, state));
		return changed;
	}

	@Override
	public boolean addFreshEntity(Entity entity) {
		Vec3 w = raft.toWorld(entity.position());
		Vec3 v = raft.rotateToWorld(entity.getDeltaMovement());
		if (entity instanceof ItemEntity item) {
			if (!(real instanceof ServerLevel server)) return false;
			RaftInteraction.spawnItemAt(server, raft, w, item.getItem().copy(), v);
			return true;
		}
		if (entity instanceof ExperienceOrb orb) {
			return real.addFreshEntity(new ExperienceOrb(real, w.x, w.y, w.z, orb.getValue()));
		}
		return false;
	}

	@Override
	public List<Entity> getEntities(@Nullable Entity except, AABB box, Predicate<? super Entity> filter) {
		return real.getEntities(except, worldBounds(box), filter);
	}

	@Override
	public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> test, AABB box, Predicate<? super T> filter, List<? super T> out, int max) {
		real.getEntities(test, worldBounds(box), filter, out, max);
	}

	@Override
	public @Nullable Entity getEntity(int id) {
		return real.getEntity(id);
	}

	@Override
	public List<? extends Player> players() {
		return real.players();
	}

	@Override
	public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB box) {
		return List.of();
	}

	@Override
	public boolean isUnobstructed(@Nullable Entity except, VoxelShape shape) {
		if (shape.isEmpty()) return true;
		for (Entity e : real.getEntities(except, worldBounds(shape.bounds()), other -> !(other instanceof RaftEntity) && !other.isRemoved() && other.blocksBuilding && (except == null || !other.isPassengerOfSameVehicle(except)))) {
			if (net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(shape, net.minecraft.world.phys.shapes.Shapes.create(localBounds(e.getBoundingBox())), net.minecraft.world.phys.shapes.BooleanOp.AND)) {
				return false;
			}
		}
		return true;
	}

	public AABB localBounds(AABB world) {
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			Vec3 c = raft.toLocal(new Vec3((i & 1) == 0 ? world.minX : world.maxX, (i & 2) == 0 ? world.minY : world.maxY, (i & 4) == 0 ? world.minZ : world.maxZ));
			minX = Math.min(minX, c.x); maxX = Math.max(maxX, c.x);
			minY = Math.min(minY, c.y); maxY = Math.max(maxY, c.y);
			minZ = Math.min(minZ, c.z); maxZ = Math.max(maxZ, c.z);
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static final LevelEntityGetter<Entity> NO_ENTITIES = new LevelEntityGetter<>() {
		@Override
		public @Nullable Entity get(int id) {
			return null;
		}

		@Override
		public @Nullable Entity get(UUID uuid) {
			return null;
		}

		@Override
		public Iterable<Entity> getAll() {
			return List.of();
		}

		@Override
		public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AbortableIterationConsumer<U> consumer) {
		}

		@Override
		public void get(AABB box, Consumer<Entity> consumer) {
		}

		@Override
		public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AABB box, AbortableIterationConsumer<U> consumer) {
		}
	};

	@Override
	protected LevelEntityGetter<Entity> getEntities() {
		return NO_ENTITIES;
	}

	@Override
	public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
		return this;
	}

	@Override
	public boolean hasChunk(int chunkX, int chunkZ) {
		return true;
	}

	@Override
	public boolean isLoaded(BlockPos pos) {
		return true;
	}

	@Override
	public int getHeight(Heightmap.Types type, int x, int z) {
		return (int) raft.blocks().localBounds().maxY;
	}

	@Override
	public int getBrightness(LightLayer layer, BlockPos pos) {
		if (lighting != null) return lighting.get(layer, pos);
		return layer == LightLayer.SKY ? 15 : 0;
	}

	@Override
	public int getRawBrightness(BlockPos pos, int skyDarken) {
		return Math.max(getBrightness(LightLayer.BLOCK, pos), getBrightness(LightLayer.SKY, pos) - skyDarken);
	}

	@Override
	public WorldBorder getWorldBorder() {
		return real.getWorldBorder();
	}

	@Override
	public int getSeaLevel() {
		return real.getSeaLevel();
	}

	@Override
	public int getSkyDarken() {
		return real.getSkyDarken();
	}

	@Override
	public Holder<Biome> getBiome(BlockPos pos) {
		return real.getBiome(worldBlockPos(pos));
	}

	@Override
	public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
		return real.getUncachedNoiseBiome(x, y, z);
	}

	@Override
	public float getRainLevel(float partialTick) {
		return real.getRainLevel(partialTick);
	}

	@Override
	public float getThunderLevel(float partialTick) {
		return real.getThunderLevel(partialTick);
	}

	@Override
	public net.minecraft.world.level.chunk.ChunkSource getChunkSource() {
		return real.getChunkSource();
	}

	@Override
	public LevelTickAccess<Block> getBlockTicks() {
		return ticks;
	}

	@Override
	public LevelTickAccess<Fluid> getFluidTicks() {
		return BlackholeTickAccess.emptyLevelList();
	}

	@Override
	public @Nullable MinecraftServer getServer() {
		return real.getServer();
	}

	@Override
	public String gatherChunkSourceStats() {
		return "raft";
	}

	@Override
	public TickRateManager tickRateManager() {
		return real.tickRateManager();
	}

	@Override
	public @Nullable MapItemSavedData getMapData(MapId id) {
		return real.getMapData(id);
	}

	@Override
	public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
	}

	@Override
	public Scoreboard getScoreboard() {
		return real.getScoreboard();
	}

	@Override
	public RecipeAccess recipeAccess() {
		return real.recipeAccess();
	}

	@Override
	public EnvironmentAttributeSystem environmentAttributes() {
		return real.environmentAttributes();
	}

	@Override
	public ClockManager clockManager() {
		return real.clockManager();
	}

	@Override
	public Collection<EnderDragonPart> dragonParts() {
		return real.dragonParts();
	}

	@Override
	public LevelData.RespawnData getRespawnData() {
		return real.getRespawnData();
	}

	@Override
	public void setRespawnData(LevelData.RespawnData data) {
		real.setRespawnData(data);
	}

	@Override
	public void explode(@Nullable Entity source, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator calculator,
	                    double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction interaction,
	                    ParticleOptions smallParticles, ParticleOptions largeParticles,
	                    WeightedList<ExplosionParticleInfo> blockParticles, Holder<SoundEvent> sound) {
		Vec3 w = raft.toWorld(new Vec3(x, y, z));
		real.explode(source, damageSource, calculator, w.x, w.y, w.z, radius, fire, interaction, smallParticles, largeParticles, blockParticles, sound);
	}

	@Override
	public FeatureFlagSet enabledFeatures() {
		return real.enabledFeatures();
	}

	private static final class RaftTicks implements LevelTickAccess<Block> {
		private final List<ScheduledTick<Block>> list = new ArrayList<>();

		@Override
		public void schedule(ScheduledTick<Block> tick) {
			if (hasScheduledTick(tick.pos(), tick.type())) return;
			list.add(tick);
		}

		@Override
		public boolean hasScheduledTick(BlockPos pos, Block block) {
			for (ScheduledTick<Block> t : list) {
				if (t.type() == block && t.pos().equals(pos)) return true;
			}
			return false;
		}

		@Override
		public boolean willTickThisTick(BlockPos pos, Block block) {
			return false;
		}

		@Override
		public int count() {
			return list.size();
		}

		List<ScheduledTick<Block>> due(long gameTime) {
			if (list.isEmpty()) return List.of();
			List<ScheduledTick<Block>> due = new ArrayList<>();
			for (Iterator<ScheduledTick<Block>> it = list.iterator(); it.hasNext(); ) {
				ScheduledTick<Block> t = it.next();
				if (t.triggerTick() <= gameTime) {
					due.add(t);
					it.remove();
				}
			}
			return due;
		}
	}
}
