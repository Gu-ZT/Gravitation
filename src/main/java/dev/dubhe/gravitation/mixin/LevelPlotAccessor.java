package dev.dubhe.gravitation.mixin;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelPlot.class)
public interface LevelPlotAccessor {
    @Accessor
    SubLevelContainer getContainer();
}
