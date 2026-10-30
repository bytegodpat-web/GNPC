package com.npcgemini.client.gui;

import com.npcgemini.entity.EntityNPC;
import com.npcgemini.profile.BotProfile;
import com.npcgemini.profile.ProfileManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.util.Util;
import net.minecraft.util.text.TextComponentString;
import com.npcgemini.NPCGeminiMod;
import com.npcgemini.client.ChatHandler;
import com.npcgemini.client.SkinManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Menú simple de personalidad del NPC (fase 3).
 * Click derecho agachado sobre el NPC.
 */
public class GuiNpcMenu extends GuiScreen {

    private final EntityNPC npc;
    private BotProfile profile;

    private GuiTextField fieldName;
    private GuiTextField fieldRole;
    private GuiTextField fieldPersonality;
    private GuiTextField fieldAttitude;
    private GuiTextField fieldStyle;
    private GuiTextField fieldLang;
    private GuiTextField fieldLength;
    private GuiButton btnCaptures;
    private GuiButton btnSave;
    private GuiButton btnClose;

    private boolean captures;

    // Skin
    private final List<String> skins = new ArrayList<>();
    private int skinIndex = 0;
    private String currentSkin;
    private boolean slim;
    private final String origSkin;
    private final boolean origSlim;
    private boolean saved = false;
    private GuiButton btnSlim;

    private final GuiScreen parent;

    public GuiNpcMenu(EntityNPC npc) {
        this(npc, null);
    }

    public GuiNpcMenu(EntityNPC npc, GuiScreen parent) {
        this.parent = parent;
        this.npc = npc;
        this.profile = npc.getProfile();
        this.captures = profile.capturesEnabled;
        this.currentSkin = npc.getSkinId();
        this.slim = profile.slimArms;
        this.origSkin = npc.getSkinId();
        this.origSlim = profile.slimArms;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int cx = this.width / 2;
        int y = 30;
        int fw = 220;

        fieldName = new GuiTextField(0, fontRenderer, cx - fw / 2, y, fw, 18);
        fieldName.setMaxStringLength(32);
        fieldName.setText(profile.name);
        y += 28;

        fieldRole = new GuiTextField(1, fontRenderer, cx - fw / 2, y, fw, 18);
        fieldRole.setMaxStringLength(80);
        fieldRole.setText(profile.role);
        y += 28;

        fieldPersonality = new GuiTextField(2, fontRenderer, cx - fw / 2, y, fw, 18);
        fieldPersonality.setMaxStringLength(120);
        fieldPersonality.setText(profile.personality);
        y += 28;

        fieldAttitude = new GuiTextField(3, fontRenderer, cx - fw / 2, y, fw, 18);
        fieldAttitude.setMaxStringLength(80);
        fieldAttitude.setText(profile.attitude);
        y += 28;

        fieldStyle = new GuiTextField(4, fontRenderer, cx - fw / 2, y, fw, 18);
        fieldStyle.setMaxStringLength(60);
        fieldStyle.setText(profile.speechStyle);
        y += 28;

        fieldLang = new GuiTextField(5, fontRenderer, cx - fw / 2, y, fw / 2 - 4, 18);
        fieldLang.setMaxStringLength(20);
        fieldLang.setText(profile.language);

        fieldLength = new GuiTextField(6, fontRenderer, cx + 4, y, fw / 2 - 4, 18);
        fieldLength.setMaxStringLength(4);
        fieldLength.setText(profile.responseLength);
        y += 32;

        btnCaptures = new GuiButton(10, cx - fw / 2, y, fw, 20,
                "Capturas: " + (captures ? "§aON" : "§cOFF"));
        buttonList.add(btnCaptures);
        y += 28;

        btnSave = new GuiButton(1, cx - fw / 2, y, fw / 2 - 4, 20, "Guardar");
        btnClose = new GuiButton(0, cx + 4, y, fw / 2 - 4, 20, "Cerrar");
        buttonList.add(btnSave);
        buttonList.add(btnClose);

        // Panel de skins (columna derecha)
        loadSkinList();
        int px = cx + 114;
        int pw = 96;
        buttonList.add(new GuiButton(20, px, 110, 22, 20, "<"));
        buttonList.add(new GuiButton(21, px + pw - 22, 110, 22, 20, ">"));
        btnSlim = new GuiButton(22, px, 134, pw, 20, slimLabel());
        buttonList.add(btnSlim);
        buttonList.add(new GuiButton(23, px, 158, pw, 20, "Abrir carpeta"));
        buttonList.add(new GuiButton(24, px, 182, pw, 20, "Recargar"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (!saved) {
            // Cerrar sin guardar: deshacer la vista previa de la skin
            profile.slimArms = origSlim;
            npc.setSkinId(origSkin);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(parent);
        } else if (button.id == 1) {
            save();
            mc.displayGuiScreen(parent);
        } else if (button.id == 10) {
            captures = !captures;
            btnCaptures.displayString = "Capturas: " + (captures ? "§aON" : "§cOFF");
        } else if (button.id == 20 || button.id == 21) {
            if (!skins.isEmpty()) {
                int n = skins.size();
                skinIndex = (skinIndex + (button.id == 21 ? 1 : n - 1)) % n;
                currentSkin = skins.get(skinIndex);
                npc.setSkinId(currentSkin); // vista previa
            }
        } else if (button.id == 22) {
            slim = !slim;
            profile.slimArms = slim; // vista previa
            btnSlim.displayString = slimLabel();
        } else if (button.id == 23) {
            File dir = new File(ProfileManager.getConfigRoot(), "skins");
            openFolder(dir);
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString("\u00a77Carpeta de skins: " + dir.getAbsolutePath()));
            }
        } else if (button.id == 24) {
            SkinManager.reload();
            loadSkinList();
        }
    }

