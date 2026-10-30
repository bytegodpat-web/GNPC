package com.npcgemini.client.gui;

import com.npcgemini.client.ChatHandler;
import com.npcgemini.entity.EntityNPC;
import com.npcgemini.profile.BotProfile;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Menu central del NPC (click derecho normal sobre el NPC).
 * Desde aqui se habla con el bot, se abre su inventario y se cambia su personalidad y skin.
 */
public class GuiNpcInteract extends GuiScreen {

    private static final int W = 260;
    private static final int H = 190;

    private final EntityNPC npc;
    private GuiTextField field;
    private GuiButton btnChatMode;

    public GuiNpcInteract(EntityNPC npc) {
        this.npc = npc;
    }

    private BotProfile profile() {
        return npc.getProfile();
    }

    private boolean isChatMode() {
        String m = ChatHandler.chatModeBot;
        return m != null && m.equalsIgnoreCase(profile().id);
    }

    private String chatModeLabel() {
        return "Modo chat: " + (isChatMode() ? "\u00a7aON" : "\u00a7cOFF");
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int left = width / 2 - W / 2;
        int top = height / 2 - H / 2;
        int bx = left + W / 2 + 5;
        int bw = W / 2 - 5;

        buttonList.add(new GuiButton(1, bx, top + 34, bw, 20, "Inventario"));
        buttonList.add(new GuiButton(2, bx, top + 58, bw, 20, "Personalidad y skin"));
        btnChatMode = new GuiButton(3, bx, top + 82, bw, 20, chatModeLabel());
        buttonList.add(btnChatMode);
        buttonList.add(new GuiButton(0, bx, top + 106, bw, 20, "Cerrar"));
        buttonList.add(new GuiButton(4, left + W - 56, top + 148, 56, 20, "Enviar"));

        String keep = field != null ? field.getText() : "";
        field = new GuiTextField(10, fontRenderer, left, top + 149, W - 62, 18);
        field.setMaxStringLength(200);
        field.setText(keep);
        field.setFocused(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        field.updateCursorCounter();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                mc.displayGuiScreen(null);
                break;
            case 1:
                mc.displayGuiScreen(new GuiNPCInventory(npc, this));
                break;
            case 2:
                mc.displayGuiScreen(new GuiNpcMenu(npc, this));
                break;
            case 3:
                BotProfile p = profile();
                if (isChatMode()) {
                    ChatHandler.chatModeBot = null;
                    if (mc.player != null) {
                        mc.player.sendMessage(new TextComponentString("\u00a77Modo chat desactivado."));
                    }
                } else {
                    ChatHandler.chatModeBot = p.id;
                    if (mc.player != null) {
                        mc.player.sendMessage(new TextComponentString(
                                "\u00a7aHablando con \u00a7d" + p.name
                                        + "\u00a7a. Todo lo que escribas en el chat ir\u00e1 al bot."));
                    }
                }
                btnChatMode.displayString = chatModeLabel();
                break;
            case 4:
                send();
                break;
            default:
                break;
        }
    }

    private void send() {
        String text = field.getText().trim();
        if (text.isEmpty()) return;
        String id = profile().id;
        // Cerrar primero: la respuesta sale en el chat y en la burbuja, y la captura no debe incluir el menu
        mc.displayGuiScreen(null);
        ChatHandler.sendToBot(id, text, true);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            send();
            return;
        }
        if (field.textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        field.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = width / 2 - W / 2;
        int top = height / 2 - H / 2;
        int bx = left + W / 2 + 5;

        drawRect(left - 8, top - 8, left + W + 8, top + H + 8, 0xB0000000);

        BotProfile p = profile();
        drawCenteredString(fontRenderer, "\u00a7d" + p.name, width / 2, top + 4, 0xFFFFFF);

        int pvx = left + W / 4 - 5;
        GuiInventory.drawEntityOnScreen(pvx, top + 128, 48,
                (float) (pvx - mouseX), (float) (top + 70 - mouseY), npc);

        // Resumen del inventario (se lee del servidor integrado si esta disponible)
        EntityNPC src = ChatHandler.resolveServerNpc(npc);
        if (src == null) src = npc;
        int items = 0;
        for (int i = 0; i < EntityNPC.INV_SIZE; i++) {
            if (!src.getStackInSlot(i).isEmpty()) items++;
        }
        fontRenderer.drawString("\u00a77Inventario: " + items + " / " + EntityNPC.INV_SIZE, bx, top + 134, 0xAAAAAA);

        fontRenderer.drawString("\u00a77Mensaje:", left, top + 138, 0xAAAAAA);
        field.drawTextBox();
        drawCenteredString(fontRenderer, "\u00a78Enter: enviar", width / 2, top + 176, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
