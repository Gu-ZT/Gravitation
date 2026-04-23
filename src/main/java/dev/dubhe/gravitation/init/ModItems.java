package dev.dubhe.gravitation.init;

import com.simibubi.create.AllItems;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumRecipeProvider;
import dev.anvilcraft.lib.v2.registrum.util.entry.ItemEntry;
import dev.dubhe.gravitation.item.SurvivalPhysicsStaffItem;
import dev.eriksonn.aeronautics.index.AeroItems;
import dev.eriksonn.aeronautics.neoforge.index.AeroFluidsNeoForge;
import dev.simulated_team.simulated.index.SimItems;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Item;

import java.util.Optional;

import static dev.dubhe.gravitation.Gravitation.REGISTRUM;

public class ModItems {
    public static final ItemEntry<SurvivalPhysicsStaffItem> PHYSICS_STAFF = REGISTRUM
        .item("physics_staff", SurvivalPhysicsStaffItem::new)
        .model((ignored, ignored1) -> {
        })
        .recipe((context, provider) -> {
                Optional<Item> bucket = AeroFluidsNeoForge.LEVITITE_BLEND.getBucket();
                if (bucket.isEmpty()) return;
                Item bucketItem = bucket.get();
                ShapedRecipeBuilder
                    .shaped(RecipeCategory.TOOLS, context.get())
                    .pattern(" AD")
                    .pattern(" BA")
                    .pattern("C  ")
                    .define('A', AeroItems.ENDSTONE_POWDER)
                    .define('B', SimItems.GYRO_MECHANISM)
                    .define('C', AllItems.WRENCH)
                    .define('D', bucketItem)
                    .unlockedBy("has_endstone_powder", RegistrumRecipeProvider.has(AeroItems.ENDSTONE_POWDER))
                    .unlockedBy("has_gyro_mechanism", RegistrumRecipeProvider.has(SimItems.GYRO_MECHANISM))
                    .unlockedBy("has_wrench", RegistrumRecipeProvider.has(AllItems.WRENCH))
                    .unlockedBy("has_levitite_blend_bucket", RegistrumRecipeProvider.has(bucketItem))
                    .save(provider);
            }
        )
        .register();

    public static void register() {
    }
}
