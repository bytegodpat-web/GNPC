package com.npcgemini.entity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registro de todos los NPC del mundo (se guarda con el mundo). Permite listar, teletransportar
 * o eliminar un NPC aunque su chunk no esté cargado, y conserva nombre/identidad.
 */
public class NpcRegistryData extends WorldSavedData {

    public static final String DATA_NAME = "npcgemini_registry";

    public static class Entry {
        public UUID uuid;
        public String name;
        public String profileId;
        public int dim;
        public BlockPos pos;
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public NpcRegistryData() { super(DATA_NAME); }
    public NpcRegistryData(String name) { super(name); }

    public static NpcRegistryData get(World anyWorld) {
        if (anyWorld == null || anyWorld.getMinecraftServer() == null) return null;
        World overworld = anyWorld.getMinecraftServer().getWorld(0);
        MapStorage storage = overworld.getMapStorage();
        NpcRegistryData data = (NpcRegistryData) storage.getOrLoadData(NpcRegistryData.class, DATA_NAME);
        if (data == null) {
            data = new NpcRegistryData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public static void touch(EntityNPC npc) {
        if (npc.world.isRemote) return;
        NpcRegistryData d = get(npc.world);
        if (d == null) return;
        Entry e = d.entries.get(npc.getUniqueID());
        if (e == null) { e = new Entry(); e.uuid = npc.getUniqueID(); d.entries.put(e.uuid, e); }
        e.name = npc.getName();
        e.profileId = npc.getProfileId();
        e.dim = npc.dimension;
        e.pos = npc.getPosition();
        d.markDirty();
    }

    public static void remove(World world, UUID id) {
        NpcRegistryData d = get(world);
        if (d != null && d.entries.remove(id) != null) d.markDirty();
    }

    public Map<UUID, Entry> all() { return entries; }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        entries.clear();
        NBTTagList list = nbt.getTagList("Npcs", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            if (!c.hasUniqueId("Id")) continue;
            Entry e = new Entry();
            e.uuid = c.getUniqueId("Id");
            e.name = c.getString("Name");
            e.profileId = c.getString("Profile");
            e.dim = c.getInteger("Dim");
            e.pos = BlockPos.fromLong(c.getLong("Pos"));
            entries.put(e.uuid, e);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Entry e : entries.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setUniqueId("Id", e.uuid);
            c.setString("Name", e.name == null ? "" : e.name);
            c.setString("Profile", e.profileId == null ? "" : e.profileId);
            c.setInteger("Dim", e.dim);
            c.setLong("Pos", e.pos == null ? 0L : e.pos.toLong());
            list.appendTag(c);
        }
        nbt.setTag("Npcs", list);
        return nbt;
    }
}
