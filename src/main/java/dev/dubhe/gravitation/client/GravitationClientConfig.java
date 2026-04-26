package dev.dubhe.gravitation.client;

import dev.anvilcraft.lib.v2.config.CollapsibleObject;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;
import dev.dubhe.gravitation.Gravitation;
import net.neoforged.fml.config.ModConfig;

@Config(name = Gravitation.MOD_ID, type = ModConfig.Type.CLIENT)
public class GravitationClientConfig {
    @Comment("Contraption Diagram Export Config")
    @CollapsibleObject
    public DiagramExportConfig diagramExport = new DiagramExportConfig();

    public static class DiagramExportConfig {
        @Comment("Export 16:9 aspect ratio image (1920×1080, rendered at 448×252)")
        public boolean export16x9 = true;
        @Comment("Export 4:3 aspect ratio image (1920×1440, rendered at 256×192)")
        public boolean export4x3 = true;
        @Comment("Export 3:2 aspect ratio image (1920×1280, rendered at 384×256)")
        public boolean export3x2 = true;
        @Comment("Export 1:1 aspect ratio image (1920×1920, rendered at 256×256)")
        public boolean export1x1 = true;
    }
}
