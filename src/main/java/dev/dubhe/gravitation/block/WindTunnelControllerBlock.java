package dev.dubhe.gravitation.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker;
import dev.dubhe.gravitation.init.ModBlockEntities;
import dev.dubhe.gravitation.menu.WindTunnelControllerMenu;
import dev.dubhe.gravitation.windtunnel.WindTunnelNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class WindTunnelControllerBlock extends BaseEntityBlock implements EntityBlock {
    public static final MapCodec<WindTunnelControllerBlock> CODEC = simpleCodec(WindTunnelControllerBlock::new);
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");
    private static final VoxelShape SHAPE = Shapes.block();

    public WindTunnelControllerBlock() {
        this(Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3.5F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops());
    }

    public WindTunnelControllerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(
            POWERED,
            Boolean.FALSE
        ).setValue(ENABLED, Boolean.FALSE));
    }

    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(
            POWERED,
            ENABLED
        );
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(POWERED, Boolean.FALSE).setValue(ENABLED, Boolean.FALSE);
    }

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.WIND_TUNNEL_CONTROLLER.create(pos, state);
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> blockEntityType
    ) {
        return blockEntityType != ModBlockEntities.WIND_TUNNEL_CONTROLLER.get() ? null : new SmartBlockEntityTicker<>();
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            } else {
                level.setBlock(pos, state.cycle(ENABLED), 2);
                WindTunnelNetwork.refreshFromController(level, pos);
                return InteractionResult.CONSUME;
            }
        } else if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            MenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, user) -> new WindTunnelControllerMenu(containerId, pos),
                Component.translatable("block.windtunnel.wind_tunnel_controller")
            );
            player.openMenu(provider, pos);
            return InteractionResult.CONSUME;
        }
    }

    protected void neighborChanged(
        BlockState state,
        Level level,
        BlockPos pos,
        Block neighborBlock,
        BlockPos neighborPos,
        boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered), 2);
            WindTunnelNetwork.refreshFromController(level, pos);
        }

    }

    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            boolean powered = level.hasNeighborSignal(pos);
            if (state.getValue(POWERED) != powered) {
                level.setBlock(pos, state.setValue(POWERED, powered), 2);
            }

            WindTunnelNetwork.refreshFromController(level, pos);
        }

    }

    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                WindTunnelNetwork.refreshAfterControllerRemoved(level, pos);
            }

            super.onRemove(state, level, pos, newState, isMoving);
        } else {
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    public BlockState rotate(BlockState state, Rotation rotation) {
        return state;
    }

    public BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
