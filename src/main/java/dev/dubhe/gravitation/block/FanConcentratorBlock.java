
package dev.dubhe.gravitation.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import dev.dubhe.gravitation.block.entity.WindTunnelBlockEntity;
import dev.dubhe.gravitation.init.ModBlockEntities;
import dev.dubhe.gravitation.windtunnel.WindTunnelNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class FanConcentratorBlock extends BaseEntityBlock implements EntityBlock {
    public static final MapCodec<FanConcentratorBlock> CODEC = simpleCodec(FanConcentratorBlock::new);
    public static final DirectionProperty FACING;
    private static final VoxelShape SHAPE;

    public FanConcentratorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction value = state.getValue(FACING);
        return state.setValue(FACING, mirror.getRotation(value).rotate(value));
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction towardsFan = state.getValue(FACING).getOpposite();
        if (!(level.getBlockEntity(pos.relative(towardsFan)) instanceof IAirCurrentSource source)) {
            return false;
        }
        return source.getAirflowOriginSide() == towardsFan.getOpposite();
    }

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.FAN_CONCENTRATOR.create(pos, state);
    }

    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> blockEntityType
    ) {
        if (blockEntityType != ModBlockEntities.FAN_CONCENTRATOR.get()) {
            return null;
        } else {
            return level.isClientSide ? createTickerHelper(
                blockEntityType,
                ModBlockEntities.FAN_CONCENTRATOR.get(),
                WindTunnelBlockEntity::clientTick
            ) : null;
        }
    }

    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            WindTunnelNetwork.refreshTunnel(level, pos);
            if (!this.canSurvive(state, level, pos)) {
                level.destroyBlock(pos, true);
            }
        }

    }

    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            WindTunnelNetwork.refreshAdjacentNetworks(level, pos);
        }

        super.onRemove(state, level, pos, newState, isMoving);
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
        if (!level.isClientSide) {
            if (neighborPos.equals(pos.relative(state.getValue(FACING).getOpposite())) && !this.canSurvive(state, level, pos)) {
                level.destroyBlock(pos, true);
                return;
            }
            WindTunnelNetwork.refreshTunnel(level, pos);
        }

    }

    static {
        FACING = BlockStateProperties.FACING;
        SHAPE = Shapes.block();
    }
}
