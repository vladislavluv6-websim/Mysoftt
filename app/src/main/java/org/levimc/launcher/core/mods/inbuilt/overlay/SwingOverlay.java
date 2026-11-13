package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.widget.Toast;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;
import org.levimc.launcher.core.mods.inbuilt.nativemod.AimAssistMod;

/**
 * Hit-swing animation module. The native side only applies the selected curve
 * to a swing armed by a successful TriggerBot attack, so enabling this module
 * never changes normal PvP swings.
 */
public class SwingOverlay extends BaseOverlayButton {
    private boolean isActive = false;
    private boolean wantActive = false;
    private boolean initPending = false;

    public SwingOverlay(Activity activity) {
        super(activity);
    }

    @Override
    protected String getModId() {
        return ModIds.SWING;
    }

    @Override
    protected int getIconResource() {
        return 0;
    }

    private void applyNativeSettings() {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        AimAssistMod.setSwingStyle(manager.getSwingStyle());
        AimAssistMod.setSwingSpeed(manager.getSwingSpeed());
        AimAssistMod.setSwingAmplitude(manager.getSwingAmplitude());
    }

    private void requestActivate(boolean fromClick) {
        wantActive = true;
        if (initPending) return;
        initPending = true;
        AimAssistMod.initializeAsync(ok -> {
            initPending = false;
            if (!wantActive || activity.isFinishing() || activity.isDestroyed()) return;
            if (ok) {
                applyNativeSettings();
                isActive = true;
            } else {
                isActive = false;
                wantActive = false;
                if (fromClick) {
                    Toast.makeText(activity, R.string.aim_assist_mod_unavailable,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void show(int startX, int startY) {
        if (!isActive
                && InbuiltModManager.getInstance(activity)
                .resolveInbuiltModEnabled(getModId(), false)) {
            requestActivate(false);
        }
        super.show(startX, startY);
    }

    @Override
    protected void onButtonClick() {
        if (!isActive) {
            requestActivate(true);
            return;
        }
        wantActive = false;
        AimAssistMod.setSwingStyle(0);
        isActive = false;
    }

    @Override
    public void applyConfigurationChanges() {
        super.applyConfigurationChanges();
        if (isActive) {
            applyNativeSettings();
        }
    }

    @Override
    public void hide() {
        wantActive = false;
        if (isActive) {
            AimAssistMod.setSwingStyle(0);
            isActive = false;
        }
        super.hide();
    }

    @Override
    protected boolean isShortcutActive() {
        return isActive;
    }

    @Override
    protected boolean autoActivateOnConfig() {
        return true;
    }

    @Override
    public void onModuleConfigDisabled() {
        wantActive = false;
        if (isActive) {
            AimAssistMod.setSwingStyle(0);
            isActive = false;
        }
    }
}
