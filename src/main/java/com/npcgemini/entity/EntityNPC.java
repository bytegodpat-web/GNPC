package com.npcgemini.entity;

import com.npcgemini.profile.BotProfile;
import com.npcgemini.profile.ProfileManager;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

public class EntityNPC extends EntityCreature {

    private static final DataParameter<String> PROFILE_ID =
            EntityDataManager.createKey(EntityNPC.class, DataSerializers.STRING);
    private static final DataParameter<String> SKIN_ID =
            EntityDataManager.createKey(EntityNPC.class, DataSerializers.STRING);

    private String bubbleText = null;
    private long bubbleUntil = 0L;

    public EntityNPC(World world) {
        super(world);
        this.setSize(0.6F, 1.8F);
        this.setEntityInvulnerable(true);
        this.enablePersistence();
        this.setAlwaysRenderNameTag(true);
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
            applyNameFromProfile(p);
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

    /** Actualiza el nametag visible con el nombre del perfil */
    public void applyNameFromProfile(BotProfile p) {
        if (p == null) return;
        this.setCustomNameTag(p.name);
        this.setAlwaysRenderNameTag(true);
    }

    @Override
    public String getName() {
        BotProfile p = ProfileManager.get(getProfileId());
        if (p != null && p.name != null && !p.name.isEmpty()) {
            return p.name;
        }
        String tag = this.getCustomNameTag();
        if (tag != null && !tag.isEmpty()) return tag;
        return super.getName();
    }

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
            if (player.isSneaking()) {
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                        new com.npcgemini.client.gui.GuiNpcMenu(this));
            } else {
                BotProfile p = getProfile();
                if (p != null) {
                    applyNameFromProfile(p);
                    player.sendStatusMessage(
                            new net.minecraft.util.text.TextComponentString(
                                    "\u00a7d[" + p.name + "] \u00a7f*te mira y sonrie*"),
                            true);
                }
            }
            return true;
        }
        return super.processInteract(player, hand);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        // Asegurar nametag en cliente
        if (world.isRemote && ticksExisted % 40 == 0) {
            BotProfile p = ProfileManager.get(getProfileId());
            if (p != null) {
                String current = this.getCustomNameTag();
                if (current == null || !current.equals(p.name)) {
                    applyNameFromProfile(p);
                }
            }
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setString("ProfileId", getProfileId());
        compound.setString("SkinId", getSkinId());
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
        BotProfile p = ProfileManager.get(getProfileId());
        if (p != null) applyNameFromProfile(p);
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
