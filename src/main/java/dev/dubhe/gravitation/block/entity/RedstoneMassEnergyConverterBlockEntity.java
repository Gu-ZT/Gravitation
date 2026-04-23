package dev.dubhe.gravitation.block.entity;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.dubhe.gravitation.Gravitation;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import javax.annotation.Nullable;

import static dev.dubhe.gravitation.Gravitation.REGISTRUM;

public class RedstoneMassEnergyConverterBlockEntity extends SmartBlockEntity {
    protected @Nullable ScrollValueBehaviour maxConverterValue;
    private static final MutableComponent SCROLL_OPTION_TITLE = REGISTRUM.addLang(
        "scroll_option",
        Gravitation.location("max_mass"),
        "Max Mass"
    );
    private static final String VALUE_FORMAT = "%s kpg";

    public RedstoneMassEnergyConverterBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState state
    ) {
        super(type, pos, state);
    }

    public final double getMaxConverterValue() {
        if (this.maxConverterValue == null) return 1.0d;
        return this.maxConverterValue.getValue();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        this.maxConverterValue = new ScrollValueBehaviour(
            SCROLL_OPTION_TITLE,
            this,
            new MassEnergyConverterValueBoxTransform()
        )
            .withFormatter(VALUE_FORMAT::formatted)
            .between(1, 100);
        this.maxConverterValue.value = 1;
        behaviours.add(this.maxConverterValue);
    }

    private static class MassEnergyConverterValueBoxTransform extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8f, 16);
        }
    }
}
