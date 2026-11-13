package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
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
import org.levimc.launcher.preloader.PreloaderInput;

import java.util.Random;

public class TriggerBotOverlay extends BaseOverlayButton {
    private static final String TAG = "TriggerBot";
    private static final String SUPPORTED_MINECRAFT_VERSION = "1.26.60";
    private static boolean versionLogged = false;

    private final Handler triggerHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private boolean isActive = false;
    private boolean wantActive = false;
    private boolean initPending = false;
    private boolean nativeTriggerEnabled = false;
    private long targetSince = 0L;
    private long nextAttackAt = 0L;
    private long lastGateLogAt = 0L;

    // Поведение атаки (берётся из настроек модуля и кешируется).
    private int reactionDelayMs = 280;
    private int reactionJitterMs = 70;
    private int attackIntervalMs = 620;
    private int attackIntervalJitterMs = 170;
    private int skipChancePercent = 6;
    private boolean humanizationEnabled = true;
    private boolean onlyCrit = false;
    private float triggerRange = 3.0f;
    private float triggerTolerance = 0.03f;

    private final Runnable triggerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isActive) {
                return;
            }
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
            // isHudScreenOpen() == идёт обычный геймплей (HUD-экран открыт).
            // Бьём только в геймплее и никогда — в меню/паузе/инвентаре.
            boolean hudOpen = PreloaderInput.isHudScreenOpen();
            boolean pauseOpen = PreloaderInput.isPauseMenuOpen();
            boolean menuOpen = PreloaderInput.isShowingMenu();
            boolean modMenu = overlayManager != null && overlayManager.isModMenuShowing();
            boolean gateOpen = hudOpen && !pauseOpen && !menuOpen && !modMenu;

            // Удар выполняет нативный GameMode::attack на игровом потоке
            // (AimAssist::updateTrigger) — без тапов, инъекции касаний и без
            // чего-либо видимого на экране. Java-цикл лишь разрешает/запрещает
            // нативный триггер по состоянию экрана, чтобы не бить сквозь меню,
            // паузу и меню клиента.
            if (gateOpen != nativeTriggerEnabled) {
                nativeTriggerEnabled = gateOpen;
                AimAssistMod.setTriggerBot(gateOpen);
            }
            triggerHandler.postDelayed(this, gateOpen ? 100L : 300L);
        }
    };

    public TriggerBotOverlay(Activity activity) {
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
        return ModIds.TRIGGER_BOT;
    }

    @Override
    protected int getIconResource() {
        return isActive ? R.drawable.ic_trigger_bot_pressed : R.drawable.ic_trigger_bot_normal;
    }

    private void applyNativeSettings(InbuiltModManager manager) {
        triggerRange = manager.getTriggerBotRange();
        triggerTolerance = manager.getTriggerBotTolerance();
        reactionDelayMs = manager.getTriggerBotDelay();
        reactionJitterMs = manager.getTriggerBotReactionJitter();
        attackIntervalMs = manager.getTriggerBotInterval();
        attackIntervalJitterMs = manager.getTriggerBotIntervalJitter();
        skipChancePercent = manager.getTriggerBotSkipChance();
        humanizationEnabled = manager.isTriggerBotHumanization();
        onlyCrit = manager.isTriggerBotOnlyCrit();

        // Настройки самого триггера (не трогаем параметры aim assist, чтобы
        // модули не перезаписывали друг друга).
        AimAssistMod.setTriggerDelay(reactionDelayMs);
        AimAssistMod.setTriggerInterval(attackIntervalMs);
        AimAssistMod.setTriggerRange(triggerRange);
        AimAssistMod.setTriggerTolerance(triggerTolerance);
        AimAssistMod.setTargetPlayers(manager.isTriggerBotTargetPlayers());
        AimAssistMod.setTargetMobs(manager.isTriggerBotTargetMobs());
        AimAssistMod.setOnlyCrit(onlyCrit);
        AimAssistMod.setTargetLock(manager.isAimAssistTargetLock());
        AimAssistMod.setAttackMissChance(skipChancePercent / 100.0f);
        AimAssistMod.setAntiBot(manager.isTriggerBotAntiBot());
        AimAssistMod.setRandomAimPoint(manager.isTriggerBotRandomPoint());
        AimAssistMod.setTriggerBot(true);
        EspMod.setFriendNames(manager.isFriendModuleEnabled()
                ? manager.getFriendNames() : "");
        nativeTriggerEnabled = true;
    }

    // Native init идёт в фоне (скан сигнатур занимает секунды).
    private void requestActivate(boolean fromClick) {
        wantActive = true;
        if (initPending) return;
        initPending = true;
        // Удар идёт через нативный GameMode::attack, экранная кнопка больше
        // не нужна — позицию из options.txt не читаем.
        AimAssistMod.initializeAsync(ok -> {
            initPending = false;
            if (!wantActive || activity.isFinishing() || activity.isDestroyed()) return;
            if (ok) {
                applyNativeSettings(InbuiltModManager.getInstance(activity));
                isActive = true;
                startLoop();
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
        stopLoop();
        AimAssistMod.setTriggerBot(false);
        nativeTriggerEnabled = false;
        isActive = false;
        updateButtonState(false);
    }

    private void startLoop() {
        // Удар делает нативный GameMode::attack на игровом потоке (без тапов
        // и без видимых нажатий). Java-цикл только разрешает/запрещает его по
        // состоянию экрана (геймплей vs меню/пауза).
        triggerHandler.removeCallbacks(triggerRunnable);
        targetSince = 0L;
        nextAttackAt = 0L;
        triggerHandler.post(triggerRunnable);
    }

    private void stopLoop() {
        triggerHandler.removeCallbacks(triggerRunnable);
        targetSince = 0L;
        nextAttackAt = 0L;
    }

    private void updateButtonState(boolean active) {
        if (overlayView != null) {
            ImageButton btn = overlayView.findViewById(R.id.mod_overlay_button);
            if (btn != null) {
                btn.setAlpha(getButtonOpacity());
                btn.setImageResource(active ? R.drawable.ic_trigger_bot_pressed : R.drawable.ic_trigger_bot_normal);
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
        stopLoop();
        if (isActive) {
            AimAssistMod.setTriggerBot(false);
            nativeTriggerEnabled = false;
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
        stopLoop();
        if (isActive) {
            AimAssistMod.setTriggerBot(false);
            nativeTriggerEnabled = false;
            isActive = false;
            updateButtonState(false);
        }
    }
}
