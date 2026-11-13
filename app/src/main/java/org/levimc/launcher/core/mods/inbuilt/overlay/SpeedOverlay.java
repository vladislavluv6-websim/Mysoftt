package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.ModManager;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;
import org.levimc.launcher.core.mods.inbuilt.nativemod.SpeedMod;
import org.levimc.launcher.core.versions.GameVersion;

public class SpeedOverlay extends BaseOverlayButton {
    private static final String TAG = "SpeedMod";
    private static final String SUPPORTED_MINECRAFT_VERSION = "1.26.60";
    private static boolean versionLogged = false;
    private boolean isActive = false;

    public SpeedOverlay(Activity activity) {
        super(activity);
    }

    private boolean isSupportedVersion() {
        GameVersion version = ModManager.getInstance().getCurrentVersion();
        if (!versionLogged) {
            versionLogged = true;
            Log.i(TAG, "Minecraft version=" + (version == null ? "unknown" : version.versionCode));
        }
        if (version == null || version.versionCode == null || version.versionCode.isEmpty()) {
            return true;
        }
        return version.versionCode.startsWith(SUPPORTED_MINECRAFT_VERSION);
    }

    @Override
    protected String getModId() {
        return ModIds.SPEED;
    }

    @Override
    protected int getIconResource() {
        return isActive ? R.drawable.ic_speed_pressed : R.drawable.ic_speed_normal;
    }

    private void applyNativeSettings(InbuiltModManager manager) {
        SpeedMod.setSpeedEnabled(manager.isSpeedS1Enabled());
        SpeedMod.setSpeedMode(manager.getSpeedMode());
        SpeedMod.setMotionBoost(manager.getSpeedMotionBoost());
        SpeedMod.setMaxSpeed(manager.getSpeedMaxSpeed());
        SpeedMod.setGapMax(manager.getSpeedGapMax());
        SpeedMod.setResetInterval(manager.getSpeedResetInterval());
        SpeedMod.setMaxResetTicks(manager.getSpeedMaxResetTicks());
        SpeedMod.setHopEnabled(manager.isSpeedHopEnabled());
        SpeedMod.setHopHeight(manager.getSpeedHopHeight());
        SpeedMod.setHopInterval(manager.getSpeedHopInterval());
        SpeedMod.setMaskY(manager.isSpeedMaskY());
        SpeedMod.setPhantomHoldTicks(manager.getSpeedPhantomHoldTicks());
        SpeedMod.setFakeJumpTicks(manager.getSpeedFakeJumpTicks());
        SpeedMod.setCancelCorrections(manager.isSpeedCancelCorrections());
        SpeedMod.setPauseOnFall(manager.isSpeedPauseOnFall());
        SpeedMod.setPauseIfSneaking(manager.isSpeedPauseIfSneaking());
        SpeedMod.setPauseIfNotMoving(manager.isSpeedPauseIfNotMoving());
        SpeedMod.setAutoSprint(manager.isSpeedAutoSprint());
        SpeedMod.setDebug(manager.isSpeedDebug());
        SpeedMod.setMultiplier(manager.getSpeedMultiplier() / 100f);
    }

    @Override
    public void show(int startX, int startY) {
        if (!isActive && isSupportedVersion()
                && InbuiltModManager.getInstance(activity).resolveInbuiltModEnabled(getModId(), false)) {
            InbuiltModManager manager = InbuiltModManager.getInstance(activity);
            float multiplier = manager.getSpeedMultiplier() / 100f;
            isActive = SpeedMod.setEnabled(true, multiplier);
            if (isActive) {
                applyNativeSettings(manager);
            }
        }
        super.show(startX, startY);
    }

    @Override
    protected void onButtonClick() {
        boolean nextState = !isActive;
        if (nextState) {
            if (!isSupportedVersion()) {
                Toast.makeText(activity, R.string.speed_mod_unavailable, Toast.LENGTH_SHORT).show();
                nextState = false;
            } else {
                InbuiltModManager manager = InbuiltModManager.getInstance(activity);
                float multiplier = manager.getSpeedMultiplier() / 100f;
                if (!SpeedMod.setEnabled(true, multiplier)) {
                    Toast.makeText(activity, R.string.speed_mod_unavailable, Toast.LENGTH_SHORT).show();
                    nextState = false;
                } else {
                    applyNativeSettings(manager);
                }
            }
        } else {
            SpeedMod.setEnabled(false, 1.0f);
        }
        isActive = nextState;
        updateButtonState(isActive);
    }

    private void updateButtonState(boolean active) {
        if (overlayView != null) {
            ImageButton btn = overlayView.findViewById(R.id.mod_overlay_button);
            if (btn != null) {
                btn.setAlpha(getButtonOpacity());
                btn.setImageResource(active ? R.drawable.ic_speed_pressed : R.drawable.ic_speed_normal);
            }
        }
    }

    @Override
    public void applyConfigurationChanges() {
        super.applyConfigurationChanges();
        if (isActive) {
            applyNativeSettings(InbuiltModManager.getInstance(activity));
        }
    }

    @Override
    public void hide() {
        if (isActive) {
            SpeedMod.setEnabled(false, 1.0f);
            isActive = false;
            updateButtonState(false);
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
}
