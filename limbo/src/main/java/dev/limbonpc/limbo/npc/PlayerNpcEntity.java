package dev.limbonpc.limbo.npc;

import com.loohp.limbo.entity.DataWatcher.WatchableField;
import com.loohp.limbo.entity.DataWatcher.WatchableObjectType;
import com.loohp.limbo.entity.DataWatcher;
import com.loohp.limbo.entity.EntityType;
import com.loohp.limbo.entity.LivingEntity;
import com.loohp.limbo.location.Location;
import java.util.UUID;

public final class PlayerNpcEntity extends LivingEntity {
    private final DataWatcher watcher;
    @WatchableField(MetadataIndex = 15, WatchableObjectType = WatchableObjectType.BYTE)
    private byte mainHand = 1;
    @WatchableField(MetadataIndex = 16, WatchableObjectType = WatchableObjectType.BYTE)
    private byte skinLayers = (byte) 0x7F;
    @WatchableField(MetadataIndex = 17, WatchableObjectType = WatchableObjectType.FLOAT)
    private float additionalHearts = 0;
    @WatchableField(MetadataIndex = 18, WatchableObjectType = WatchableObjectType.VARINT)
    private int score = 0;

    public PlayerNpcEntity(UUID uuid, Location location) {
        super(EntityType.PLAYER, uuid, location);
        setGravity(false);
        setSilent(true);
        this.watcher = new DataWatcher(this);
    }

    @Override public DataWatcher getDataWatcher() { return watcher; }
    @Override public boolean isValid() { return true; }
    @Override public void remove() { }
}
