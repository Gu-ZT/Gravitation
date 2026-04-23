package dev.dubhe.gravitation;

import dev.anvilcraft.lib.v2.registrum.AbstractRegistrum;
import dev.anvilcraft.lib.v2.registrum.Registrum;
import dev.anvilcraft.lib.v2.registrum.builders.Builder;
import dev.anvilcraft.lib.v2.registrum.util.entry.RegistryEntry;
import dev.anvilcraft.lib.v2.util.nullness.NonNullFunction;
import dev.anvilcraft.lib.v2.util.nullness.NonNullSupplier;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

public class GravitationRegistrum extends AbstractRegistrum<GravitationRegistrum> {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(Registrum.class);

    private ResourceLocation currentSection;

    protected GravitationRegistrum(final ResourceLocation initialSection, String modid) {
        super(modid);
        this.currentSection = initialSection;
    }

    public GravitationRegistrum inSection(final ResourceLocation section) {
        this.currentSection = section;
        return this;
    }

    public static GravitationRegistrum create(ResourceLocation initialSection, String modid) {
        var ret = new GravitationRegistrum(initialSection, modid);
        Optional<IEventBus> modEventBus = ModList.get().getModContainerById(modid).map(ModContainer::getEventBus);
        modEventBus.ifPresentOrElse(
            ret::registerEventListeners, () -> {
                String message = "# [Registrum] Failed to register eventListeners for mod " + modid + ", This should be reported to this mod's dev #";
                StringBuilder hashtags = new StringBuilder().repeat("#", message.length());
                log.fatal(hashtags.toString());
                log.fatal(message);
                log.fatal(hashtags.toString());
            }
        );
        ret.defaultCreativeTab((ResourceKey<CreativeModeTab>) null);
        return ret;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <R, T extends R> RegistryEntry<R, T> accept(
        String name,
        ResourceKey<? extends Registry<R>> type,
        Builder<R, T, ?, ?> builder,
        NonNullSupplier<? extends T> creator,
        NonNullFunction<DeferredHolder<R, T>, ? extends RegistryEntry<R, T>> entryFactory
    ) {
        final RegistryEntry<R, T> entry = super.accept(name, type, builder, creator, entryFactory);

        if (type.equals(Registries.ITEM)) {
            final RegistryEntry<Item, ? extends Item> itemEntry = (RegistryEntry<Item, ? extends Item>) entry;
            SimulatedRegistrate.TAB_ITEMS.add(itemEntry::get);
            SimulatedRegistrate.ITEM_TO_SECTION.put(entry.getId(), this.currentSection);
        }

        return entry;
    }

    public void addExtraItem(final ResourceLocation item) {
        SimulatedRegistrate.TAB_ITEMS.add(() -> BuiltInRegistries.ITEM.get(item));
        SimulatedRegistrate.ITEM_TO_SECTION.put(item, this.currentSection);
    }
}
