package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;

import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;
import org.levimc.launcher.core.mods.inbuilt.nativemod.MovementMod;

public class FlyOverlay extends MovementModOverlay {
    public FlyOverlay(Activity activity) {
        super(activity);
    }

    @Override
    protected String getModId() {
        return ModIds.FLY;
    }

    @Override
    protected boolean enableNative(InbuiltModManager manager) {
        if (!MovementMod.setEnabled(true)) return false;
        MovementMod.setFlyEnabled(true);
        return true;
    }

    @Override
    protected void disableNative() {
        MovementMod.setFlyEnabled(false);
    }

    @Override
    protected void applyNativeSettings(InbuiltModManager manager) {
        MovementMod.setFlyMode(manager.getFlyMode());
        MovementMod.setFlySpeed(manager.getFlySpeed());
        MovementMod.setFlyUp(manager.getFlyUp());
        MovementMod.setFlyDown(manager.getFlyDown());
        MovementMod.setFlyCancelCorrections(manager.isFlyCancelCorrections());
        MovementMod.setDebug(manager.isMovementDebug());
    }
}
