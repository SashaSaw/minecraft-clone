package craft.ui;

import craft.item.ItemStack;
import craft.player.Player;
import craft.render.Tiles;

/** In-game HUD: crosshair, hotbar, hearts, hunger, air, hurt vignette, death screen. */
public class Hud {
    public void render(UI ui, Player p, String debugLine) {
        int cx = ui.screenW / 2;
        int bottom = ui.screenH;

        // crosshair
        ui.rect(cx - 5, ui.screenH / 2f - 1, 10, 2, 1, 1, 1, 0.6f);
        ui.rect(cx - 1, ui.screenH / 2f - 5, 2, 10, 1, 1, 1, 0.6f);

        // hotbar
        int hbX = cx - 9 * 20 / 2;
        int hbY = bottom - 22;
        for (int i = 0; i < 9; i++) {
            float x = hbX + i * 20;
            boolean sel = i == p.inventory.selected;
            ui.rect(x - 2, hbY - 2, 20, 22, 0.1f, 0.1f, 0.1f, 0.75f);
            if (sel) {
                ui.rect(x - 2, hbY - 2, 20, 2, 1, 1, 1, 0.95f);
                ui.rect(x - 2, hbY + 18, 20, 2, 1, 1, 1, 0.95f);
                ui.rect(x - 2, hbY - 2, 2, 22, 1, 1, 1, 0.95f);
                ui.rect(x + 16, hbY - 2, 2, 22, 1, 1, 1, 0.95f);
            }
            ItemStack st = p.inventory.slots[i];
            if (st != null) ui.itemStack(st, x, hbY);
        }

        // hearts (above hotbar, left side)
        int rowY = hbY - 12;
        for (int i = 0; i < 10; i++) {
            int tile;
            int v = p.hp - i * 2;
            if (v >= 2) tile = Tiles.ICON_HEART;
            else if (v == 1) tile = Tiles.ICON_HEART_HALF;
            else tile = Tiles.ICON_HEART_EMPTY;
            ui.tile(tile, hbX - 1 + i * 9, rowY, 9, 9);
        }

        // hunger (right side, fills right-to-left like vanilla)
        for (int i = 0; i < 10; i++) {
            int v = p.hunger - i * 2;
            int tile;
            if (v >= 2) tile = Tiles.ICON_HUNGER;
            else if (v == 1) tile = Tiles.ICON_HUNGER_HALF;
            else tile = Tiles.ICON_HUNGER_EMPTY;
            ui.tile(tile, hbX + 9 * 20 - 8 - i * 9, rowY, 9, 9);
        }

        // air bubbles above hunger when underwater
        if (p.air < 300) {
            int bubbles = (int) Math.ceil(p.air / 30.0);
            for (int i = 0; i < bubbles; i++) {
                ui.tile(Tiles.ICON_BUBBLE, hbX + 9 * 20 - 8 - i * 9, rowY - 10, 9, 9);
            }
        }

        // hurt vignette
        if (p.hurtFlash > 0) {
            ui.rect(0, 0, ui.screenW, ui.screenH, 0.8f, 0.05f, 0.05f, p.hurtFlash / 10f * 0.35f);
        }

        if (debugLine != null) {
            ui.text(debugLine, 3, 3, 1, 1, 1);
        }

        if (p.dead) {
            ui.rect(0, 0, ui.screenW, ui.screenH, 0.45f, 0.02f, 0.02f, 0.55f);
            ui.textCentered("YOU DIED!", cx, ui.screenH / 2f - 20, 1, 1, 1);
            ui.textCentered("CLICK TO RESPAWN", cx, ui.screenH / 2f + 4, 0.9f, 0.9f, 0.9f);
        }
    }
}
