package com.npcgemini.client.gui;

import com.npcgemini.client.ChatHandler;
import com.npcgemini.entity.EntityNPC;
import com.npcgemini.profile.BotProfile;
import com.npcgemini.server.NpcActions;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Menú central del NPC (clic derecho): estado, órdenes rápidas, inventario, personalidad y chat.
 * Las órdenes pasan por el mismo intérprete que el chat y se ejecutan en el servidor.
 */
public class GuiNpcInteract extends GuiScreen {

    private static final int W = 300;
    private static final int H = 232;

    private final EntityNPC npc;
    private GuiTextField field;
    private GuiButton btnChatMode, btnCombat;

    public GuiNpcInteract(EntityNPC npc) { this.npc = npc; }

    private BotProfile profile() { return npc.getProfile(); }

    private boolean isChatMode() {
        String m = ChatHandler.chatModeBot;
        return m != null && m.equalsIgnoreCase(profile().id);
    }

    private String chatModeLabel() { return "Modo chat: " + (isChatMode() ? "\u00a7aON" : "\u00a7cOFF"); }

    private EntityNPC server() {
        EntityNPC s = ChatHandler.resolveServerNpc(npc);
        return s != null ? s : npc;
    }

    private String combatLabel() { return "Combate: " + (server().isCombatEnabled() ? "\u00a7aON" : "\u00a7cOFF"); }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int left = width / 2 - W / 2;
        int top = height / 2 - H / 2;
        int bx = left + 120;
        int bw = 86;
        int bw2 = W - 120 - bw - 8;
        int x2 = bx + bw + 4;

        buttonList.add(new GuiButton(1, bx, top + 22, bw, 20, "Inventario"));
        buttonList.add(new GuiButton(2, x2, top + 22, bw2 + 4, 20, "Personalidad"));
        buttonList.add(new GuiButton(10, bx, top + 46, bw, 20, "S\u00edgueme"));
        buttonList.add(new GuiButton(11, x2, top + 46, bw2 + 4, 20, "Ven aqu\u00ed"));
        buttonList.add(new GuiButton(12, bx, top + 70, bw, 20, "Detente"));
        btnCombat = new GuiButton(5, x2, top + 70, bw2 + 4, 20, combatLabel());
        buttonList.add(btnCombat);
        buttonList.add(new GuiButton(13, bx, top + 94, bw, 20, "Talar un \u00e1rbol"));
        buttonList.add(new GuiButton(14, x2, top + 94, bw2 + 4, 20, "Recoger objetos"));
        buttonList.add(new GuiButton(15, bx, top + 118, bw, 20, "Dame todo"));
        btnChatMode = new GuiButton(3, x2, top + 118, bw2 + 4, 20, chatModeLabel());
        buttonList.add(btnChatMode);
        buttonList.add(new GuiButton(0, left + W - 60, top + H - 26, 60, 20, "Cerrar"));
        buttonList.add(new GuiButton(4, left + W - 60 - 64, top + H - 52, 60, 20, "Enviar"));

        String keep = field != null ? field.getText() : "";
        field = new GuiTextField(10, fontRenderer, left, top + H - 51, W - 130, 18);
        field.setMaxStringLength(200);
        field.setText(keep);
        field.setFocused(true);
    }

    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
    @Override public void updateScreen() { field.updateCursorCounter(); }

    private void order(String text) {
        mc.displayGuiScreen(null);
        ChatHandler.sendToBot(profile().id, text, false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0: mc.displayGuiScreen(null); break;
            case 1: NpcActions.openInventory(npc); break;
            case 2: mc.displayGuiScreen(new GuiNpcMenu(npc, this)); break;
            case 3: {
                BotProfile p = profile();
                if (isChatMode()) {
                    ChatHandler.chatModeBot = null;
                    if (mc.player != null) mc.player.sendMessage(new TextComponentString("\u00a77Modo chat desactivado."));
                } else {
                    ChatHandler.chatModeBot = p.id;
                    if (mc.player != null) mc.player.sendMessage(new TextComponentString(
                            "\u00a7aHablando con \u00a7d" + p.name + "\u00a7a. Todo lo que escribas en el chat ir\u00e1 al bot."));
                }
                btnChatMode.displayString = chatModeLabel();
                break;
            }
            case 4: send(); break;
            case 5: {
                boolean on = server().isCombatEnabled();
                order(on ? "no pelees" : "pelea");
                break;
            }
            case 10: order("s\u00edgueme"); break;
            case 11: order("ven aqu\u00ed"); break;
            case 12: order("detente"); break;
            case 13: order("tala un \u00e1rbol"); break;
            case 14: order("recoge los objetos"); break;
            case 15: order("dame todo"); break;
            default: break;
        }
    }

    private void send() {
        String text = field.getText().trim();
        if (text.isEmpty()) return;
        String id = profile().id;
        mc.displayGuiScreen(null);   // la respuesta sale en el chat y la captura no debe incluir el menú
        ChatHandler.sendToBot(id, text, true);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) { send(); return; }
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

        drawRect(left - 8, top - 8, left + W + 8, top + H + 8, 0xF0101018);
        drawRect(left - 7, top - 7, left + W + 7, top + H + 7, 0xF01C1C26);
        drawRect(left, top + 18, left + 112, top + 142, 0xFF0B0B10);

        BotProfile p = profile();
        drawCenteredString(fontRenderer, "\u00a7d" + p.name, width / 2, top, 0xFFFFFF);

        int pvx = left + 56;
        GuiInventory.drawEntityOnScreen(pvx, top + 118, 42, (float) (pvx - mouseX), (float) (top + 70 - mouseY), npc);

        EntityNPC s = server();
        String job = s.getJobDescription();
        fontRenderer.drawString("\u00a77Vida: " + (int) s.getHealth() + "/" + (int) s.getMaxHealth()
                + "   Espacios: " + s.getUsedSlots() + "/" + EntityNPC.INV_SIZE, left, top + 146, 0xAAAAAA);
        fontRenderer.drawString("\u00a77Tarea: \u00a7f" + (job == null ? "ninguna" : job), left, top + 157, 0xAAAAAA);
        btnCombat.displayString = combatLabel();

        fontRenderer.drawString("\u00a77Mensaje o orden (ej. \u00abpica hierro\u00bb, \u00abdame 5 palos\u00bb):", left, top + H - 62, 0xAAAAAA);
        field.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
