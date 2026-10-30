package com.npcgemini.client.gui;

import com.npcgemini.entity.EntityNPC;
import com.npcgemini.profile.BotProfile;
import com.npcgemini.profile.ProfileManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
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
    private GuiTextField fieldSkin;
    private GuiButton btnCaptures;
    private GuiButton btnSave;
    private GuiButton btnClose;

    private boolean captures;

    public GuiNpcMenu(EntityNPC npc) {
        this.npc = npc;
        this.profile = npc.getProfile();
        this.captures = profile.capturesEnabled;
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
        y += 28;

        fieldSkin = new GuiTextField(7, fontRenderer, cx - fw / 2, y, fw, 18);
        fieldSkin.setMaxStringLength(40);
        fieldSkin.setText(profile.skinId != null ? profile.skinId : "luna");
        y += 32;

        btnCaptures = new GuiButton(10, cx - fw / 2, y, fw, 20,
                "Capturas: " + (captures ? "§aON" : "§cOFF"));
        buttonList.add(btnCaptures);
        y += 28;

        btnSave = new GuiButton(1, cx - fw / 2, y, fw / 2 - 4, 20, "Guardar");
        btnClose = new GuiButton(0, cx + 4, y, fw / 2 - 4, 20, "Cerrar");
        buttonList.add(btnSave);
        buttonList.add(btnClose);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(null);
        } else if (button.id == 1) {
            save();
            mc.displayGuiScreen(null);
        } else if (button.id == 10) {
            captures = !captures;
            btnCaptures.displayString = "Capturas: " + (captures ? "§aON" : "§cOFF");
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
        String skin = fieldSkin.getText().trim();
        if (!skin.isEmpty()) {
            profile.skinId = skin.replace(".png", "");
        }
        ProfileManager.save(profile);
        npc.setProfileId(profile.id);
        npc.setSkinId(profile.skinId);
        npc.applyNameFromProfile(profile);
        com.npcgemini.client.SkinManager.reload();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // Escape
            mc.displayGuiScreen(null);
            return;
        }
        fieldName.textboxKeyTyped(typedChar, keyCode);
        fieldRole.textboxKeyTyped(typedChar, keyCode);
        fieldPersonality.textboxKeyTyped(typedChar, keyCode);
        fieldAttitude.textboxKeyTyped(typedChar, keyCode);
        fieldStyle.textboxKeyTyped(typedChar, keyCode);
        fieldLang.textboxKeyTyped(typedChar, keyCode);
        fieldLength.textboxKeyTyped(typedChar, keyCode);
        fieldSkin.textboxKeyTyped(typedChar, keyCode);
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
        fieldSkin.mouseClicked(mouseX, mouseY, mouseButton);
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
        y += 28;
        fontRenderer.drawString("Skin ID", lx - 50, y + 5, 0xAAAAAA);
        fieldSkin.drawTextBox();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
