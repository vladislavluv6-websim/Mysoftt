package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.ModManager;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;
import org.levimc.launcher.core.mods.inbuilt.nativemod.FullbrightMod;
import org.levimc.launcher.core.versions.GameVersion;

public class FullbrightOverlay extends BaseOverlayButton {
    private static final String TAG = "Fullbright";
    private static final String SUPPORTED_MINECRAFT_VERSION = "1.26.60";
    private static boolean versionLogged = false;
    private boolean isActive = false;
    private boolean wantActive = false;
    private boolean initPending = false;

    public FullbrightOverlay(Activity activity) {
        super(activity);
    }

    private boolean isSupportedVersion() {
        GameVersion version = ModManager.getInstance().getCurrentVersion();
        if (!versionLogged) {
            versionLogged = true;
            Log.i(TAG, "Minecraft version="
                    + (version == null ? "unknown" : version.versionCode));
        }
        if (version == null || version.versionCode == null
                || version.versionCode.isEmpty()) {
            return true;
        }
        return version.versionCode.startsWith(SUPPORTED_MINECRAFT_VERSION);
    }

    private void requestActivate(boolean fromClick) {
        wantActive = true;
        if (initPending) return;
        initPending = true;
        FullbrightMod.initializeAsync(ok -> {
            initPending = false;
            if (!wantActive || activity.isFinishing() || activity.isDestroyed()) return;
            if (ok && FullbrightMod.setEnabled(true)) {
                isActive = true;
            } else {
                isActive = false;
                wantActive = false;
                if (fromClick) {
                    Toast.makeText(activity, R.string.fullbright_mod_unavailable,
                            Toast.LENGTH_SHORT).show();
                }
            }
            updateButtonState(isActive);
        });
    }

    @Override
    protected String getModId() {
        return ModIds.FULLBRIGHT;
    }

    @Override
    protected int getIconResource() {
        return isActive ? R.drawable.ic_fullbright_pressed
                : R.drawable.ic_fullbright_normal;
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
                Toast.makeText(activity, R.string.fullbright_mod_unavailable,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            requestActivate(true);
            return;
        }
        wantActive = false;
        FullbrightMod.setEnabled(false);
        isActive = false;
        updateButtonState(false);
    }

    private void updateButtonState(boolean active) {
        if (overlayView != null) {
            ImageButton btn = overlayView.findViewById(R.id.mod_overlay_button);
            if (btn != null) {
                btn.setAlpha(getButtonOpacity());
                btn.setImageResource(active ? R.drawable.ic_fullbright_pressed
                        : R.drawable.ic_fullbright_normal);
            }
        }
    }

    @Override
    public void hide() {
        wantActive = false;
        if (isActive) {
            FullbrightMod.setEnabled(false);
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
            FullbrightMod.setEnabled(false);
            isActive = false;
            updateButtonState(false);
        }
    }
}
