package com.npcgemini.entity;

import com.npcgemini.profile.BotProfile;
import com.npcgemini.profile.ProfileManager;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

public class EntityNPC extends EntityCreature {

    private static final DataParameter<String> PROFILE_ID =
            EntityDataManager.createKey(EntityNPC.class, DataSerializers.STRING);
    private static final DataParameter<String> SKIN_ID =
            EntityDataManager.createKey(EntityNPC.class, DataSerializers.STRING);

    public static final int INV_SIZE = 27;
    private final NonNullList<ItemStack> inventory = NonNullList.withSize(INV_SIZE, ItemStack.EMPTY);

    // Solo cliente: burbuja de texto
    private String bubbleText = null;
    private long bubbleUntil = 0L;

    public EntityNPC(World world) {
        super(world);
        this.setSize(0.6F, 1.8F);
        this.setEntityInvulnerable(true);
        this.enablePersistence();
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(PROFILE_ID, "luna");
        this.dataManager.register(SKIN_ID, "luna");
    }

    @Override
    protected void initEntityAI() {
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        this.tasks.addTask(2, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.25D);
    }

    public String getProfileId() {
        return this.dataManager.get(PROFILE_ID);
    }

    public void setProfileId(String id) {
        if (id == null || id.isEmpty()) id = "luna";
        this.dataManager.set(PROFILE_ID, id.toLowerCase());
        BotProfile p = ProfileManager.get(id);
        if (p != null) {
            setSkinId(p.skinId);
        }
    }

    public String getSkinId() {
        return this.dataManager.get(SKIN_ID);
    }

    public void setSkinId(String id) {
        if (id == null || id.isEmpty()) id = "luna";
        this.dataManager.set(SKIN_ID, id);
    }

    public BotProfile getProfile() {
        return ProfileManager.getOrCreate(getProfileId());
    }

    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= INV_SIZE) return ItemStack.EMPTY;
        return inventory.get(slot);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= INV_SIZE) return;
        inventory.set(slot, stack == null ? ItemStack.EMPTY : stack);
    }

    /** Pasa todo el inventario del NPC al jugador. Devuelve cuantos stacks se entregaron completos. */
    public int deliverAllTo(EntityPlayer player) {
        int delivered = 0;
        for (int i = 0; i < INV_SIZE; i++) {
            ItemStack s = inventory.get(i);
            if (s.isEmpty()) continue;
            ItemStack copy = s.copy();
            if (player.inventory.addItemStackToInventory(copy)) {
                inventory.set(i, ItemStack.EMPTY);
                delivered++;
            } else if (copy.getCount() < s.getCount()) {
                s.setCount(copy.getCount());
            }
        }
        return delivered;
    }

    /** Solo cliente */
    public void showBubble(String text) {
        if (text == null || text.isEmpty()) {
            bubbleText = null;
            bubbleUntil = 0;
            return;
        }
        bubbleText = text;
        int words = text.trim().split("\\s+").length;
        long duration = 3000L + words * 250L;
        if (duration > 12000L) duration = 12000L;
        bubbleUntil = System.currentTimeMillis() + duration;
    }

    public String getBubbleText() {
        if (bubbleText == null) return null;
        if (System.currentTimeMillis() > bubbleUntil) {
            bubbleText = null;
            return null;
        }
        return bubbleText;
    }

    public float getBubbleAlpha() {
        if (bubbleText == null) return 0f;
        long left = bubbleUntil - System.currentTimeMillis();
        if (left > 800) return 1f;
        if (left <= 0) return 0f;
        return left / 800f;
    }

    @Override
    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        if (world.isRemote && hand == EnumHand.MAIN_HAND) {
            net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                    new com.npcgemini.client.gui.GuiNpcInteract(this));
            return true;
        }
        return super.processInteract(player, hand);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setString("ProfileId", getProfileId());
        compound.setString("SkinId", getSkinId());
        ItemStackHelper.saveAllItems(compound, inventory);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasKey("ProfileId")) {
            setProfileId(compound.getString("ProfileId"));
        }
        if (compound.hasKey("SkinId")) {
            setSkinId(compound.getString("SkinId"));
        }
        ItemStackHelper.loadAllItems(compound, inventory);
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }
}