    private String slimLabel() {
        return "Brazos: " + (slim ? "finos" : "cl\u00e1sicos");
    }

    private void loadSkinList() {
        skins.clear();
        skins.addAll(Arrays.asList(SkinManager.BUILTIN));
        File dir = new File(ProfileManager.getConfigRoot(), "skins");
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                String n = f.getName();
                String id = n.substring(0, n.length() - 4);
                if (!skins.contains(id)) skins.add(id);
            }
        }
        if (currentSkin == null || currentSkin.isEmpty()) currentSkin = "luna";
        int i = skins.indexOf(currentSkin);
        if (i < 0) {
            skins.add(currentSkin);
            i = skins.size() - 1;
        }
        skinIndex = i;
    }

    private static void openFolder(File dir) {
        try {
            if (!dir.exists()) dir.mkdirs();
            String path = dir.getAbsolutePath();
            Util.EnumOS os = Util.getOSType();
            if (os == Util.EnumOS.OSX) {
                new ProcessBuilder("/usr/bin/open", path).start();
            } else if (os == Util.EnumOS.WINDOWS) {
                new ProcessBuilder("explorer.exe", path).start();
            } else {
                new ProcessBuilder("xdg-open", path).start();
            }
        } catch (Exception e) {
            NPCGeminiMod.logger.warn("No se pudo abrir la carpeta: " + e.getMessage());
        }
    }

    private void save() {
        profile.name = fieldName.getText().trim().isEmpty() ? profile.name : fieldName.getText().trim();
        profile.role = fieldRole.getText().trim();
        profile.personality = fieldPersonality.getText().trim();
        profile.attitude = fieldAttitude.getText().trim();
        profile.speechStyle = fieldStyle.getText().trim();
        profile.language = fieldLang.getText().trim().isEmpty() ? "español" : fieldLang.getText().trim();
        profile.responseLength = fieldLength.getText().trim().isEmpty() ? "2" : fieldLength.getText().trim();
        profile.capturesEnabled = captures;
        profile.skinId = currentSkin;
        profile.slimArms = slim;
        saved = true;
        ProfileManager.save(profile);
        npc.setProfileId(profile.id);
        npc.setSkinId(profile.skinId);
        // Aplicar tambien en el servidor integrado para que la skin persista al guardar el mundo
        final EntityNPC serverNpc = ChatHandler.resolveServerNpc(npc);
        if (serverNpc != null && mc.getIntegratedServer() != null) {
            final String sid = profile.skinId;
            final String pid = profile.id;
            mc.getIntegratedServer().addScheduledTask(() -> {
                serverNpc.setProfileId(pid);
                serverNpc.setSkinId(sid);
            });
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // Escape
            mc.displayGuiScreen(parent);
            return;
        }
        fieldName.textboxKeyTyped(typedChar, keyCode);
        fieldRole.textboxKeyTyped(typedChar, keyCode);
        fieldPersonality.textboxKeyTyped(typedChar, keyCode);
        fieldAttitude.textboxKeyTyped(typedChar, keyCode);
        fieldStyle.textboxKeyTyped(typedChar, keyCode);
        fieldLang.textboxKeyTyped(typedChar, keyCode);
        fieldLength.textboxKeyTyped(typedChar, keyCode);
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        fieldName.mouseClicked(mouseX, mouseY, mouseButton);
        fieldRole.mouseClicked(mouseX, mouseY, mouseButton);
        fieldPersonality.mouseClicked(mouseX, mouseY, mouseButton);
        fieldAttitude.mouseClicked(mouseX, mouseY, mouseButton);
        fieldStyle.mouseClicked(mouseX, mouseY, mouseButton);
        fieldLang.mouseClicked(mouseX, mouseY, mouseButton);
        fieldLength.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        drawCenteredString(fontRenderer, "§dPersonalidad de " + profile.name, cx, 12, 0xFFFFFF);

        int y = 30;
        int lx = cx - 110 - 4;
        fontRenderer.drawString("Nombre", lx - 50, y + 5, 0xAAAAAA);
        fieldName.drawTextBox();
        y += 28;
        fontRenderer.drawString("Rol", lx - 50, y + 5, 0xAAAAAA);
        fieldRole.drawTextBox();
        y += 28;
        fontRenderer.drawString("Personalidad", lx - 50, y + 5, 0xAAAAAA);
        fieldPersonality.drawTextBox();
        y += 28;
        fontRenderer.drawString("Actitud", lx - 50, y + 5, 0xAAAAAA);
        fieldAttitude.drawTextBox();
        y += 28;
        fontRenderer.drawString("Estilo", lx - 50, y + 5, 0xAAAAAA);
        fieldStyle.drawTextBox();
        y += 28;
        fontRenderer.drawString("Idioma", lx - 50, y + 5, 0xAAAAAA);
        fieldLang.drawTextBox();
        fontRenderer.drawString("Frases", cx + 4 + 60, y + 5, 0xAAAAAA);
        fieldLength.drawTextBox();

        // Panel de skins: vista previa 3D y nombre
        int px = cx + 114;
        int pw = 96;
        drawCenteredString(fontRenderer, "Skin", px + pw / 2, 20, 0xAAAAAA);
        GuiInventory.drawEntityOnScreen(px + pw / 2, 100, 40,
                (float) (px + pw / 2 - mouseX), (float) (60 - mouseY), npc);
        String label = fontRenderer.trimStringToWidth(currentSkin == null ? "" : currentSkin, pw - 48);
        drawCenteredString(fontRenderer, label, px + pw / 2, 116, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
