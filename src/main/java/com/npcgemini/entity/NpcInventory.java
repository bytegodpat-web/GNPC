package com.npcgemini.entity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

/**
 * Vista IInventory del inventario del NPC. Se abre con un ContainerChest vanilla, así que
 * clics, arrastres, shift-clic y sincronización cliente/servidor los hace Minecraft (sin duplicados).
 * El título lleva el prefijo "GNPC|id|nombre" para que el cliente lo reconozca y dibuje la GUI propia.
 */
public class NpcInventory implements IInventory {

    public static final String TITLE_PREFIX = "GNPC|";

    private final EntityNPC npc;

    public NpcInventory(EntityNPC npc) { this.npc = npc; }

    @Override public int getSizeInventory() { return EntityNPC.INV_SIZE; }

    @Override public boolean isEmpty() {
        for (ItemStack s : npc.getInventoryList()) if (!s.isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getStackInSlot(int index) { return npc.getStackInSlot(index); }

    @Override public ItemStack decrStackSize(int index, int count) {
        return ItemStackHelper.getAndSplit(npc.getInventoryList(), index, count);
    }

    @Override public ItemStack removeStackFromSlot(int index) {
        return ItemStackHelper.getAndRemove(npc.getInventoryList(), index);
    }

    @Override public void setInventorySlotContents(int index, ItemStack stack) {
        if (index < 0 || index >= EntityNPC.INV_SIZE) return;
        npc.getInventoryList().set(index, stack == null ? ItemStack.EMPTY : stack);
    }

    @Override public int getInventoryStackLimit() { return 64; }
    @Override public void markDirty() { }

    @Override public boolean isUsableByPlayer(EntityPlayer player) {
        return !npc.isDead && player.getDistanceSq(npc) <= 100.0D;
    }

    @Override public void openInventory(EntityPlayer player) { }
    @Override public void closeInventory(EntityPlayer player) { }
    @Override public boolean isItemValidForSlot(int index, ItemStack stack) { return true; }
    @Override public int getField(int id) { return 0; }
    @Override public void setField(int id, int value) { }
    @Override public int getFieldCount() { return 0; }

    @Override public void clear() {
        for (int i = 0; i < EntityNPC.INV_SIZE; i++) npc.getInventoryList().set(i, ItemStack.EMPTY);
    }

    @Override public String getName() { return TITLE_PREFIX + npc.getEntityId() + "|" + npc.getName(); }
    @Override public boolean hasCustomName() { return true; }
    @Override public ITextComponent getDisplayName() { return new TextComponentString(getName()); }
}
