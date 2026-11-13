package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;

import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;

/**
 * Compact named shortcut for modules whose on-screen output is not itself a
 * button (Name Tags, Target ESP). Tapping toggles the module; the shortcut
 * stays visible while the module is off.
 */
public class ModuleToggleShortcut extends BaseOverlayButton {
    private final String modId;
    private boolean active;

    public ModuleToggleShortcut(Activity activity, String modId) {
        super(activity);
        this.modId = modId;
        this.active = InbuiltModManager.getInstance(activity)
                .resolveInbuiltModEnabled(modId, false);
    }

    @Override
    protected String getModId() {
        return modId;
    }

    @Override
    protected int getIconResource() {
        return 0;
    }

    @Override
    protected boolean isShortcutActive() {
        return active;
    }

    @Override
    protected long getShowDelayMillis() {
        return 300L;
    }

    @Override
    public void tick() {
        boolean next = InbuiltModManager.getInstance(activity)
                .resolveInbuiltModEnabled(modId, false);
        if (next != active) {
            active = next;
        }
        super.tick();
    }

    @Override
    protected void onButtonClick() {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        boolean next = !manager.resolveInbuiltModEnabled(modId, false);
        manager.setInbuiltModEnabled(modId, next);
        InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
        if (overlayManager != null) {
            overlayManager.handleModToggle(modId, next);
        }
        active = next;
    }
}
