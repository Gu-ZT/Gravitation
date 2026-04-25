package dev.dubhe.gravitation.block;

import com.mojang.serialization.MapCodec;
import dev.dubhe.gravitation.windtunnel.WindTunnelMountSelection;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WindTunnelMountInterfaceBlock extends Block {
    public static final MapCodec<WindTunnelMountInterfaceBlock> CODEC = simpleCodec(WindTunnelMountInterfaceBlock::new);
    public static final DirectionProperty FACING;
    private static final VoxelShape SHAPE;

    public WindTunnelMountInterfaceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction value = state.getValue(FACING);
        return state.setValue(FACING, mirror.getRotation(value).rotate(value));
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else if (!(Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel)) {
            player.displayClientMessage(Component.translatable("block.gravitation.wind_tunnel_mount_interface.not_on_aircraft"), true);
            return InteractionResult.CONSUME;
        } else {
            WindTunnelMountSelection.store(player, level.dimension(), pos);
            player.displayClientMessage(Component.translatable("block.gravitation.wind_tunnel_mount_interface.selected"), true);
            return InteractionResult.CONSUME;
        }
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    static {
        FACING = BlockStateProperties.FACING;
        SHAPE = Shapes.block();
    }
}
