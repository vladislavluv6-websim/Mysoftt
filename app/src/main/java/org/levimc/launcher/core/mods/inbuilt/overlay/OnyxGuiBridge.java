package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;

import org.levimc.launcher.core.mods.inbuilt.UnifiedMod;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;

public class OnyxGuiBridge {
    private static final String TAG = "OnyxGuiBridge";

    private final Activity activity;
    private final OnyxMenuOverlay overlay;

    public OnyxGuiBridge(Activity activity, OnyxMenuOverlay overlay) {
        this.activity = activity;
        this.overlay = overlay;
    }

    @JavascriptInterface
    public void setEnabled(String moduleKey, boolean enabled) {
        post(() -> {
            UnifiedMod mod = overlay.findModule(moduleKey);
            if (mod != null) mod.applyEnabled(enabled);
        });
    }

    @JavascriptInterface
    public void setConfig(String moduleKey, String configKey, String value) {
        post(() -> {
            UnifiedMod mod = overlay.findModule(moduleKey);
            if (mod != null) mod.updateConfig(configKey, value == null ? "" : value);
        });
    }

    @JavascriptInterface
    public void setShortcut(String moduleKey, boolean visible) {
        post(() -> {
            UnifiedMod mod = overlay.findModule(moduleKey);
            if (mod == null) return;
            if (mod.getSource() != UnifiedMod.Source.INBUILT) return;
            InbuiltModManager manager = InbuiltModManager.getInstance(activity);
            manager.setOverlayShortcutVisible(mod.getId(), visible);
            InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
            if (overlayManager != null) {
                overlayManager.syncShortcutVisibility(mod.getId());
            }
        });
    }

    @JavascriptInterface
    public void captureKeybind(String moduleKey, String configKey) {
        post(() -> showKeybindDialog(moduleKey, configKey));
    }

    @JavascriptInterface
    public void hideMenu() {
        post(overlay::hide);
    }

    @JavascriptInterface
    public void exportLogs() {
        post(overlay::exportLogs);
    }

    private void post(Runnable action) {
        try {
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                try {
                    action.run();
                } catch (Throwable error) {
                    Log.e(TAG, "bridge action failed", error);
                }
            });
        } catch (Throwable error) {
            Log.e(TAG, "bridge post failed", error);
        }
    }

    private void showKeybindDialog(String moduleKey, String configKey) {
        UnifiedMod mod = overlay.findModule(moduleKey);
        if (mod == null) return;
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Keybind")
                .setMessage("Press a key")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", (d, which) -> {
                    mod.updateConfig(configKey, String.valueOf(KeyEvent.KEYCODE_UNKNOWN));
                    overlay.updateKeybindLabel(moduleKey, configKey, "None");
                })
                .create();
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                dialog.dismiss();
                return true;
            }
            if (!event.hasNoModifiers() && !KeyEvent.isModifierKey(keyCode)) return false;
            mod.updateConfig(configKey, String.valueOf(keyCode));
            overlay.updateKeybindLabel(moduleKey, configKey, keyLabel(keyCode));
            dialog.dismiss();
            return true;
        });
        dialog.show();
    }

    private static String keyLabel(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return "None";
        String name = KeyEvent.keyCodeToString(keyCode);
        if (name == null) return "None";
        String label = name.replace("KEYCODE_", "").replace('_', ' ');
        if (label.isEmpty()) return "None";
        return label;
    }
}
