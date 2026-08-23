package dev.limbonpc.limbo.npc;

import com.loohp.limbo.entity.ArmorStand;
import com.loohp.limbo.entity.DataWatcher;
import com.loohp.limbo.location.Location;

/** A packet-only armor stand that is never registered with Limbo's ticking world. */
public final class HologramEntity extends ArmorStand {
    private final DataWatcher watcher;

    public HologramEntity(Location location) {
        super(location);
        this.watcher = new DataWatcher(this);
    }

    @Override public DataWatcher getDataWatcher() { return watcher; }
    @Override public boolean isValid() { return true; }
    @Override public void remove() { }
}
