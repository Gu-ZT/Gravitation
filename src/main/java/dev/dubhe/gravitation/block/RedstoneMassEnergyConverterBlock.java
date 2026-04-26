package dev.dubhe.gravitation.block;

import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.dubhe.gravitation.block.entity.RedstoneMassEnergyConverterBlockEntity;
import dev.dubhe.gravitation.init.ModBlockEntities;
import dev.dubhe.gravitation.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import javax.annotation.Nullable;

public class RedstoneMassEnergyConverterBlock extends CasingBlock implements IBE<RedstoneMassEnergyConverterBlockEntity> {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public RedstoneMassEnergyConverterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(POWER, 0)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
        super.createBlockStateDefinition(builder);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(POWER, context.getLevel().getBestNeighborSignal(context.getClickedPos()));
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide()) {
            return;
        }
        int thisPower = state.getValue(POWER);
        int getPower = level.getBestNeighborSignal(pos);
        if (thisPower == getPower) {
            return;
        }
        level.setBlock(pos, state.setValue(POWER, getPower), 3);
    }

    public static double getMass(BlockGetter level, BlockPos pos, BlockState blockState) {
        if (!blockState.is(ModBlocks.REDSTONE_QUALITY_ENERGY_CONVERTER)) return 1.0d;
        if (!(level.getBlockEntity(pos) instanceof RedstoneMassEnergyConverterBlockEntity blockEntity)) return 1.0d;
        Integer power = blockState.getValue(POWER);
        double maxConverterValue = blockEntity.getMaxConverterValue();
        return Math.clamp(maxConverterValue * ((power + 1) / 16.0d), 0.25d, maxConverterValue);
    }

    @Override
    public Class<RedstoneMassEnergyConverterBlockEntity> getBlockEntityClass() {
        return RedstoneMassEnergyConverterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RedstoneMassEnergyConverterBlockEntity> getBlockEntityType() {
        return ModBlockEntities.REDSTONE_QUALITY_ENERGY_CONVERTER.get();
    }
}
