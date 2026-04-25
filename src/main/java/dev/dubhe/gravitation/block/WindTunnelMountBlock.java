package dev.dubhe.gravitation.block;

import com.mojang.serialization.MapCodec;
import dev.dubhe.gravitation.block.entity.WindTunnelMountBlockEntity;
import dev.dubhe.gravitation.init.ModBlockEntities;
import dev.dubhe.gravitation.menu.WindTunnelMountMenu;
import dev.dubhe.gravitation.windtunnel.WindTunnelMountSelection;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class WindTunnelMountBlock extends BaseEntityBlock implements EntityBlock {
    public static final MapCodec<WindTunnelMountBlock> CODEC = simpleCodec(WindTunnelMountBlock::new);
    public static final DirectionProperty FACING;
    private static final VoxelShape SHAPE;

    public WindTunnelMountBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction value = state.getValue(FACING);
        return state.setValue(FACING, mirror.getRotation(value).rotate(value));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{FACING});
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.WIND_TUNNEL_MOUNT.create(pos, state);
    }

    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> blockEntityType
    ) {
        return null;
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            BlockEntity var7 = level.getBlockEntity(pos);
            if (var7 instanceof WindTunnelMountBlockEntity mount) {
                if (player.isShiftKeyDown()) {
                    WindTunnelMountSelection.Selection selection = WindTunnelMountSelection.get(player);
                    if (selection != null) {
                        return this.bindSelection(level, player, mount, selection);
                    }

                    if (mount.hasBinding()) {
                        mount.clearBinding();
                        player.displayClientMessage(Component.translatable("block.windtunnel.wind_tunnel_mount.binding_cleared"), true);
                        return InteractionResult.CONSUME;
                    }
                }

                MenuProvider provider = new SimpleMenuProvider(
                    (containerId, inventory, user) -> new WindTunnelMountMenu(containerId, pos),
                    Component.translatable("block.windtunnel.wind_tunnel_mount")
                );
                player.openMenu(provider, pos);
                return InteractionResult.CONSUME;
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    @SuppressWarnings("SameReturnValue")
    private InteractionResult bindSelection(
        Level level,
        Player player,
        WindTunnelMountBlockEntity mount,
        WindTunnelMountSelection.Selection selection
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.CONSUME;
        }
        if (!selection.dimension().equals(level.dimension())) {
            player.displayClientMessage(Component.translatable("block.windtunnel.wind_tunnel_mount.selection_wrong_dimension"), true);
            return InteractionResult.CONSUME;
        }
        BlockState selectedState = level.getBlockState(selection.pos());
        Block var8 = selectedState.getBlock();
        if (!(var8 instanceof WindTunnelMountInterfaceBlock)) {
            WindTunnelMountSelection.clear(player);
            player.displayClientMessage(Component.translatable("block.windtunnel.wind_tunnel_mount.selection_invalid"), true);
            return InteractionResult.CONSUME;
        }
        SubLevel var9 = Sable.HELPER.getContaining(level, selection.pos());
        if (var9 instanceof ServerSubLevel subLevel) {
            mount.bind(
                serverLevel,
                subLevel.getUniqueId(),
                selection.pos(),
                selectedState.getValue(WindTunnelMountInterfaceBlock.FACING)
            );
            WindTunnelMountSelection.clear(player);
            player.displayClientMessage(Component.translatable("block.windtunnel.wind_tunnel_mount.bound"), true);
        } else {
            player.displayClientMessage(
                Component.translatable("block.windtunnel.wind_tunnel_mount.selection_not_aircraft"),
                true
            );
        }
        return InteractionResult.CONSUME;
    }

    static {
        FACING = BlockStateProperties.FACING;
        SHAPE = Shapes.block();
    }
}
