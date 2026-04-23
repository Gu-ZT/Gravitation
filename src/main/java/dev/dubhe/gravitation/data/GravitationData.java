package dev.dubhe.gravitation.data;

import dev.anvilcraft.lib.v2.registrum.providers.DataProviderInitializer;
import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.dubhe.gravitation.Gravitation;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

import static dev.dubhe.gravitation.Gravitation.REGISTRUM;

@EventBusSubscriber(modid = Gravitation.MOD_ID)
public class GravitationData {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        PackOutput packOutput = generator.getPackOutput();
    }

    public static void init() {
        DataProviderInitializer genInit = REGISTRUM.getDataGenInitializer();
        REGISTRUM.addDataGenerator(ProviderType.LANG, LangHandler::init);
    }
}
