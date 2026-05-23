package dev.dubhe.gravitation.mixin;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

abstract class BlockSubLevelLiftProviderMixin$Sub {
    /** 记录当前 tick 内已应用过风场的 SubLevel，防止多帆叠加 */
    @Unique
    static Set<ServerSubLevel> gravitation$WIND_APPLIED_THIS_TICK = new HashSet<>();
    @Unique
    static AtomicLong gravitation$LAST_WIND_TICK = new AtomicLong(-1L);
}
