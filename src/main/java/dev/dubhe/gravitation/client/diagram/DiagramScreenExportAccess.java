package dev.dubhe.gravitation.client.diagram;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.simulated_team.simulated.content.entities.diagram.DiagramConfig;

public interface DiagramScreenExportAccess {
    ClientSubLevel gravitation$getDiagramSubLevel();

    DiagramConfig gravitation$getDiagramConfig();

    String gravitation$getDiagramName();

    float gravitation$getLastPartialTicks();
}
