package com.windanesz.bridges.worldgen;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.Map;

// A bridge is built in one go once every chunk under it has been decorated
public final class BridgeSiteManager extends WorldSavedData {

    private final Map<String, Long> decorated = new HashMap<>();

    public BridgeSiteManager(String name) {
        super(name);
    }

    public static BridgeSiteManager get(World world, String name) {
        MapStorage storage = world.getPerWorldStorage();
        BridgeSiteManager progress = (BridgeSiteManager) storage.getOrLoadData(BridgeSiteManager.class, name);
        if (progress == null) {
            progress = new BridgeSiteManager(name);
            storage.setData(name, progress);
        }
        return progress;
    }

    public boolean decorate(String site, int bit, long all) {
        long done = decorated.getOrDefault(site, 0L) | 1L << bit;
        markDirty();
        if (done == all) {
            decorated.remove(site);
            return true;
        }
        decorated.put(site, done);
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        decorated.clear();
        NBTTagCompound sites = nbt.getCompoundTag("Decorated");
        for (String key : sites.getKeySet()) {
            decorated.put(key, sites.getLong(key));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagCompound sites = new NBTTagCompound();
        decorated.forEach(sites::setLong);
        nbt.setTag("Decorated", sites);
        return nbt;
    }
}
