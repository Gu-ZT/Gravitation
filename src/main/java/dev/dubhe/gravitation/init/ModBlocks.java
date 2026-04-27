package dev.dubhe.gravitation.init;

import com.simibubi.create.AllBlocks;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumRecipeProvider;
import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntry;
import dev.dubhe.gravitation.block.FanConcentratorBlock;
import dev.dubhe.gravitation.block.RedstoneMassEnergyConverterBlock;
import dev.eriksonn.aeronautics.index.AeroItems;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.VariantBlockStateBuilder;

import static dev.dubhe.gravitation.Gravitation.REGISTRUM;

public class ModBlocks {
    public static final BlockEntry<RedstoneMassEnergyConverterBlock> REDSTONE_QUALITY_ENERGY_CONVERTER = REGISTRUM
        .block("redstone_mass_energy_converter", RedstoneMassEnergyConverterBlock::new)
        .properties(p -> p.mapColor(MapColor.TERRACOTTA_BROWN))
        .properties(BlockBehaviour.Properties::noOcclusion)
        .blockstate((context, provider) -> {
            ModelFile off = provider.models().getExistingFile(context.getId().withPrefix("block/"));
            ModelFile on = provider.models().getExistingFile(context.getId().withPrefix("block/").withSuffix("_on"));
            VariantBlockStateBuilder.PartialBlockstate builder = provider.getVariantBuilder(context.get())
                .partialState()
                .with(RedstoneMassEnergyConverterBlock.POWER, 0)
                .addModels(new ConfiguredModel(off));
            for (int i = 1; i < 16; i++) {
                builder.partialState()
                    .with(RedstoneMassEnergyConverterBlock.POWER, i)
                    .addModels(new ConfiguredModel(on));
            }
        })
        .simpleItem()
        .recipe((context, provider) -> ShapedRecipeBuilder
            .shaped(RecipeCategory.REDSTONE, context.get())
            .pattern("DBD")
            .pattern("AEC")
            .pattern("DXD")
            .define('D', AeroItems.ENDSTONE_POWDER)
            .define('E', Items.REDSTONE_BLOCK)
            .define('X', AllBlocks.BRASS_CASING)
            .define('A', Items.CHIPPED_ANVIL)
            .define('B', Items.ANVIL)
            .define('C', Items.DAMAGED_ANVIL)
            .unlockedBy("has_endstone_powder", RegistrumRecipeProvider.has(AeroItems.ENDSTONE_POWDER))
            .unlockedBy("has_redstone_block", RegistrumRecipeProvider.has(Items.REDSTONE_BLOCK))
            .unlockedBy("has_brass_casing", RegistrumRecipeProvider.has(AllBlocks.BRASS_CASING))
            .unlockedBy("has_chipped_anvil", RegistrumRecipeProvider.has(Items.CHIPPED_ANVIL))
            .unlockedBy("has_anvil", RegistrumRecipeProvider.has(Items.ANVIL))
            .unlockedBy("has_damaged_anvil", RegistrumRecipeProvider.has(Items.DAMAGED_ANVIL))
            .save(provider)
        )
        .register();

    public static final BlockEntry<FanConcentratorBlock> FAN_CONCENTRATOR = REGISTRUM
        .block("fan_concentrator", FanConcentratorBlock::new)
        .properties(properties -> properties.mapColor(MapColor.METAL)
            .strength(3.5F, 6.0F)
            .sound(SoundType.COPPER)
            .noOcclusion()
            .requiresCorrectToolForDrops()
        )
        .blockstate((ctx, provider) -> provider
            .directionalBlock(ctx.get(), provider.models().getExistingFile(ctx.getId().withPrefix("block/")))
        )
        .simpleItem()
        .recipe((context, provider) -> ShapedRecipeBuilder
            .shaped(RecipeCategory.MISC, context.get())
            .pattern("ISI")
            .pattern("SCS")
            .pattern("ISI")
            .define('I', Items.IRON_BARS)
            .define('S', AllBlocks.SHAFT.get())
            .define('C', AllBlocks.ANDESITE_CASING.get())
            .unlockedBy("has_iron_bars", RegistrumRecipeProvider.has(Items.IRON_BARS))
            .unlockedBy("has_shaft", RegistrumRecipeProvider.has(AllBlocks.SHAFT.get()))
            .unlockedBy("has_andesite_casing", RegistrumRecipeProvider.has(AllBlocks.ANDESITE_CASING.get()))
            .save(provider)
        )
        .register();

    public static void register() {
    }
}
