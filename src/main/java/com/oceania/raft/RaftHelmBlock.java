package com.oceania.raft;

import com.oceania.registry.OceaniaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RaftHelmBlock extends Block {

	public static final double HEIGHT = 0.5;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16 * HEIGHT, 16);

	public static final EnumProperty<Wood> WOOD = EnumProperty.create("wood", Wood.class);

	public enum Wood implements StringRepresentable {
		OAK("oak", Blocks.OAK_PLANKS),
		SPRUCE("spruce", Blocks.SPRUCE_PLANKS),
		BIRCH("birch", Blocks.BIRCH_PLANKS),
		JUNGLE("jungle", Blocks.JUNGLE_PLANKS),
		ACACIA("acacia", Blocks.ACACIA_PLANKS),
		DARK_OAK("dark_oak", Blocks.DARK_OAK_PLANKS),
		MANGROVE("mangrove", Blocks.MANGROVE_PLANKS),
		CHERRY("cherry", Blocks.CHERRY_PLANKS),
		PALE_OAK("pale_oak", Blocks.PALE_OAK_PLANKS),
		POPLAR("poplar", Blocks.POPLAR_PLANKS),
		BAMBOO("bamboo", Blocks.BAMBOO_PLANKS);

		private final String name;
		private final Block planks;

		Wood(String name, Block planks) {
			this.name = name;
			this.planks = planks;
		}

		public Block planks() {
			return planks;
		}

		public static Wood of(Block planks) {
			for (Wood wood : values()) {
				if (wood.planks == planks) return wood;
			}
			return JUNGLE;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public RaftHelmBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(WOOD, Wood.JUNGLE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(WOOD);
	}

	public static BlockState withWood(Block planks) {
		return OceaniaBlocks.RAFT_HELM.defaultBlockState().setValue(WOOD, Wood.of(planks));
	}

	public static Item houseboat(BlockState state) {
		return RaftItems.houseboat(state.getValue(WOOD).planks());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof RaftLevel raftLevel)) return InteractionResult.PASS;
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		return raftLevel.raft().sit(player, pos) ? InteractionResult.CONSUME : InteractionResult.PASS;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(houseboat(state));
	}
}
