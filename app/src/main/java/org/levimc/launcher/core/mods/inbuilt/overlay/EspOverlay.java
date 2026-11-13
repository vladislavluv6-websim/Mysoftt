package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.ModManager;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;
import org.levimc.launcher.core.mods.inbuilt.nativemod.EspMod;
import org.levimc.launcher.core.versions.GameVersion;

public class EspOverlay extends BaseOverlayButton {
    private static final String TAG = "EspOverlay";
    private static final String SUPPORTED_MINECRAFT_VERSION = "1.26.60";
    private static boolean versionLogged = false;
    private boolean isActive = false;
    private boolean wantActive = false;
    private boolean initPending = false;
    private EspBoxesOverlay boxesOverlay;

    public EspOverlay(Activity activity) {
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

    private void applyNativeSettings(InbuiltModManager manager) {
        EspMod.setRange(manager.getEspRange());
        EspMod.setPlayers(manager.isEspShowPlayers());
        EspMod.setMobs(manager.isEspShowMobs());
        EspMod.setPlayerColor(manager.getEspPlayerColor());
        EspMod.setMobColor(manager.getEspMobColor());
        EspMod.setArmorColors(manager.isEspArmorColors());
        EspMod.setArmoredColor(manager.getEspArmoredColor());
        EspMod.setEspBoxStyle(manager.getEspBoxStyle());
        EspMod.setFriendNames(manager.isFriendModuleEnabled()
                ? manager.getFriendNames() : "");
    }

    // Native init сканирует libminecraftpe.so несколько секунд, поэтому
    // идёт в фоне; включаем модуль, когда он готов и всё ещё нужен.
    private void requestActivate(boolean fromClick) {
        wantActive = true;
        if (initPending) return;
        initPending = true;
        EspMod.initializeAsync(ok -> {
            initPending = false;
            if (!wantActive || activity.isFinishing() || activity.isDestroyed()) return;
            if (ok && EspMod.setEnabled(true)) {
                applyNativeSettings(InbuiltModManager.getInstance(activity));
                isActive = true;
                syncBoxOverlay();
            } else {
                isActive = false;
                wantActive = false;
                if (fromClick) {
                    Toast.makeText(activity, R.string.esp_mod_unavailable, Toast.LENGTH_SHORT).show();
                }
            }
            updateButtonState(isActive);
        });
    }

    @Override
    protected String getModId() {
        return ModIds.ESP;
    }

    @Override
    protected int getIconResource() {
        return isActive ? R.drawable.ic_esp_pressed : R.drawable.ic_esp_normal;
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
                Toast.makeText(activity, R.string.esp_mod_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }
            requestActivate(true);
            return;
        }
        wantActive = false;
        EspMod.setEnabled(false);
        isActive = false;
        updateButtonState(false);
        if (boxesOverlay != null) {
            boxesOverlay.hide();
            boxesOverlay = null;
        }
    }

    private void updateButtonState(boolean active) {
        if (overlayView != null) {
            ImageButton btn = overlayView.findViewById(R.id.mod_overlay_button);
            if (btn != null) {
                btn.setAlpha(getButtonOpacity());
                btn.setImageResource(active ? R.drawable.ic_esp_pressed : R.drawable.ic_esp_normal);
            }
        }
    }

    private void syncBoxOverlay() {
        int style = InbuiltModManager.getInstance(activity).getEspBoxStyle();
        if (isActive && style > 0) {
            if (boxesOverlay == null) {
                boxesOverlay = new EspBoxesOverlay(activity);
                boxesOverlay.show();
            }
            boxesOverlay.setModuleEnabled(true);
            boxesOverlay.applyConfigurationChanges();
        } else if (boxesOverlay != null) {
            boxesOverlay.hide();
            boxesOverlay = null;
        }
    }

    @Override
    public void applyConfigurationChanges() {
        super.applyConfigurationChanges();
        if (isActive) {
            applyNativeSettings(InbuiltModManager.getInstance(activity));
            syncBoxOverlay();
        }
    }

    @Override
    public void hide() {
        wantActive = false;
        if (isActive) {
            EspMod.setEnabled(false);
            isActive = false;
            updateButtonState(false);
        }
        if (boxesOverlay != null) {
            boxesOverlay.hide();
            boxesOverlay = null;
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
            EspMod.setEnabled(false);
            isActive = false;
            updateButtonState(false);
        }
        if (boxesOverlay != null) {
            boxesOverlay.hide();
            boxesOverlay = null;
        }
    }
}
