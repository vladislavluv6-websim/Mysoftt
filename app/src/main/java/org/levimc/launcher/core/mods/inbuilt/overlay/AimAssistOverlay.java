package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.ModManager;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;
import org.levimc.launcher.core.mods.inbuilt.nativemod.AimAssistMod;
import org.levimc.launcher.core.mods.inbuilt.nativemod.EspMod;
import org.levimc.launcher.core.versions.GameVersion;


public class AimAssistOverlay extends BaseOverlayButton {
    private static final String TAG = "AimAssist";
    private static final String SUPPORTED_MINECRAFT_VERSION = "1.26.60";
    private static boolean versionLogged = false;
    private boolean isActive = false;
    private boolean wantActive = false;
    private boolean initPending = false;

    public AimAssistOverlay(Activity activity) {
        super(activity);
    }

    private void applyNativeSettings(InbuiltModManager manager) {
        AimAssistMod.setLegitMode(manager.isAimAssistLegitMode());
        AimAssistMod.setRange(manager.getAimAssistRange());
        AimAssistMod.setFov(manager.getAimAssistFov());
        AimAssistMod.setStrength(manager.getAimAssistStrength());
        AimAssistMod.setWallCheck(manager.isAimAssistWallCheck());
        AimAssistMod.setIgnoreInvisible(manager.isAimAssistIgnoreInvisible());
        AimAssistMod.setKillaura(manager.isAimAssistKillaura());
        AimAssistMod.setTargetPlayers(manager.isAimAssistTargetPlayers());
        AimAssistMod.setTargetMobs(manager.isAimAssistTargetMobs());
        AimAssistMod.setMaxSpeed(manager.getAimAssistMaxSpeed());
        AimAssistMod.setSmoothing(manager.getAimAssistSmoothing());
        AimAssistMod.setAcceleration(manager.getAimAssistAcceleration());
        AimAssistMod.setDeadzone(manager.getAimAssistDeadzone() / 100.0f);
        AimAssistMod.setFullSpeedRange(manager.getAimAssistFullRange());
        AimAssistMod.setStickyTicks(manager.getAimAssistSticky());
        AimAssistMod.setTargetGrace(manager.getAimAssistGrace());
        AimAssistMod.setPreferClosest(manager.isAimAssistPreferClosest());
        AimAssistMod.setTargetLock(manager.isAimAssistTargetLock());
        AimAssistMod.setFastAim(manager.isAimAssistFastAim());
        AimAssistMod.setFastAimTime(manager.getAimAssistFastAimTime());
        AimAssistMod.setFastAimMaxSpeed(manager.getAimAssistFastAimMaxSpeed());
        AimAssistMod.setAntiBot(manager.isAimAssistAntiBot());
        AimAssistMod.setRandomAimPoint(manager.isAimAssistRandomPoint());
        EspMod.setFriendNames(manager.isFriendModuleEnabled()
                ? manager.getFriendNames() : "");
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
        return ModIds.AIM_ASSIST;
    }

    @Override
    protected int getIconResource() {
        return isActive ? R.drawable.ic_aimassist_pressed : R.drawable.ic_aimassist_normal;
    }

    // Native init сканирует libminecraftpe.so несколько секунд, поэтому
    // идёт в фоне; включаем модуль, когда он готов и всё ещё нужен.
    private void requestActivate(boolean fromClick) {
        wantActive = true;
        if (initPending) return;
        initPending = true;
        AimAssistMod.initializeAsync(ok -> {
            initPending = false;
            if (!wantActive || activity.isFinishing() || activity.isDestroyed()) return;
            InbuiltModManager manager = InbuiltModManager.getInstance(activity);
            if (ok && AimAssistMod.setEnabled(true,
                    manager.getAimAssistRange(), manager.getAimAssistStrength())) {
                applyNativeSettings(manager);
                isActive = true;
            } else {
                isActive = false;
                wantActive = false;
                if (fromClick) {
                    Toast.makeText(activity, R.string.aim_assist_mod_unavailable, Toast.LENGTH_SHORT).show();
                }
            }
            updateButtonState(isActive);
        });
    }

    @Override
    public void show(int startX, int startY) {
        if (!isActive && isSupportedVersion()
                && InbuiltModManager.getInstance(activity).resolveInbuiltModEnabled(getModId(), false)) {
            requestActivate(false);
        }
        super.show(startX, startY);
    }

    @Override
    protected void onButtonClick() {
        if (!isActive) {
            if (!isSupportedVersion()) {
                Toast.makeText(activity, R.string.aim_assist_mod_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }
            requestActivate(true);
            return;
        }
        wantActive = false;
        AimAssistMod.setEnabled(false, 8.0f, 100.0f);
        isActive = false;
        updateButtonState(false);
    }

    private void updateButtonState(boolean active) {
        if (overlayView != null) {
            ImageButton btn = overlayView.findViewById(R.id.mod_overlay_button);
            if (btn != null) {
                btn.setAlpha(getButtonOpacity());
                btn.setImageResource(active ? R.drawable.ic_aimassist_pressed : R.drawable.ic_aimassist_normal);
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
        wantActive = false;
        if (isActive) {
            AimAssistMod.setEnabled(false, 8.0f, 100.0f);
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

    @Override
    public void onModuleConfigDisabled() {
        wantActive = false;
        if (isActive) {
            AimAssistMod.setEnabled(false, 8.0f, 100.0f);
            isActive = false;
            updateButtonState(false);
        }
    }
}
