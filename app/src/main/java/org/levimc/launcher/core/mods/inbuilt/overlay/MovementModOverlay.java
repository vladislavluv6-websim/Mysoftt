package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;

import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;

/**
 * Общая логика оверлеев движения (Fly / Jesus / Scaffold / AutoJump):
 * кнопка включает соответствующую фичу нативной библиотеки movementmod,
 * конфиг прокидывается в рантайм, выключение гасит только свою фичу.
 */
public abstract class MovementModOverlay extends BaseOverlayButton {
    protected boolean isActive = false;

    protected MovementModOverlay(Activity activity) {
        super(activity);
    }

    protected abstract boolean enableNative(InbuiltModManager manager);

    protected abstract void disableNative();

    protected abstract void applyNativeSettings(InbuiltModManager manager);

    @Override
    protected int getIconResource() {
        return 0;
    }

    @Override
    public void show(int startX, int startY) {
        if (!isActive && InbuiltModManager.getInstance(activity)
                .resolveInbuiltModEnabled(getModId(), false)) {
            InbuiltModManager manager = InbuiltModManager.getInstance(activity);
            isActive = enableNative(manager);
            if (isActive) {
                applyNativeSettings(manager);
            }
        }
        super.show(startX, startY);
    }

    @Override
    protected void onButtonClick() {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        boolean nextState = !isActive;
        if (nextState) {
            if (!enableNative(manager)) {
                nextState = false;
            } else {
                applyNativeSettings(manager);
            }
        } else {
            disableNative();
        }
        isActive = nextState;
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
            disableNative();
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
}
