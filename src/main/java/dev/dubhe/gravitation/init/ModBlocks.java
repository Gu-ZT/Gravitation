package dev.dubhe.gravitation.init;

import com.simibubi.create.AllBlocks;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumRecipeProvider;
import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntry;
import dev.dubhe.gravitation.block.RedstoneMassEnergyConverterBlock;
import dev.eriksonn.aeronautics.index.AeroItems;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

import static dev.dubhe.gravitation.Gravitation.REGISTRUM;

public class ModBlocks {
    public static final BlockEntry<RedstoneMassEnergyConverterBlock> REDSTONE_QUALITY_ENERGY_CONVERTER = REGISTRUM
        .block("redstone_mass_energy_converter", RedstoneMassEnergyConverterBlock::new)
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

    public static void register() {
    }
}
