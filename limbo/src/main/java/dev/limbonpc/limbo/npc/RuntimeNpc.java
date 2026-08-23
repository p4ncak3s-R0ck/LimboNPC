package dev.limbonpc.limbo.npc;

import com.loohp.limbo.entity.ArmorStand;
import java.util.List;

public record RuntimeNpc(NpcDefinition definition, PlayerNpcEntity entity, List<ArmorStand> holograms) {
    public int entityId() { return entity.getEntityId(); }
}
