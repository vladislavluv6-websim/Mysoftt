package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;

import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;
import org.levimc.launcher.core.mods.inbuilt.nativemod.MovementMod;

public class ScaffoldOverlay extends MovementModOverlay {
    public ScaffoldOverlay(Activity activity) {
        super(activity);
    }

    @Override
    protected String getModId() {
        return ModIds.SCAFFOLD;
    }

    @Override
    protected boolean enableNative(InbuiltModManager manager) {
        if (!MovementMod.setEnabled(true)) return false;
        MovementMod.setScaffoldEnabled(true);
        return true;
    }

    @Override
    protected void disableNative() {
        MovementMod.setScaffoldEnabled(false);
    }

    @Override
    protected void applyNativeSettings(InbuiltModManager manager) {
        MovementMod.setScaffoldMode(manager.getScaffoldMode());
        MovementMod.setScaffoldPitch(manager.getScaffoldPitch());
        MovementMod.setScaffoldInterval(manager.getScaffoldInterval());
        MovementMod.setScaffoldRotate(manager.isScaffoldRotate());
        MovementMod.setDebug(manager.isMovementDebug());
    }
}
