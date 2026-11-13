package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;

import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;
import org.levimc.launcher.core.mods.inbuilt.nativemod.MovementMod;

public class AutoJumpOverlay extends MovementModOverlay {
    public AutoJumpOverlay(Activity activity) {
        super(activity);
    }

    @Override
    protected String getModId() {
        return ModIds.AUTO_JUMP;
    }

    @Override
    protected boolean enableNative(InbuiltModManager manager) {
        if (!MovementMod.setEnabled(true)) return false;
        MovementMod.setAutoJumpEnabled(true);
        return true;
    }

    @Override
    protected void disableNative() {
        MovementMod.setAutoJumpEnabled(false);
    }

    @Override
    protected void applyNativeSettings(InbuiltModManager manager) {
        MovementMod.setAutoJumpHeight(manager.getAutoJumpHeight());
        MovementMod.setAutoJumpMode(manager.getAutoJumpMode());
        MovementMod.setDebug(manager.isMovementDebug());
    }
}
