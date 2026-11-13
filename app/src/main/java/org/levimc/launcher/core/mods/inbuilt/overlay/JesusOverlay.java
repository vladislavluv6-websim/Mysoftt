package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;

import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;
import org.levimc.launcher.core.mods.inbuilt.nativemod.MovementMod;

public class JesusOverlay extends MovementModOverlay {
    public JesusOverlay(Activity activity) {
        super(activity);
    }

    @Override
    protected String getModId() {
        return ModIds.JESUS;
    }

    @Override
    protected boolean enableNative(InbuiltModManager manager) {
        if (!MovementMod.setEnabled(true)) return false;
        MovementMod.setJesusEnabled(true);
        return true;
    }

    @Override
    protected void disableNative() {
        MovementMod.setJesusEnabled(false);
    }

    @Override
    protected void applyNativeSettings(InbuiltModManager manager) {
        MovementMod.setJesusMode(manager.getJesusMode());
        MovementMod.setJesusDepth(manager.getJesusDepth());
        MovementMod.setDebug(manager.isMovementDebug());
    }
}
