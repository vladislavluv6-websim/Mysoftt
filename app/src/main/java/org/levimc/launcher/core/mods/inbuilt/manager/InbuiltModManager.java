package org.levimc.launcher.core.mods.inbuilt.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import org.levimc.launcher.core.mods.inbuilt.model.ModIds;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class InbuiltModManager {
    private static final String PREFS_NAME = "inbuilt_mods_prefs";
    private static final String KEY_AUTOSPRINT_KEY = "autosprint_key";
    private static final String KEY_SPEED_MULTIPLIER = "speed_multiplier";
    private static final String KEY_SPEED_S1_ENABLED = "speed_s1_enabled";
    private static final String KEY_SPEED_MODE = "speed_mode";
    private static final String KEY_SPEED_MOTION_BOOST = "speed_motion_boost";
    private static final String KEY_SPEED_MAX_SPEED = "speed_max_speed";
    private static final String KEY_SPEED_GAP_MAX = "speed_gap_max";
    private static final String KEY_SPEED_RESET_INTERVAL = "speed_reset_interval";
    private static final String KEY_SPEED_MAX_RESET_TICKS = "speed_max_reset_ticks";
    private static final String KEY_SPEED_HOP_ENABLED = "speed_hop_enabled";
    private static final String KEY_SPEED_HOP_HEIGHT = "speed_hop_height";
    private static final String KEY_SPEED_HOP_HEIGHT_EXACT = "speed_hop_height_exact";
    private static final String KEY_SPEED_HOP_INTERVAL = "speed_hop_interval";
    private static final String KEY_SPEED_MASK_Y = "speed_mask_y";
    private static final String KEY_SPEED_PHANTOM_HOLD = "speed_phantom_hold";
    private static final String KEY_SPEED_FAKE_JUMP_TICKS = "speed_fake_jump_ticks";
    private static final String KEY_SPEED_CANCEL_CORRECTIONS = "speed_cancel_corrections";
    private static final String KEY_SPEED_PAUSE_ON_FALL = "speed_pause_on_fall";
    private static final String KEY_SPEED_PAUSE_IF_SNEAKING = "speed_pause_if_sneaking";
    private static final String KEY_SPEED_PAUSE_IF_NOT_MOVING = "speed_pause_if_not_moving";
    private static final String KEY_SPEED_AUTO_SPRINT = "speed_auto_sprint";
    private static final String KEY_SPEED_DEBUG = "speed_debug";
    private static final String KEY_AIM_ASSIST_RANGE = "aim_assist_range";
    private static final String KEY_AIM_ASSIST_STRENGTH = "aim_assist_strength";
    private static final String KEY_AIM_ASSIST_LEGIT = "aim_assist_legit";
    private static final String KEY_AIM_ASSIST_FOV = "aim_assist_fov";
    private static final String KEY_AIM_ASSIST_WALL_CHECK = "aim_assist_wall_check";
    private static final String KEY_AIM_ASSIST_IGNORE_INVISIBLE = "aim_assist_ignore_invisible";
    private static final String KEY_AIM_ASSIST_NEURAL = "aim_assist_neural";
    private static final String KEY_AIM_ASSIST_KILLAURA = "aim_assist_killaura";
    private static final String KEY_KILLAURA_RANGE = "killaura_range";
    private static final String KEY_KILLAURA_CONE = "killaura_cone";
    private static final String KEY_KILLAURA_REACTION = "killaura_reaction";
    private static final String KEY_AIM_ASSIST_ROT_RECORDING = "aim_assist_rot_recording";
    private static final String KEY_AIM_ASSIST_TARGET_PLAYERS = "aim_assist_target_players";
    private static final String KEY_AIM_ASSIST_TARGET_MOBS = "aim_assist_target_mobs";
    private static final String KEY_AIM_ASSIST_BODY_PART = "aim_assist_body_part";
    private static final String KEY_AIM_ASSIST_MAX_SPEED = "aim_assist_max_speed";
    private static final String KEY_AIM_ASSIST_SMOOTHING = "aim_assist_smoothing";
    private static final String KEY_AIM_ASSIST_ACCEL = "aim_assist_accel";
    private static final String KEY_AIM_ASSIST_DEADZONE = "aim_assist_deadzone";
    private static final String KEY_AIM_ASSIST_FULL_RANGE = "aim_assist_full_range";
    private static final String KEY_AIM_ASSIST_STICKY = "aim_assist_sticky";
    private static final String KEY_AIM_ASSIST_GRACE = "aim_assist_grace";
    private static final String KEY_AIM_ASSIST_PREFER_CLOSEST = "aim_assist_prefer_closest";
    private static final String KEY_AIM_ASSIST_TARGET_LOCK = "aim_assist_target_lock";
    private static final String KEY_AIM_ASSIST_FAST_AIM = "aim_assist_fast_aim";
    private static final String KEY_AIM_ASSIST_FAST_AIM_TIME = "aim_assist_fast_aim_time";
    private static final String KEY_AIM_ASSIST_FAST_AIM_MAX_SPEED = "aim_assist_fast_aim_max_speed";
    private static final String KEY_AIM_ASSIST_ANTI_BOT = "aim_assist_anti_bot";
    private static final String KEY_AIM_ASSIST_RANDOM_POINT = "aim_assist_random_point";
    private static final String KEY_TRIGGER_BOT_ANTI_BOT = "trigger_bot_anti_bot";
    private static final String KEY_TRIGGER_BOT_RANDOM_POINT = "trigger_bot_random_point";
    private static final String KEY_ESP_SHOW_PLAYERS = "esp_show_players";
    private static final String KEY_ESP_SHOW_MOBS = "esp_show_mobs";
    private static final String KEY_ESP_NAME_TAGS = "esp_name_tags";
    private static final String KEY_ESP_RANGE = "esp_range";
    private static final String KEY_ESP_PLAYER_COLOR = "esp_player_color";
    private static final String KEY_ESP_MOB_COLOR = "esp_mob_color";
    private static final String KEY_ESP_ARMOR_COLORS = "esp_armor_colors";
    private static final String KEY_ESP_ARMORED_COLOR = "esp_armored_color";
    private static final String KEY_ESP_BOX_STYLE = "esp_box_style";
    private static final String KEY_TARGET_ESP_STYLE = "target_esp_style";
    private static final String KEY_NAME_TAG_STYLE = "name_tag_style";
    private static final String KEY_NAME_TAG_COLOR = "name_tag_color";
    private static final String KEY_NAME_TAG_SIZE = "name_tag_size";
    private static final String KEY_NAME_TAG_ARMOR_SIZE = "name_tag_armor_size";
    private static final String KEY_NAME_TAG_BLUR = "name_tag_blur";
    private static final String KEY_NAME_TAG_BLUR_COLOR = "name_tag_blur_color";
    private static final String KEY_NAME_TAG_OUTLINE_COLOR = "name_tag_outline_color";
    private static final String KEY_NAME_TAG_SHOW_ARMOR = "name_tag_show_armor";
    private static final String KEY_NAME_TAG_SHOW_OFFHAND = "name_tag_show_offhand";
    private static final String KEY_SWING_STYLE = "swing_style";
    private static final String KEY_SWING_SPEED = "swing_speed";
    private static final String KEY_SWING_AMPLITUDE = "swing_amplitude";
    private static final String KEY_TARGET_ESP_COLOR = "target_esp_color";
    private static final String KEY_TARGET_ESP_HOLD = "target_esp_hold_ms";
    private static final String KEY_TRIGGER_BOT_ENABLED = "trigger_bot_enabled";
    private static final String KEY_TRIGGER_BOT_DELAY = "trigger_bot_delay";
    private static final String KEY_TRIGGER_BOT_REACTION_JITTER = "trigger_bot_reaction_jitter";
    private static final String KEY_TRIGGER_BOT_RANGE = "trigger_bot_range";
    private static final String KEY_TRIGGER_BOT_TOLERANCE = "trigger_bot_tolerance";
    private static final String KEY_TRIGGER_BOT_TARGET_PLAYERS = "trigger_bot_target_players";
    private static final String KEY_TRIGGER_BOT_TARGET_MOBS = "trigger_bot_target_mobs";
    private static final String KEY_TRIGGER_BOT_HUMANIZATION = "trigger_bot_humanization";
    private static final String KEY_TRIGGER_BOT_INTERVAL = "trigger_bot_interval";
    private static final String KEY_TRIGGER_BOT_INTERVAL_JITTER = "trigger_bot_interval_jitter";
    private static final String KEY_TRIGGER_BOT_SKIP_CHANCE = "trigger_bot_skip_chance";
    private static final String KEY_TRIGGER_BOT_ONLY_CRIT = "trigger_bot_only_crit";
    private static final String KEY_FLY_MODE = "fly_mode";
    private static final String KEY_FLY_SPEED = "fly_speed";
    private static final String KEY_FLY_UP = "fly_up";
    private static final String KEY_FLY_DOWN = "fly_down";
    private static final String KEY_FLY_CANCEL_CORRECTIONS = "fly_cancel_corrections";
    private static final String KEY_JESUS_MODE = "jesus_mode";
    private static final String KEY_JESUS_DEPTH = "jesus_depth";
    private static final String KEY_SCAFFOLD_MODE = "scaffold_mode";
    private static final String KEY_SCAFFOLD_PITCH = "scaffold_pitch";
    private static final String KEY_SCAFFOLD_INTERVAL = "scaffold_interval";
    private static final String KEY_SCAFFOLD_ROTATE = "scaffold_rotate";
    private static final String KEY_AUTO_JUMP_HEIGHT = "auto_jump_height";
    private static final String KEY_AUTO_JUMP_MODE = "auto_jump_mode";
    private static final String KEY_MOVEMENT_DEBUG = "movement_debug";
    private static final String KEY_INBUILT_KEYBIND_PREFIX = "inbuilt_keybind_";
    private static final String KEY_OVERLAY_BUTTON_SIZE_PREFIX = "overlay_button_size_";
    private static final String KEY_OVERLAY_OPACITY_PREFIX = "overlay_opacity_";
    private static final String KEY_OVERLAY_SHORTCUT_PREFIX = "overlay_shortcut_";
    private static final String KEY_MOD_MENU_ENABLED = "mod_menu_enabled";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String KEY_MOD_MENU_OPACITY = "mod_menu_opacity";
    private static final String KEY_MOD_MENU_BUTTON_OPACITY = "mod_menu_button_opacity";
    private static final String KEY_MOD_MENU_COMPACT = "mod_menu_compact";
    private static final String KEY_MOD_MENU_KEYBIND = "mod_menu_keybind";
    private static final String KEY_PAUSE_MENU_ONLY = "pause_menu_only";
    private static final String KEY_HIDE_LAUNCHER_HUD = "hide_launcher_hud_with_minecraft";
    private static final String KEY_FAVORITE_MOD_KEYS = "favorite_mod_keys";
    private static final String KEY_INBUILT_MOD_ENABLED_PREFIX = "inbuilt_mod_enabled_";
    private static final String KEY_EXTERNAL_MODULE_ENABLED_PREFIX = "external_module_enabled_";
    private static final String KEY_ZOOM_LEVEL = "zoom_level";
    private static final String KEY_ZOOM_KEYBIND = "zoom_keybind";
    private static final String KEY_ZOOM_TRANSITION_DURATION = "zoom_transition_duration";
    private static final String KEY_CURSOR_SENSITIVITY = "cursor_sensitivity";
    private static final String KEY_GYRO_SENSITIVITY_MULTIPLIER = "gyro_sensitivity_multiplier";
    private static final String KEY_GYRO_SENSITIVITY_X = "gyro_sensitivity_x";
    private static final String KEY_GYRO_SENSITIVITY_Y = "gyro_sensitivity_y";
    private static final String KEY_GYRO_INVERT_X = "gyro_invert_x";
    private static final String KEY_GYRO_INVERT_Y = "gyro_invert_y";
    private static final String KEY_GYRO_DEADZONE = "gyro_deadzone";
    private static final String KEY_OVERLAY_POSITION_X_PREFIX = "overlay_pos_x_";
    private static final String KEY_OVERLAY_POSITION_Y_PREFIX = "overlay_pos_y_";
    private static final String KEY_OVERLAY_LOCK_PREFIX = "overlay_lock_";
    private static final String KEY_OVERLAY_SHOW_EVERYWHERE_PREFIX = "overlay_show_everywhere_";
    private static final String KEY_HOTBAR_ITEM_ICONS = "hotbar_item_icons";
    private static final String KEY_HOTBAR_ITEM_COUNTS = "hotbar_item_counts";
    private static final String KEY_HOTBAR_SLOT_ENABLED_PREFIX = "hotbar_slot_enabled_";
    private static final int DEFAULT_OVERLAY_BUTTON_SIZE = 56;
    private static final int DEFAULT_OVERLAY_OPACITY = 100;
    private static final int MIN_MOD_MENU_OPACITY = 70;
    private static final int DEFAULT_ZOOM_LEVEL = 10;
    private static final int DEFAULT_ZOOM_TRANSITION_DURATION = 150;
    private static final int DEFAULT_CURSOR_SENSITIVITY = 120;
    private static final int DEFAULT_SPEED_MULTIPLIER = 200;
    private static final boolean DEFAULT_SPEED_S1_ENABLED = true;
    private static final int DEFAULT_SPEED_MODE = 0;
    private static final int DEFAULT_SPEED_MOTION_BOOST = 4;
    private static final int DEFAULT_SPEED_MAX_SPEED = 34;
    private static final int DEFAULT_SPEED_GAP_MAX = 0;
    private static final int DEFAULT_SPEED_RESET_INTERVAL = 3500;
    private static final int DEFAULT_SPEED_MAX_RESET_TICKS = 3;
    private static final boolean DEFAULT_SPEED_HOP_ENABLED = true;
    private static final int DEFAULT_SPEED_HOP_HEIGHT = 420;
    private static final float DEFAULT_SPEED_HOP_HEIGHT_EXACT = 0.42f;
    private static final int DEFAULT_SPEED_HOP_INTERVAL = 1;
    private static final boolean DEFAULT_SPEED_MASK_Y = false;
    private static final int DEFAULT_SPEED_PHANTOM_HOLD = 3;
    private static final int DEFAULT_SPEED_FAKE_JUMP_TICKS = 4;
    private static final boolean DEFAULT_SPEED_CANCEL_CORRECTIONS = false;
    private static final boolean DEFAULT_SPEED_PAUSE_ON_FALL = false;
    private static final boolean DEFAULT_SPEED_PAUSE_IF_SNEAKING = true;
    private static final boolean DEFAULT_SPEED_PAUSE_IF_NOT_MOVING = true;
    private static final boolean DEFAULT_SPEED_AUTO_SPRINT = true;
    private static final boolean DEFAULT_SPEED_DEBUG = false;
    private static final int DEFAULT_AIM_ASSIST_RANGE = 10;
    private static final int DEFAULT_AIM_ASSIST_STRENGTH = 100;
    private static final boolean DEFAULT_AIM_ASSIST_LEGIT = true;
    private static final int DEFAULT_AIM_ASSIST_FOV = 360;
    private static final boolean DEFAULT_AIM_ASSIST_WALL_CHECK = true;
    private static final boolean DEFAULT_AIM_ASSIST_TARGET_PLAYERS = true;
    private static final boolean DEFAULT_AIM_ASSIST_TARGET_MOBS = true;
    private static final int DEFAULT_AIM_ASSIST_BODY_PART = 0;
    private static final int DEFAULT_AIM_ASSIST_MAX_SPEED = 3400;
    private static final int DEFAULT_AIM_ASSIST_SMOOTHING = 60;
    private static final int DEFAULT_AIM_ASSIST_ACCEL = 12000;
    private static final int DEFAULT_AIM_ASSIST_DEADZONE = 0;
    private static final int DEFAULT_AIM_ASSIST_FULL_RANGE = 60;
    private static final int DEFAULT_AIM_ASSIST_STICKY = 0;
    private static final int DEFAULT_AIM_ASSIST_GRACE = 3;
    private static final boolean DEFAULT_AIM_ASSIST_PREFER_CLOSEST = false;
    private static final boolean DEFAULT_AIM_ASSIST_TARGET_LOCK = true;
    private static final boolean DEFAULT_AIM_ASSIST_FAST_AIM = true;
    private static final int DEFAULT_AIM_ASSIST_FAST_AIM_TIME = 35;
    private static final int DEFAULT_AIM_ASSIST_FAST_AIM_MAX_SPEED = 4200;
    private static final boolean DEFAULT_AIM_ASSIST_ANTI_BOT = true;
    private static final boolean DEFAULT_AIM_ASSIST_RANDOM_POINT = true;
    private static final boolean DEFAULT_ESP_SHOW_PLAYERS = true;
    private static final boolean DEFAULT_ESP_SHOW_MOBS = true;
    private static final boolean DEFAULT_ESP_NAME_TAGS = false;
    private static final boolean DEFAULT_ESP_ARMOR_COLORS = true;
    private static final int DEFAULT_ESP_ARMORED_COLOR = 0xFF4E83FF;
    private static final int DEFAULT_ESP_RANGE = 32;
    private static final int DEFAULT_ESP_PLAYER_COLOR = 0xFFFF5555;
    private static final int DEFAULT_ESP_MOB_COLOR = 0xFFFFAA00;
    private static final int DEFAULT_ESP_BOX_STYLE = 0;
    private static final int DEFAULT_TARGET_ESP_STYLE = 0;
    private static final int DEFAULT_TARGET_ESP_COLOR = 0xFF4E83FF;
    private static final int DEFAULT_TARGET_ESP_HOLD = 650;
    private static final int DEFAULT_NAME_TAG_STYLE = 0;
    private static final int DEFAULT_NAME_TAG_COLOR = 0xFF4E83FF;
    private static final int DEFAULT_NAME_TAG_SIZE = 11;
    private static final int DEFAULT_NAME_TAG_ARMOR_SIZE = 15;
    private static final int DEFAULT_NAME_TAG_BLUR = 4;
    private static final boolean DEFAULT_NAME_TAG_SHOW_ARMOR = true;
    private static final boolean DEFAULT_NAME_TAG_SHOW_OFFHAND = true;
    private static final int DEFAULT_SWING_STYLE = 1;
    private static final int DEFAULT_SWING_SPEED = 100;
    private static final int DEFAULT_SWING_AMPLITUDE = 100;
    private static final boolean DEFAULT_TRIGGER_BOT_ENABLED = true;
    private static final int DEFAULT_TRIGGER_BOT_DELAY = 0;
    private static final int DEFAULT_TRIGGER_BOT_REACTION_JITTER = 15;
    private static final int DEFAULT_TRIGGER_BOT_RANGE = 30;
    private static final int DEFAULT_TRIGGER_BOT_TOLERANCE = 8;
    private static final boolean DEFAULT_TRIGGER_BOT_TARGET_PLAYERS = true;
    private static final boolean DEFAULT_TRIGGER_BOT_TARGET_MOBS = true;
    private static final boolean DEFAULT_TRIGGER_BOT_HUMANIZATION = true;
    private static final int DEFAULT_TRIGGER_BOT_INTERVAL = 220;
    private static final int DEFAULT_TRIGGER_BOT_INTERVAL_JITTER = 50;
    private static final int DEFAULT_TRIGGER_BOT_SKIP_CHANCE = 0;
    private static final boolean DEFAULT_TRIGGER_BOT_ONLY_CRIT = false;
    private static final int DEFAULT_FLY_MODE = 0;
    private static final int DEFAULT_FLY_SPEED = 100;
    private static final int DEFAULT_FLY_UP = 42;
    private static final int DEFAULT_FLY_DOWN = 42;
    private static final boolean DEFAULT_FLY_CANCEL_CORRECTIONS = true;
    private static final int DEFAULT_JESUS_MODE = 0;
    private static final int DEFAULT_JESUS_DEPTH = 0;
    private static final int DEFAULT_SCAFFOLD_MODE = 0;
    private static final int DEFAULT_SCAFFOLD_PITCH = 85;
    private static final int DEFAULT_SCAFFOLD_INTERVAL = 0;
    private static final boolean DEFAULT_SCAFFOLD_ROTATE = true;
    private static final int DEFAULT_AUTO_JUMP_HEIGHT = 125;
    private static final int DEFAULT_AUTO_JUMP_MODE = 0;
    private static final boolean DEFAULT_MOVEMENT_DEBUG = false;
    private static final int DEFAULT_GYRO_SENSITIVITY_MULTIPLIER = 100;
    private static final int DEFAULT_GYRO_SENSITIVITY = 100;
    private static final int DEFAULT_GYRO_DEADZONE = 5;
    private static final String KEY_TUNING_REVISION = "tuning_revision";
    private static final int TUNING_REVISION = 8;
    private static final String KEY_FOV_360_REVISION = "fov_360_revision";
    private static final String KEY_SHORTCUT_REVISION = "shortcut_revision";
    private static final String KEY_ONYX_DEFAULTS_REVISION = "onyx_defaults_revision";
    private static final String KEY_SPEED_HOP_HEIGHT_REVISION = "speed_hop_height_revision";
    private static final String KEY_FRIEND_NAMES = "friend_names";

    private static volatile InbuiltModManager instance;
    private final SharedPreferences prefs;
    private final AtomicLong overlayVisibilityRevision = new AtomicLong(1L);
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;

    private InbuiltModManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferenceListener = (sharedPreferences, key) -> {
            if (KEY_PAUSE_MENU_ONLY.equals(key) ||
                    (key != null && key.startsWith(KEY_OVERLAY_SHOW_EVERYWHERE_PREFIX))) {
                overlayVisibilityRevision.incrementAndGet();
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener);
        migrateTuningDefaults();
        migrateFov360();
        migrateSpeedHopHeight();
        migrateShortcutVisibility();
        migrateOnyxDefaults();
    }

    private void migrateOnyxDefaults() {
        if (prefs.getInt(KEY_ONYX_DEFAULTS_REVISION, 0) >= 1) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (String modId : new String[]{
                ModIds.AUTO_SPRINT, ModIds.SPEED, ModIds.AIM_ASSIST, ModIds.ESP,
                ModIds.FULLBRIGHT, ModIds.TRIGGER_BOT, ModIds.SWING,
                ModIds.NAME_TAGS, ModIds.TARGET_ESP, ModIds.FRIEND}) {
            editor.putBoolean(KEY_INBUILT_MOD_ENABLED_PREFIX + modId, false);
            editor.putBoolean(KEY_OVERLAY_SHORTCUT_PREFIX + modId, false);
        }
        editor.putInt(KEY_ONYX_DEFAULTS_REVISION, 1).apply();
    }

    private void migrateSpeedHopHeight() {
        if (prefs.getInt(KEY_SPEED_HOP_HEIGHT_REVISION, 0) >= 3) return;
        prefs.edit()
                .putInt(KEY_SPEED_HOP_HEIGHT, DEFAULT_SPEED_HOP_HEIGHT)
                .putFloat(KEY_SPEED_HOP_HEIGHT_EXACT,
                        DEFAULT_SPEED_HOP_HEIGHT_EXACT)
                .putInt(KEY_SPEED_HOP_HEIGHT_REVISION, 3)
                .apply();
    }

    private void migrateShortcutVisibility() {
        if (prefs.getInt(KEY_SHORTCUT_REVISION, 0) >= 3) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (String modId : new String[]{
                ModIds.AUTO_SPRINT, ModIds.SPEED, ModIds.AIM_ASSIST, ModIds.ESP,
                ModIds.FULLBRIGHT, ModIds.TRIGGER_BOT, ModIds.SWING,
                ModIds.NAME_TAGS, ModIds.TARGET_ESP, ModIds.FRIEND}) {
            editor.putBoolean(KEY_OVERLAY_SHORTCUT_PREFIX + modId, false);
        }
        editor.putInt(KEY_SHORTCUT_REVISION, 3).apply();
    }

    private void migrateFov360() {
        if (prefs.getInt(KEY_FOV_360_REVISION, 0) >= 1) return;
        prefs.edit()
                .putInt(KEY_AIM_ASSIST_FOV, 360)
                .putInt(KEY_FOV_360_REVISION, 1)
                .apply();
    }

    /**
     * One-time reset of tuning values so installs created before the
     * fast-aim/trigger rework do not keep the old sluggish presets.
     */
    private void migrateTuningDefaults() {
        if (prefs.getInt(KEY_TUNING_REVISION, 0) >= TUNING_REVISION) return;
        prefs.edit()
                .putInt(KEY_AIM_ASSIST_MAX_SPEED, DEFAULT_AIM_ASSIST_MAX_SPEED)
                .putInt(KEY_AIM_ASSIST_SMOOTHING, DEFAULT_AIM_ASSIST_SMOOTHING)
                .putInt(KEY_AIM_ASSIST_ACCEL, DEFAULT_AIM_ASSIST_ACCEL)
                .putInt(KEY_AIM_ASSIST_DEADZONE, DEFAULT_AIM_ASSIST_DEADZONE)
                .putInt(KEY_AIM_ASSIST_FULL_RANGE, DEFAULT_AIM_ASSIST_FULL_RANGE)
                .putInt(KEY_AIM_ASSIST_STICKY, DEFAULT_AIM_ASSIST_STICKY)
                .putInt(KEY_AIM_ASSIST_GRACE, DEFAULT_AIM_ASSIST_GRACE)
                .putBoolean(KEY_AIM_ASSIST_TARGET_LOCK,
                        DEFAULT_AIM_ASSIST_TARGET_LOCK)
                .putBoolean(KEY_AIM_ASSIST_FAST_AIM, DEFAULT_AIM_ASSIST_FAST_AIM)
                .putInt(KEY_AIM_ASSIST_FAST_AIM_TIME,
                        DEFAULT_AIM_ASSIST_FAST_AIM_TIME)
                .putInt(KEY_AIM_ASSIST_FAST_AIM_MAX_SPEED,
                        DEFAULT_AIM_ASSIST_FAST_AIM_MAX_SPEED)
                .putBoolean(KEY_AIM_ASSIST_ANTI_BOT,
                        DEFAULT_AIM_ASSIST_ANTI_BOT)
                .putBoolean(KEY_AIM_ASSIST_RANDOM_POINT,
                        DEFAULT_AIM_ASSIST_RANDOM_POINT)
                .putBoolean(KEY_TRIGGER_BOT_ANTI_BOT,
                        DEFAULT_AIM_ASSIST_ANTI_BOT)
                .putBoolean(KEY_TRIGGER_BOT_RANDOM_POINT,
                        DEFAULT_AIM_ASSIST_RANDOM_POINT)
                .putInt(KEY_TRIGGER_BOT_DELAY, DEFAULT_TRIGGER_BOT_DELAY)
                .putInt(KEY_TRIGGER_BOT_REACTION_JITTER, DEFAULT_TRIGGER_BOT_REACTION_JITTER)
                .putInt(KEY_TRIGGER_BOT_TOLERANCE, DEFAULT_TRIGGER_BOT_TOLERANCE)
                .putInt(KEY_TRIGGER_BOT_INTERVAL, DEFAULT_TRIGGER_BOT_INTERVAL)
                .putInt(KEY_TRIGGER_BOT_INTERVAL_JITTER, DEFAULT_TRIGGER_BOT_INTERVAL_JITTER)
                .putInt(KEY_TRIGGER_BOT_SKIP_CHANCE, DEFAULT_TRIGGER_BOT_SKIP_CHANCE)
                .putInt(KEY_SPEED_MODE, DEFAULT_SPEED_MODE)
                .putInt(KEY_SPEED_MOTION_BOOST, DEFAULT_SPEED_MOTION_BOOST)
                .putInt(KEY_SPEED_MAX_SPEED, DEFAULT_SPEED_MAX_SPEED)
                .putInt(KEY_SPEED_GAP_MAX, DEFAULT_SPEED_GAP_MAX)
                .putInt(KEY_SPEED_RESET_INTERVAL, DEFAULT_SPEED_RESET_INTERVAL)
                .putInt(KEY_SPEED_MAX_RESET_TICKS, DEFAULT_SPEED_MAX_RESET_TICKS)
                .putBoolean(KEY_SPEED_HOP_ENABLED, DEFAULT_SPEED_HOP_ENABLED)
                .putInt(KEY_SPEED_HOP_HEIGHT, DEFAULT_SPEED_HOP_HEIGHT)
                .putFloat(KEY_SPEED_HOP_HEIGHT_EXACT, DEFAULT_SPEED_HOP_HEIGHT_EXACT)
                .putInt(KEY_SPEED_HOP_INTERVAL, DEFAULT_SPEED_HOP_INTERVAL)
                .putBoolean(KEY_SPEED_MASK_Y, DEFAULT_SPEED_MASK_Y)
                .putInt(KEY_SPEED_PHANTOM_HOLD, DEFAULT_SPEED_PHANTOM_HOLD)
                .putInt(KEY_SPEED_FAKE_JUMP_TICKS, DEFAULT_SPEED_FAKE_JUMP_TICKS)
                .putBoolean(KEY_SPEED_CANCEL_CORRECTIONS, DEFAULT_SPEED_CANCEL_CORRECTIONS)
                .putBoolean(KEY_SPEED_PAUSE_ON_FALL, DEFAULT_SPEED_PAUSE_ON_FALL)
                .putBoolean(KEY_SPEED_PAUSE_IF_SNEAKING, DEFAULT_SPEED_PAUSE_IF_SNEAKING)
                .putBoolean(KEY_SPEED_PAUSE_IF_NOT_MOVING, DEFAULT_SPEED_PAUSE_IF_NOT_MOVING)
                .putBoolean(KEY_SPEED_AUTO_SPRINT, DEFAULT_SPEED_AUTO_SPRINT)
                .putInt(KEY_TUNING_REVISION, TUNING_REVISION)
                .apply();
    }

    public static InbuiltModManager getInstance(Context context) {
        if (instance == null) {
            synchronized (InbuiltModManager.class) {
                if (instance == null) {
                    instance = new InbuiltModManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public int getAutoSprintKeybind() {
        return prefs.getInt(KEY_AUTOSPRINT_KEY, KeyEvent.KEYCODE_CTRL_LEFT);
    }

    public void setAutoSprintKeybind(int keyCode) {
        prefs.edit().putInt(KEY_AUTOSPRINT_KEY, keyCode).apply();
    }

    public int getSpeedMultiplier() {
        return Math.max(100, Math.min(500,
                prefs.getInt(KEY_SPEED_MULTIPLIER, DEFAULT_SPEED_MULTIPLIER)));
    }

    public void setSpeedMultiplier(int multiplier) {
        prefs.edit().putInt(KEY_SPEED_MULTIPLIER,
                Math.max(100, Math.min(500, multiplier))).apply();
    }

    public boolean isSpeedS1Enabled() {
        return prefs.getBoolean(KEY_SPEED_S1_ENABLED, DEFAULT_SPEED_S1_ENABLED);
    }

    public void setSpeedS1Enabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SPEED_S1_ENABLED, enabled).apply();
    }

    public int getSpeedMode() {
        return Math.max(0, Math.min(2,
                prefs.getInt(KEY_SPEED_MODE, DEFAULT_SPEED_MODE)));
    }

    public void setSpeedMode(int mode) {
        prefs.edit().putInt(KEY_SPEED_MODE, Math.max(0, Math.min(2, mode))).apply();
    }

    public int getSpeedMotionBoostRaw() {
        return Math.max(1, Math.min(200,
                prefs.getInt(KEY_SPEED_MOTION_BOOST, DEFAULT_SPEED_MOTION_BOOST)));
    }

    public float getSpeedMotionBoost() {
        return getSpeedMotionBoostRaw() / 1000f;
    }

    public void setSpeedMotionBoostRaw(int value) {
        prefs.edit().putInt(KEY_SPEED_MOTION_BOOST,
                Math.max(1, Math.min(200, value))).apply();
    }

    public int getSpeedMaxSpeedRaw() {
        return Math.max(28, Math.min(200,
                prefs.getInt(KEY_SPEED_MAX_SPEED, DEFAULT_SPEED_MAX_SPEED)));
    }

    public float getSpeedMaxSpeed() {
        return getSpeedMaxSpeedRaw() / 100f;
    }

    public void setSpeedMaxSpeedRaw(int value) {
        prefs.edit().putInt(KEY_SPEED_MAX_SPEED,
                Math.max(28, Math.min(200, value))).apply();
    }

    public int getSpeedGapMaxRaw() {
        return Math.max(0, Math.min(300,
                prefs.getInt(KEY_SPEED_GAP_MAX, DEFAULT_SPEED_GAP_MAX)));
    }

    public float getSpeedGapMax() {
        return getSpeedGapMaxRaw() / 100f;
    }

    public void setSpeedGapMaxRaw(int value) {
        prefs.edit().putInt(KEY_SPEED_GAP_MAX,
                Math.max(0, Math.min(300, value))).apply();
    }

    public int getSpeedResetInterval() {
        return Math.max(1000, Math.min(10000,
                prefs.getInt(KEY_SPEED_RESET_INTERVAL, DEFAULT_SPEED_RESET_INTERVAL)));
    }

    public void setSpeedResetInterval(int value) {
        prefs.edit().putInt(KEY_SPEED_RESET_INTERVAL,
                Math.max(1000, Math.min(10000, value))).apply();
    }

    public int getSpeedMaxResetTicks() {
        return Math.max(2, Math.min(20,
                prefs.getInt(KEY_SPEED_MAX_RESET_TICKS, DEFAULT_SPEED_MAX_RESET_TICKS)));
    }

    public void setSpeedMaxResetTicks(int value) {
        prefs.edit().putInt(KEY_SPEED_MAX_RESET_TICKS,
                Math.max(2, Math.min(20, value))).apply();
    }

    public boolean isSpeedHopEnabled() {
        return prefs.getBoolean(KEY_SPEED_HOP_ENABLED, DEFAULT_SPEED_HOP_ENABLED);
    }

    public void setSpeedHopEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SPEED_HOP_ENABLED, enabled).apply();
    }

    public int getSpeedHopHeightRaw() {
        return Math.max(0, Math.min(1500,
                prefs.getInt(KEY_SPEED_HOP_HEIGHT, DEFAULT_SPEED_HOP_HEIGHT)));
    }

    public float getSpeedHopHeight() {
        if (prefs.contains(KEY_SPEED_HOP_HEIGHT_EXACT)) {
            return Math.max(0.10f, Math.min(1.50f,
                    prefs.getFloat(KEY_SPEED_HOP_HEIGHT_EXACT,
                            DEFAULT_SPEED_HOP_HEIGHT_EXACT)));
        }
        final int raw = getSpeedHopHeightRaw();
        if (raw > 0) return raw / 1000f;
        return DEFAULT_SPEED_HOP_HEIGHT_EXACT;
    }

    public void setSpeedHopHeight(float height) {
        prefs.edit().putFloat(KEY_SPEED_HOP_HEIGHT_EXACT,
                Math.max(0.02f, Math.min(1.50f, height))).apply();
    }

    public void setSpeedHopHeightRaw(int value) {
        prefs.edit().putInt(KEY_SPEED_HOP_HEIGHT,
                Math.max(0, Math.min(1500, value)))
                .remove(KEY_SPEED_HOP_HEIGHT_EXACT)
                .apply();
    }

    public int getSpeedHopInterval() {
        return Math.max(0, Math.min(20,
                prefs.getInt(KEY_SPEED_HOP_INTERVAL, DEFAULT_SPEED_HOP_INTERVAL)));
    }

    public void setSpeedHopInterval(int value) {
        prefs.edit().putInt(KEY_SPEED_HOP_INTERVAL,
                Math.max(0, Math.min(20, value))).apply();
    }

    public boolean isSpeedMaskY() {
        return prefs.getBoolean(KEY_SPEED_MASK_Y, DEFAULT_SPEED_MASK_Y);
    }

    public void setSpeedMaskY(boolean enabled) {
        prefs.edit().putBoolean(KEY_SPEED_MASK_Y, enabled).apply();
    }

    public int getSpeedPhantomHoldTicks() {
        return Math.max(1, Math.min(8,
                prefs.getInt(KEY_SPEED_PHANTOM_HOLD, DEFAULT_SPEED_PHANTOM_HOLD)));
    }

    public void setSpeedPhantomHoldTicks(int value) {
        prefs.edit().putInt(KEY_SPEED_PHANTOM_HOLD,
                Math.max(1, Math.min(8, value))).apply();
    }

    public int getSpeedFakeJumpTicks() {
        return Math.max(1, Math.min(12,
                prefs.getInt(KEY_SPEED_FAKE_JUMP_TICKS, DEFAULT_SPEED_FAKE_JUMP_TICKS)));
    }

    public void setSpeedFakeJumpTicks(int value) {
        prefs.edit().putInt(KEY_SPEED_FAKE_JUMP_TICKS,
                Math.max(1, Math.min(12, value))).apply();
    }

    public boolean isSpeedCancelCorrections() {
        return prefs.getBoolean(KEY_SPEED_CANCEL_CORRECTIONS, DEFAULT_SPEED_CANCEL_CORRECTIONS);
    }

    public void setSpeedCancelCorrections(boolean enabled) {
        prefs.edit().putBoolean(KEY_SPEED_CANCEL_CORRECTIONS, enabled).apply();
    }

    public boolean isSpeedPauseOnFall() {
        return prefs.getBoolean(KEY_SPEED_PAUSE_ON_FALL, DEFAULT_SPEED_PAUSE_ON_FALL);
    }

    public void setSpeedPauseOnFall(boolean enabled) {
        prefs.edit().putBoolean(KEY_SPEED_PAUSE_ON_FALL, enabled).apply();
    }

    public boolean isSpeedPauseIfSneaking() {
        return prefs.getBoolean(KEY_SPEED_PAUSE_IF_SNEAKING, DEFAULT_SPEED_PAUSE_IF_SNEAKING);
    }

    public void setSpeedPauseIfSneaking(boolean enabled) {
        prefs.edit().putBoolean(KEY_SPEED_PAUSE_IF_SNEAKING, enabled).apply();
    }

    public boolean isSpeedPauseIfNotMoving() {
        return prefs.getBoolean(KEY_SPEED_PAUSE_IF_NOT_MOVING, DEFAULT_SPEED_PAUSE_IF_NOT_MOVING);
    }

    public void setSpeedPauseIfNotMoving(boolean enabled) {
        prefs.edit().putBoolean(KEY_SPEED_PAUSE_IF_NOT_MOVING, enabled).apply();
    }

    public boolean isSpeedAutoSprint() {
        return prefs.getBoolean(KEY_SPEED_AUTO_SPRINT, DEFAULT_SPEED_AUTO_SPRINT);
    }

    public void setSpeedAutoSprint(boolean enabled) {
        prefs.edit().putBoolean(KEY_SPEED_AUTO_SPRINT, enabled).apply();
    }

    public boolean isSpeedDebug() {
        return prefs.getBoolean(KEY_SPEED_DEBUG, DEFAULT_SPEED_DEBUG);
    }

    public void setSpeedDebug(boolean enabled) {
        prefs.edit().putBoolean(KEY_SPEED_DEBUG, enabled).apply();
    }

    // ---- Movement module (Fly / Jesus / Scaffold / AutoJump) ----

    public int getFlyMode() {
        return Math.max(0, Math.min(1,
                prefs.getInt(KEY_FLY_MODE, DEFAULT_FLY_MODE)));
    }

    public void setFlyMode(int mode) {
        prefs.edit().putInt(KEY_FLY_MODE, Math.max(0, Math.min(1, mode))).apply();
    }

    public int getFlySpeedRaw() {
        return Math.max(10, Math.min(500,
                prefs.getInt(KEY_FLY_SPEED, DEFAULT_FLY_SPEED)));
    }

    public float getFlySpeed() {
        return getFlySpeedRaw() / 100f;
    }

    public void setFlySpeedRaw(int value) {
        prefs.edit().putInt(KEY_FLY_SPEED, Math.max(10, Math.min(500, value))).apply();
    }

    public int getFlyUpRaw() {
        return Math.max(5, Math.min(150,
                prefs.getInt(KEY_FLY_UP, DEFAULT_FLY_UP)));
    }

    public float getFlyUp() {
        return getFlyUpRaw() / 100f;
    }

    public void setFlyUpRaw(int value) {
        prefs.edit().putInt(KEY_FLY_UP, Math.max(5, Math.min(150, value))).apply();
    }

    public int getFlyDownRaw() {
        return Math.max(5, Math.min(150,
                prefs.getInt(KEY_FLY_DOWN, DEFAULT_FLY_DOWN)));
    }

    public float getFlyDown() {
        return getFlyDownRaw() / 100f;
    }

    public void setFlyDownRaw(int value) {
        prefs.edit().putInt(KEY_FLY_DOWN, Math.max(5, Math.min(150, value))).apply();
    }

    public boolean isFlyCancelCorrections() {
        return prefs.getBoolean(KEY_FLY_CANCEL_CORRECTIONS, DEFAULT_FLY_CANCEL_CORRECTIONS);
    }

    public void setFlyCancelCorrections(boolean enabled) {
        prefs.edit().putBoolean(KEY_FLY_CANCEL_CORRECTIONS, enabled).apply();
    }

    public int getJesusMode() {
        return Math.max(0, Math.min(1,
                prefs.getInt(KEY_JESUS_MODE, DEFAULT_JESUS_MODE)));
    }

    public void setJesusMode(int mode) {
        prefs.edit().putInt(KEY_JESUS_MODE, Math.max(0, Math.min(1, mode))).apply();
    }

    public int getJesusDepthRaw() {
        return Math.max(0, Math.min(100,
                prefs.getInt(KEY_JESUS_DEPTH, DEFAULT_JESUS_DEPTH)));
    }

    public float getJesusDepth() {
        return getJesusDepthRaw() / 100f;
    }

    public void setJesusDepthRaw(int value) {
        prefs.edit().putInt(KEY_JESUS_DEPTH, Math.max(0, Math.min(100, value))).apply();
    }

    public int getScaffoldMode() {
        return Math.max(0, Math.min(1,
                prefs.getInt(KEY_SCAFFOLD_MODE, DEFAULT_SCAFFOLD_MODE)));
    }

    public void setScaffoldMode(int mode) {
        prefs.edit().putInt(KEY_SCAFFOLD_MODE, Math.max(0, Math.min(1, mode))).apply();
    }

    public int getScaffoldPitch() {
        return Math.max(0, Math.min(89,
                prefs.getInt(KEY_SCAFFOLD_PITCH, DEFAULT_SCAFFOLD_PITCH)));
    }

    public void setScaffoldPitch(int pitch) {
        prefs.edit().putInt(KEY_SCAFFOLD_PITCH, Math.max(0, Math.min(89, pitch))).apply();
    }

    public int getScaffoldInterval() {
        return Math.max(0, Math.min(10,
                prefs.getInt(KEY_SCAFFOLD_INTERVAL, DEFAULT_SCAFFOLD_INTERVAL)));
    }

    public void setScaffoldInterval(int interval) {
        prefs.edit().putInt(KEY_SCAFFOLD_INTERVAL, Math.max(0, Math.min(10, interval))).apply();
    }

    public boolean isScaffoldRotate() {
        return prefs.getBoolean(KEY_SCAFFOLD_ROTATE, DEFAULT_SCAFFOLD_ROTATE);
    }

    public void setScaffoldRotate(boolean enabled) {
        prefs.edit().putBoolean(KEY_SCAFFOLD_ROTATE, enabled).apply();
    }

    public int getAutoJumpHeightRaw() {
        return Math.max(20, Math.min(400,
                prefs.getInt(KEY_AUTO_JUMP_HEIGHT, DEFAULT_AUTO_JUMP_HEIGHT)));
    }

    public float getAutoJumpHeight() {
        return getAutoJumpHeightRaw() / 100f;
    }

    public void setAutoJumpHeightRaw(int value) {
        prefs.edit().putInt(KEY_AUTO_JUMP_HEIGHT, Math.max(20, Math.min(400, value))).apply();
    }

    public int getAutoJumpMode() {
        return Math.max(0, Math.min(1,
                prefs.getInt(KEY_AUTO_JUMP_MODE, DEFAULT_AUTO_JUMP_MODE)));
    }

    public void setAutoJumpMode(int mode) {
        prefs.edit().putInt(KEY_AUTO_JUMP_MODE, Math.max(0, Math.min(1, mode))).apply();
    }

    public boolean isMovementDebug() {
        return prefs.getBoolean(KEY_MOVEMENT_DEBUG, DEFAULT_MOVEMENT_DEBUG);
    }

    public void setMovementDebug(boolean enabled) {
        prefs.edit().putBoolean(KEY_MOVEMENT_DEBUG, enabled).apply();
    }

    public int getAimAssistRange() {
        return Math.max(3, Math.min(20,
                prefs.getInt(KEY_AIM_ASSIST_RANGE, DEFAULT_AIM_ASSIST_RANGE)));
    }

    public void setAimAssistRange(int range) {
        prefs.edit().putInt(KEY_AIM_ASSIST_RANGE,
                Math.max(3, Math.min(20, range))).apply();
    }

    public int getAimAssistFov() {
        return Math.max(30, Math.min(360,
                prefs.getInt(KEY_AIM_ASSIST_FOV, DEFAULT_AIM_ASSIST_FOV)));
    }

    public void setAimAssistFov(int fov) {
        prefs.edit().putInt(KEY_AIM_ASSIST_FOV,
                Math.max(30, Math.min(360, fov))).apply();
    }

    public boolean isAimAssistWallCheck() {
        return prefs.getBoolean(KEY_AIM_ASSIST_WALL_CHECK, DEFAULT_AIM_ASSIST_WALL_CHECK);
    }

    public void setAimAssistWallCheck(boolean enabled) {
        prefs.edit().putBoolean(KEY_AIM_ASSIST_WALL_CHECK, enabled).apply();
    }

    public boolean isAimAssistIgnoreInvisible() {
        return prefs.getBoolean(KEY_AIM_ASSIST_IGNORE_INVISIBLE, false);
    }

    public void setAimAssistIgnoreInvisible(boolean enabled) {
        prefs.edit().putBoolean(KEY_AIM_ASSIST_IGNORE_INVISIBLE, enabled).apply();
    }

    public boolean isAimAssistNeuralRotation() {
        return prefs.getBoolean(KEY_AIM_ASSIST_NEURAL, false);
    }

    public void setAimAssistNeuralRotation(boolean enabled) {
        prefs.edit().putBoolean(KEY_AIM_ASSIST_NEURAL, enabled).apply();
    }

    public boolean isAimAssistKillaura() {
        return prefs.getBoolean(KEY_AIM_ASSIST_KILLAURA, false);
    }

    public void setAimAssistKillaura(boolean enabled) {
        prefs.edit().putBoolean(KEY_AIM_ASSIST_KILLAURA, enabled).apply();
    }

    public int getKillauraRange() {
        return Math.max(3, Math.min(6, prefs.getInt(KEY_KILLAURA_RANGE, 4)));
    }

    public void setKillauraRange(int range) {
        prefs.edit().putInt(KEY_KILLAURA_RANGE, Math.max(3, Math.min(6, range))).apply();
    }

    public int getKillauraCone() {
        return Math.max(20, Math.min(180, prefs.getInt(KEY_KILLAURA_CONE, 90)));
    }

    public void setKillauraCone(int cone) {
        prefs.edit().putInt(KEY_KILLAURA_CONE, Math.max(20, Math.min(180, cone))).apply();
    }

    public int getKillauraReaction() {
        return Math.max(0, Math.min(500, prefs.getInt(KEY_KILLAURA_REACTION, 150)));
    }

    public void setKillauraReaction(int ms) {
        prefs.edit().putInt(KEY_KILLAURA_REACTION, Math.max(0, Math.min(500, ms))).apply();
    }

    public boolean isAimAssistRotationRecording() {
        return prefs.getBoolean(KEY_AIM_ASSIST_ROT_RECORDING, false);
    }

    public void setAimAssistRotationRecording(boolean enabled) {
        prefs.edit().putBoolean(KEY_AIM_ASSIST_ROT_RECORDING, enabled).apply();
    }

    public boolean isAimAssistTargetPlayers() {
        return prefs.getBoolean(KEY_AIM_ASSIST_TARGET_PLAYERS, DEFAULT_AIM_ASSIST_TARGET_PLAYERS);
    }

    public void setAimAssistTargetPlayers(boolean enabled) {
        prefs.edit().putBoolean(KEY_AIM_ASSIST_TARGET_PLAYERS, enabled).apply();
    }

    public boolean isAimAssistTargetMobs() {
        return prefs.getBoolean(KEY_AIM_ASSIST_TARGET_MOBS, DEFAULT_AIM_ASSIST_TARGET_MOBS);
    }

    public void setAimAssistTargetMobs(boolean enabled) {
        prefs.edit().putBoolean(KEY_AIM_ASSIST_TARGET_MOBS, enabled).apply();
    }

    public int getAimAssistBodyPart() {
        return Math.max(0, Math.min(4,
                prefs.getInt(KEY_AIM_ASSIST_BODY_PART, DEFAULT_AIM_ASSIST_BODY_PART)));
    }

    public void setAimAssistBodyPart(int bodyPart) {
        prefs.edit().putInt(KEY_AIM_ASSIST_BODY_PART,
                Math.max(0, Math.min(4, bodyPart))).apply();
    }

    public boolean isAimAssistTargetLock() {
        return prefs.getBoolean(KEY_AIM_ASSIST_TARGET_LOCK, DEFAULT_AIM_ASSIST_TARGET_LOCK);
    }

    public void setAimAssistTargetLock(boolean enabled) {
        prefs.edit().putBoolean(KEY_AIM_ASSIST_TARGET_LOCK, enabled).apply();
    }

    public boolean isAimAssistFastAim() {
        return prefs.getBoolean(KEY_AIM_ASSIST_FAST_AIM, DEFAULT_AIM_ASSIST_FAST_AIM);
    }

    public void setAimAssistFastAim(boolean enabled) {
        prefs.edit().putBoolean(KEY_AIM_ASSIST_FAST_AIM, enabled).apply();
    }

    public int getAimAssistFastAimTime() {
        return Math.max(20, Math.min(200,
                prefs.getInt(KEY_AIM_ASSIST_FAST_AIM_TIME, DEFAULT_AIM_ASSIST_FAST_AIM_TIME)));
    }

    public void setAimAssistFastAimTime(int timeMs) {
        prefs.edit().putInt(KEY_AIM_ASSIST_FAST_AIM_TIME,
                Math.max(20, Math.min(200, timeMs))).apply();
    }

    public int getAimAssistFastAimMaxSpeed() {
        return Math.max(120, Math.min(4000,
                prefs.getInt(KEY_AIM_ASSIST_FAST_AIM_MAX_SPEED,
                        DEFAULT_AIM_ASSIST_FAST_AIM_MAX_SPEED)));
    }

    public void setAimAssistFastAimMaxSpeed(int degPerSecond) {
        prefs.edit().putInt(KEY_AIM_ASSIST_FAST_AIM_MAX_SPEED,
                Math.max(120, Math.min(4000, degPerSecond))).apply();
    }

    public boolean isAimAssistAntiBot() {
        return prefs.getBoolean(KEY_AIM_ASSIST_ANTI_BOT,
                DEFAULT_AIM_ASSIST_ANTI_BOT);
    }

    public void setAimAssistAntiBot(boolean enabled) {
        prefs.edit()
                .putBoolean(KEY_AIM_ASSIST_ANTI_BOT, enabled)
                .putBoolean(KEY_TRIGGER_BOT_ANTI_BOT, enabled)
                .apply();
    }

    public boolean isTriggerBotAntiBot() {
        return prefs.getBoolean(KEY_TRIGGER_BOT_ANTI_BOT,
                isAimAssistAntiBot());
    }

    public void setTriggerBotAntiBot(boolean enabled) {
        setAimAssistAntiBot(enabled);
    }

    public boolean isAimAssistRandomPoint() {
        return prefs.getBoolean(KEY_AIM_ASSIST_RANDOM_POINT,
                DEFAULT_AIM_ASSIST_RANDOM_POINT);
    }

    public void setAimAssistRandomPoint(boolean enabled) {
        prefs.edit()
                .putBoolean(KEY_AIM_ASSIST_RANDOM_POINT, enabled)
                .putBoolean(KEY_TRIGGER_BOT_RANDOM_POINT, enabled)
                .apply();
    }

    public boolean isTriggerBotRandomPoint() {
        return prefs.getBoolean(KEY_TRIGGER_BOT_RANDOM_POINT,
                isAimAssistRandomPoint());
    }

    public void setTriggerBotRandomPoint(boolean enabled) {
        setAimAssistRandomPoint(enabled);
    }

    public boolean isEspShowPlayers() {
        return prefs.getBoolean(KEY_ESP_SHOW_PLAYERS, DEFAULT_ESP_SHOW_PLAYERS);
    }

    public void setEspShowPlayers(boolean enabled) {
        prefs.edit().putBoolean(KEY_ESP_SHOW_PLAYERS, enabled).apply();
    }

    public boolean isEspShowMobs() {
        return prefs.getBoolean(KEY_ESP_SHOW_MOBS, DEFAULT_ESP_SHOW_MOBS);
    }

    public void setEspShowMobs(boolean enabled) {
        prefs.edit().putBoolean(KEY_ESP_SHOW_MOBS, enabled).apply();
    }

    public boolean isEspNameTags() {
        return prefs.getBoolean(KEY_ESP_NAME_TAGS, DEFAULT_ESP_NAME_TAGS);
    }

    public void setEspNameTags(boolean enabled) {
        prefs.edit().putBoolean(KEY_ESP_NAME_TAGS, enabled).apply();
    }

    public int getEspRange() {
        return Math.max(4, Math.min(512,
                prefs.getInt(KEY_ESP_RANGE, DEFAULT_ESP_RANGE)));
    }

    public void setEspRange(int range) {
        prefs.edit().putInt(KEY_ESP_RANGE,
                Math.max(4, Math.min(512, range))).apply();
    }

    public String getFriendNames() {
        return prefs.getString(KEY_FRIEND_NAMES, "");
    }

    public void setFriendNames(String names) {
        prefs.edit().putString(KEY_FRIEND_NAMES, names == null ? "" : names).apply();
    }

    public boolean isFriendModuleEnabled() {
        return resolveInbuiltModEnabled(ModIds.FRIEND, false);
    }

    public int getEspPlayerColor() {
        return prefs.getInt(KEY_ESP_PLAYER_COLOR, DEFAULT_ESP_PLAYER_COLOR);
    }

    public void setEspPlayerColor(int argb) {
        prefs.edit().putInt(KEY_ESP_PLAYER_COLOR, argb).apply();
    }

    public int getEspMobColor() {
        return prefs.getInt(KEY_ESP_MOB_COLOR, DEFAULT_ESP_MOB_COLOR);
    }

    public void setEspMobColor(int argb) {
        prefs.edit().putInt(KEY_ESP_MOB_COLOR, argb).apply();
    }

    public boolean isEspArmorColors() {
        return prefs.getBoolean(KEY_ESP_ARMOR_COLORS, DEFAULT_ESP_ARMOR_COLORS);
    }

    public void setEspArmorColors(boolean enabled) {
        prefs.edit().putBoolean(KEY_ESP_ARMOR_COLORS, enabled).apply();
    }

    public int getEspArmoredColor() {
        return prefs.getInt(KEY_ESP_ARMORED_COLOR, DEFAULT_ESP_ARMORED_COLOR);
    }

    public void setEspArmoredColor(int argb) {
        prefs.edit().putInt(KEY_ESP_ARMORED_COLOR, argb).apply();
    }

    public int getEspBoxStyle() {
        return Math.max(0, Math.min(4,
                prefs.getInt(KEY_ESP_BOX_STYLE, DEFAULT_ESP_BOX_STYLE)));
    }

    public void setEspBoxStyle(int style) {
        prefs.edit().putInt(KEY_ESP_BOX_STYLE,
                Math.max(0, Math.min(4, style))).apply();
    }

    public boolean isNameTagsEnabled() {
        return resolveInbuiltModEnabled(ModIds.NAME_TAGS, false);
    }

    public void setNameTagsEnabled(boolean enabled) {
        setInbuiltModEnabled(ModIds.NAME_TAGS, enabled);
    }

    public boolean isTargetEspEnabled() {
        return resolveInbuiltModEnabled(ModIds.TARGET_ESP, false);
    }

    public void setTargetEspEnabled(boolean enabled) {
        setInbuiltModEnabled(ModIds.TARGET_ESP, enabled);
    }

    public int getTargetEspStyle() {
        return Math.max(0, Math.min(2,
                prefs.getInt(KEY_TARGET_ESP_STYLE, DEFAULT_TARGET_ESP_STYLE)));
    }

    public void setTargetEspStyle(int style) {
        prefs.edit().putInt(KEY_TARGET_ESP_STYLE,
                Math.max(0, Math.min(2, style))).apply();
    }

    public int getNameTagStyle() {
        return Math.max(0, Math.min(4,
                prefs.getInt(KEY_NAME_TAG_STYLE, DEFAULT_NAME_TAG_STYLE)));
    }

    public void setNameTagStyle(int style) {
        prefs.edit().putInt(KEY_NAME_TAG_STYLE,
                Math.max(0, Math.min(4, style))).apply();
    }

    public int getSwingStyle() {
        return Math.max(0, Math.min(5,
                prefs.getInt(KEY_SWING_STYLE, DEFAULT_SWING_STYLE)));
    }

    public void setSwingStyle(int style) {
        prefs.edit().putInt(KEY_SWING_STYLE,
                Math.max(0, Math.min(5, style))).apply();
    }

    public int getSwingSpeed() {
        return Math.max(50, Math.min(200,
                prefs.getInt(KEY_SWING_SPEED, DEFAULT_SWING_SPEED)));
    }

    public void setSwingSpeed(int percent) {
        prefs.edit().putInt(KEY_SWING_SPEED,
                Math.max(50, Math.min(200, percent))).apply();
    }

    public int getSwingAmplitude() {
        return Math.max(0, Math.min(150,
                prefs.getInt(KEY_SWING_AMPLITUDE, DEFAULT_SWING_AMPLITUDE)));
    }

    public void setSwingAmplitude(int percent) {
        prefs.edit().putInt(KEY_SWING_AMPLITUDE,
                Math.max(0, Math.min(150, percent))).apply();
    }

    public int getNameTagColor() {
        return prefs.getInt(KEY_NAME_TAG_COLOR, DEFAULT_NAME_TAG_COLOR);
    }

    public void setNameTagColor(int argb) {
        prefs.edit().putInt(KEY_NAME_TAG_COLOR, argb).apply();
    }

    public int getNameTagSize() {
        return Math.max(7, Math.min(24,
                prefs.getInt(KEY_NAME_TAG_SIZE, DEFAULT_NAME_TAG_SIZE)));
    }

    public void setNameTagSize(int size) {
        prefs.edit().putInt(KEY_NAME_TAG_SIZE,
                Math.max(7, Math.min(24, size))).apply();
    }

    public int getNameTagArmorSize() {
        return Math.max(8, Math.min(30,
                prefs.getInt(KEY_NAME_TAG_ARMOR_SIZE,
                        DEFAULT_NAME_TAG_ARMOR_SIZE)));
    }

    public void setNameTagArmorSize(int size) {
        prefs.edit().putInt(KEY_NAME_TAG_ARMOR_SIZE,
                Math.max(8, Math.min(30, size))).apply();
    }

    public int getNameTagBlur() {
        return Math.max(0, Math.min(20,
                prefs.getInt(KEY_NAME_TAG_BLUR, DEFAULT_NAME_TAG_BLUR)));
    }

    public void setNameTagBlur(int blur) {
        prefs.edit().putInt(KEY_NAME_TAG_BLUR,
                Math.max(0, Math.min(20, blur))).apply();
    }

    public int getNameTagBlurColor() {
        return prefs.getInt(KEY_NAME_TAG_BLUR_COLOR, 0xFF101018);
    }

    public void setNameTagBlurColor(int argb) {
        prefs.edit().putInt(KEY_NAME_TAG_BLUR_COLOR, argb).apply();
    }

    public int getNameTagOutlineColor() {
        return prefs.getInt(KEY_NAME_TAG_OUTLINE_COLOR, 0xFF0A0A12);
    }

    public void setNameTagOutlineColor(int argb) {
        prefs.edit().putInt(KEY_NAME_TAG_OUTLINE_COLOR, argb).apply();
    }

    public boolean isNameTagShowArmor() {
        return prefs.getBoolean(KEY_NAME_TAG_SHOW_ARMOR,
                DEFAULT_NAME_TAG_SHOW_ARMOR);
    }

    public void setNameTagShowArmor(boolean enabled) {
        prefs.edit().putBoolean(KEY_NAME_TAG_SHOW_ARMOR, enabled).apply();
    }

    public boolean isNameTagShowOffhand() {
        return prefs.getBoolean(KEY_NAME_TAG_SHOW_OFFHAND,
                DEFAULT_NAME_TAG_SHOW_OFFHAND);
    }

    public void setNameTagShowOffhand(boolean enabled) {
        prefs.edit().putBoolean(KEY_NAME_TAG_SHOW_OFFHAND, enabled).apply();
    }

    public int getTargetEspColor() {
        return prefs.getInt(KEY_TARGET_ESP_COLOR, DEFAULT_TARGET_ESP_COLOR);
    }

    public void setTargetEspColor(int argb) {
        prefs.edit().putInt(KEY_TARGET_ESP_COLOR, argb).apply();
    }

    public int getTargetEspHoldMs() {
        return Math.max(200, Math.min(3000,
                prefs.getInt(KEY_TARGET_ESP_HOLD, DEFAULT_TARGET_ESP_HOLD)));
    }

    public void setTargetEspHoldMs(int holdMs) {
        prefs.edit().putInt(KEY_TARGET_ESP_HOLD,
                Math.max(200, Math.min(3000, holdMs))).apply();
    }

    public boolean isFullbrightEnabled() {
        return resolveInbuiltModEnabled(ModIds.FULLBRIGHT, false);
    }

    public void setFullbrightEnabled(boolean enabled) {
        setInbuiltModEnabled(ModIds.FULLBRIGHT, enabled);
    }

    public int getAimAssistMaxSpeed() {
        return Math.max(100, Math.min(3000,
                prefs.getInt(KEY_AIM_ASSIST_MAX_SPEED, DEFAULT_AIM_ASSIST_MAX_SPEED)));
    }

    public void setAimAssistMaxSpeed(int degPerSecond) {
        prefs.edit().putInt(KEY_AIM_ASSIST_MAX_SPEED,
                Math.max(100, Math.min(3000, degPerSecond))).apply();
    }

    public int getAimAssistSmoothing() {
        return Math.max(1, Math.min(100,
                prefs.getInt(KEY_AIM_ASSIST_SMOOTHING, DEFAULT_AIM_ASSIST_SMOOTHING)));
    }

    public void setAimAssistSmoothing(int value) {
        prefs.edit().putInt(KEY_AIM_ASSIST_SMOOTHING,
                Math.max(1, Math.min(100, value))).apply();
    }

    public int getAimAssistAcceleration() {
        return Math.max(200, Math.min(20000,
                prefs.getInt(KEY_AIM_ASSIST_ACCEL, DEFAULT_AIM_ASSIST_ACCEL)));
    }

    public void setAimAssistAcceleration(int value) {
        prefs.edit().putInt(KEY_AIM_ASSIST_ACCEL,
                Math.max(200, Math.min(20000, value))).apply();
    }

    public int getAimAssistDeadzone() {
        return Math.max(0, Math.min(50,
                prefs.getInt(KEY_AIM_ASSIST_DEADZONE, DEFAULT_AIM_ASSIST_DEADZONE)));
    }

    public void setAimAssistDeadzone(int hundredthsDeg) {
        prefs.edit().putInt(KEY_AIM_ASSIST_DEADZONE,
                Math.max(0, Math.min(50, hundredthsDeg))).apply();
    }

    public int getAimAssistFullRange() {
        return Math.max(1, Math.min(90,
                prefs.getInt(KEY_AIM_ASSIST_FULL_RANGE, DEFAULT_AIM_ASSIST_FULL_RANGE)));
    }

    public void setAimAssistFullRange(int deg) {
        prefs.edit().putInt(KEY_AIM_ASSIST_FULL_RANGE,
                Math.max(1, Math.min(90, deg))).apply();
    }

    public int getAimAssistSticky() {
        return Math.max(0, Math.min(20,
                prefs.getInt(KEY_AIM_ASSIST_STICKY, DEFAULT_AIM_ASSIST_STICKY)));
    }

    public void setAimAssistSticky(int ticks) {
        prefs.edit().putInt(KEY_AIM_ASSIST_STICKY,
                Math.max(0, Math.min(20, ticks))).apply();
    }

    public int getAimAssistGrace() {
        return Math.max(0, Math.min(30,
                prefs.getInt(KEY_AIM_ASSIST_GRACE, DEFAULT_AIM_ASSIST_GRACE)));
    }

    public void setAimAssistGrace(int frames) {
        prefs.edit().putInt(KEY_AIM_ASSIST_GRACE,
                Math.max(0, Math.min(30, frames))).apply();
    }

    public boolean isAimAssistPreferClosest() {
        return prefs.getBoolean(KEY_AIM_ASSIST_PREFER_CLOSEST, DEFAULT_AIM_ASSIST_PREFER_CLOSEST);
    }

    public void setAimAssistPreferClosest(boolean enabled) {
        prefs.edit().putBoolean(KEY_AIM_ASSIST_PREFER_CLOSEST, enabled).apply();
    }

    public int getAimAssistStrength() {
        return Math.max(0, Math.min(100,
                prefs.getInt(KEY_AIM_ASSIST_STRENGTH, DEFAULT_AIM_ASSIST_STRENGTH)));
    }

    public void setAimAssistStrength(int strength) {
        prefs.edit().putInt(KEY_AIM_ASSIST_STRENGTH,
                Math.max(0, Math.min(100, strength))).apply();
    }

    public boolean isAimAssistLegitMode() {
        return prefs.getBoolean(KEY_AIM_ASSIST_LEGIT, DEFAULT_AIM_ASSIST_LEGIT);
    }

    public void setAimAssistLegitMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_AIM_ASSIST_LEGIT, enabled).apply();
    }

    public boolean isTriggerBotEnabled() {
        return prefs.getBoolean(KEY_TRIGGER_BOT_ENABLED, DEFAULT_TRIGGER_BOT_ENABLED);
    }

    public void setTriggerBotEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRIGGER_BOT_ENABLED, enabled).apply();
    }

    public int getTriggerBotDelay() {
        int value = prefs.getInt(KEY_TRIGGER_BOT_DELAY, DEFAULT_TRIGGER_BOT_DELAY);
        if (value < 120) {
            value = 280;
            prefs.edit().putInt(KEY_TRIGGER_BOT_DELAY, value).apply();
        }
        return Math.max(120, Math.min(400, value));
    }

    public void setTriggerBotDelay(int delayMs) {
        prefs.edit().putInt(KEY_TRIGGER_BOT_DELAY,
                Math.max(120, Math.min(400, delayMs))).apply();
    }

    public int getTriggerBotReactionJitter() {
        return Math.max(0, Math.min(300,
                prefs.getInt(KEY_TRIGGER_BOT_REACTION_JITTER, DEFAULT_TRIGGER_BOT_REACTION_JITTER)));
    }

    public void setTriggerBotReactionJitter(int jitterMs) {
        prefs.edit().putInt(KEY_TRIGGER_BOT_REACTION_JITTER,
                Math.max(0, Math.min(300, jitterMs))).apply();
    }

    /** Дальность триггера в десятых долях блока (30 = 3.0 блока). */
    public int getTriggerBotRangeRaw() {
        return Math.max(20, Math.min(50,
                prefs.getInt(KEY_TRIGGER_BOT_RANGE, DEFAULT_TRIGGER_BOT_RANGE)));
    }

    public float getTriggerBotRange() {
        return getTriggerBotRangeRaw() / 10.0f;
    }

    public void setTriggerBotRange(int tenths) {
        prefs.edit().putInt(KEY_TRIGGER_BOT_RANGE,
                Math.max(20, Math.min(50, tenths))).apply();
    }

    /** Допуск по хитбоксу в сотых долях блока (3 = 0.03 блока). */
    public int getTriggerBotToleranceRaw() {
        return Math.max(0, Math.min(25,
                prefs.getInt(KEY_TRIGGER_BOT_TOLERANCE, DEFAULT_TRIGGER_BOT_TOLERANCE)));
    }

    public float getTriggerBotTolerance() {
        return getTriggerBotToleranceRaw() / 100.0f;
    }

    public void setTriggerBotTolerance(int hundredths) {
        prefs.edit().putInt(KEY_TRIGGER_BOT_TOLERANCE,
                Math.max(0, Math.min(25, hundredths))).apply();
    }

    public boolean isTriggerBotTargetPlayers() {
        return prefs.getBoolean(KEY_TRIGGER_BOT_TARGET_PLAYERS, DEFAULT_TRIGGER_BOT_TARGET_PLAYERS);
    }

    public void setTriggerBotTargetPlayers(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRIGGER_BOT_TARGET_PLAYERS, enabled).apply();
    }

    public boolean isTriggerBotTargetMobs() {
        return prefs.getBoolean(KEY_TRIGGER_BOT_TARGET_MOBS, DEFAULT_TRIGGER_BOT_TARGET_MOBS);
    }

    public void setTriggerBotTargetMobs(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRIGGER_BOT_TARGET_MOBS, enabled).apply();
    }

    public boolean isTriggerBotHumanization() {
        return prefs.getBoolean(KEY_TRIGGER_BOT_HUMANIZATION, DEFAULT_TRIGGER_BOT_HUMANIZATION);
    }

    public void setTriggerBotHumanization(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRIGGER_BOT_HUMANIZATION, enabled).apply();
    }

    public int getTriggerBotInterval() {
        return Math.max(100, Math.min(1200,
                prefs.getInt(KEY_TRIGGER_BOT_INTERVAL, DEFAULT_TRIGGER_BOT_INTERVAL)));
    }

    public void setTriggerBotInterval(int intervalMs) {
        prefs.edit().putInt(KEY_TRIGGER_BOT_INTERVAL,
                Math.max(100, Math.min(1200, intervalMs))).apply();
    }

    public int getTriggerBotIntervalJitter() {
        return Math.max(0, Math.min(400,
                prefs.getInt(KEY_TRIGGER_BOT_INTERVAL_JITTER, DEFAULT_TRIGGER_BOT_INTERVAL_JITTER)));
    }

    public void setTriggerBotIntervalJitter(int jitterMs) {
        prefs.edit().putInt(KEY_TRIGGER_BOT_INTERVAL_JITTER,
                Math.max(0, Math.min(400, jitterMs))).apply();
    }

    public int getTriggerBotSkipChance() {
        return Math.max(0, Math.min(50,
                prefs.getInt(KEY_TRIGGER_BOT_SKIP_CHANCE, DEFAULT_TRIGGER_BOT_SKIP_CHANCE)));
    }

    public void setTriggerBotSkipChance(int percent) {
        prefs.edit().putInt(KEY_TRIGGER_BOT_SKIP_CHANCE,
                Math.max(0, Math.min(50, percent))).apply();
    }

    public boolean isTriggerBotOnlyCrit() {
        return prefs.getBoolean(KEY_TRIGGER_BOT_ONLY_CRIT, DEFAULT_TRIGGER_BOT_ONLY_CRIT);
    }

    public void setTriggerBotOnlyCrit(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRIGGER_BOT_ONLY_CRIT, enabled).apply();
    }

    public int getDefaultInbuiltModKeybind(String modId) {
        if (ModIds.ZOOM.equals(modId)) return KeyEvent.KEYCODE_C;
        if (ModIds.AUTO_SPRINT.equals(modId)) return KeyEvent.KEYCODE_CTRL_LEFT;
        if (ModIds.SPEED.equals(modId)) return KeyEvent.KEYCODE_V;
        if (ModIds.FLY.equals(modId)) return KeyEvent.KEYCODE_F;
        if (ModIds.JESUS.equals(modId)) return KeyEvent.KEYCODE_J;
        if (ModIds.SCAFFOLD.equals(modId)) return KeyEvent.KEYCODE_H;
        if (ModIds.AUTO_JUMP.equals(modId)) return KeyEvent.KEYCODE_K;
        if (ModIds.AIM_ASSIST.equals(modId)) return KeyEvent.KEYCODE_B;
        if (ModIds.TRIGGER_BOT.equals(modId)) return KeyEvent.KEYCODE_G;
        if (ModIds.ESP.equals(modId)) return KeyEvent.KEYCODE_N;
        if (ModIds.SNAPLOOK.equals(modId)) return KeyEvent.KEYCODE_X;
        return KeyEvent.KEYCODE_UNKNOWN;
    }

    public int getInbuiltModKeybind(String modId) {
        if (ModIds.ZOOM.equals(modId)) return getZoomKeybind();
        if (ModIds.AUTO_SPRINT.equals(modId)) return getAutoSprintKeybind();
        return prefs.getInt(KEY_INBUILT_KEYBIND_PREFIX + modId, getDefaultInbuiltModKeybind(modId));
    }

    public void setInbuiltModKeybind(String modId, int keyCode) {
        if (modId == null || modId.isEmpty()) return;
        int value = Math.max(KeyEvent.KEYCODE_UNKNOWN, keyCode);
        if (ModIds.ZOOM.equals(modId)) setZoomKeybind(value);
        else if (ModIds.AUTO_SPRINT.equals(modId)) setAutoSprintKeybind(value);
        else prefs.edit().putInt(KEY_INBUILT_KEYBIND_PREFIX + modId, value).apply();
    }

    private String sharedOverlaySettingsId(String modId) {
        if (modId != null && modId.startsWith(ModIds.HOTBAR_SLOT + ":")) return ModIds.HOTBAR_SLOT;
        return modId;
    }

    public boolean isOverlayShortcutVisible(String modId) {
        if (modId == null || modId.isEmpty()) return false;
        return prefs.getBoolean(KEY_OVERLAY_SHORTCUT_PREFIX + modId, false);
    }

    public void setOverlayShortcutVisible(String modId, boolean visible) {
        if (modId == null || modId.isEmpty()) return;
        prefs.edit().putBoolean(KEY_OVERLAY_SHORTCUT_PREFIX + modId, visible).apply();
        overlayVisibilityRevision.incrementAndGet();
    }

    public int getOverlayButtonSize(String modId) {
        String key = KEY_OVERLAY_BUTTON_SIZE_PREFIX + modId;
        if (prefs.contains(key)) return prefs.getInt(key, DEFAULT_OVERLAY_BUTTON_SIZE);
        String sharedId = sharedOverlaySettingsId(modId);
        if (sharedId != null && !sharedId.equals(modId)) {
            return prefs.getInt(KEY_OVERLAY_BUTTON_SIZE_PREFIX + sharedId, DEFAULT_OVERLAY_BUTTON_SIZE);
        }
        return DEFAULT_OVERLAY_BUTTON_SIZE;
    }

    public void setOverlayButtonSize(String modId, int sizeDp) {
        prefs.edit().putInt(KEY_OVERLAY_BUTTON_SIZE_PREFIX + modId, sizeDp).apply();
    }

    public int getOverlayOpacity(String modId) {
        String key = KEY_OVERLAY_OPACITY_PREFIX + modId;
        if (prefs.contains(key)) return prefs.getInt(key, DEFAULT_OVERLAY_OPACITY);
        String sharedId = sharedOverlaySettingsId(modId);
        if (sharedId != null && !sharedId.equals(modId)) {
            return prefs.getInt(KEY_OVERLAY_OPACITY_PREFIX + sharedId, DEFAULT_OVERLAY_OPACITY);
        }
        return DEFAULT_OVERLAY_OPACITY;
    }

    public void setOverlayOpacity(String modId, int opacity) {
        prefs.edit().putInt(KEY_OVERLAY_OPACITY_PREFIX + modId, Math.max(0, Math.min(100, opacity))).apply();
    }

    public void resetAllToDefaults() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.commit();
        overlayVisibilityRevision.incrementAndGet();
    }

    public boolean isModMenuEnabled() {
        return prefs.getBoolean(KEY_MOD_MENU_ENABLED, true);
    }

    public void setModMenuEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MOD_MENU_ENABLED, enabled).apply();
    }

    public boolean isLauncherHudHideEnabled() {
        return prefs.getBoolean(KEY_HIDE_LAUNCHER_HUD, true);
    }

    public void setLauncherHudHideEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HIDE_LAUNCHER_HUD, enabled).apply();
    }

    public boolean isNotificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
    }

    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply();
    }

    public int getModMenuOpacity() {
        return Math.max(MIN_MOD_MENU_OPACITY, prefs.getInt(KEY_MOD_MENU_OPACITY, DEFAULT_OVERLAY_OPACITY));
    }

    public void setModMenuOpacity(int opacity) {
        prefs.edit().putInt(KEY_MOD_MENU_OPACITY, Math.max(MIN_MOD_MENU_OPACITY, Math.min(100, opacity))).apply();
    }

    public int getModMenuButtonOpacity() {
        return prefs.getInt(KEY_MOD_MENU_BUTTON_OPACITY, DEFAULT_OVERLAY_OPACITY);
    }

    public void setModMenuButtonOpacity(int opacity) {
        prefs.edit().putInt(KEY_MOD_MENU_BUTTON_OPACITY, Math.max(0, Math.min(100, opacity))).apply();
    }

    public boolean isModMenuCompact() {
        return prefs.getBoolean(KEY_MOD_MENU_COMPACT, false);
    }

    public void setModMenuCompact(boolean compact) {
        prefs.edit().putBoolean(KEY_MOD_MENU_COMPACT, compact).apply();
    }

    public int getModMenuKeybind() {
        return prefs.getInt(KEY_MOD_MENU_KEYBIND, KeyEvent.KEYCODE_M);
    }

    public void setModMenuKeybind(int keyCode) {
        prefs.edit().putInt(KEY_MOD_MENU_KEYBIND, Math.max(KeyEvent.KEYCODE_UNKNOWN, keyCode)).apply();
    }

    public boolean isPauseMenuOnly() {
        return prefs.getBoolean(KEY_PAUSE_MENU_ONLY, false);
    }

    public void setPauseMenuOnly(boolean enabled) {
        prefs.edit().putBoolean(KEY_PAUSE_MENU_ONLY, enabled).apply();
    }

    public Set<String> getFavoriteModKeys() {
        return new HashSet<>(prefs.getStringSet(KEY_FAVORITE_MOD_KEYS, new HashSet<>()));
    }

    public void setModFavorite(String favoriteKey, boolean favorite) {
        if (favoriteKey == null || favoriteKey.isEmpty()) return;
        Set<String> favorites = getFavoriteModKeys();
        if (favorite) {
            favorites.add(favoriteKey);
        } else {
            favorites.remove(favoriteKey);
        }
        prefs.edit().putStringSet(KEY_FAVORITE_MOD_KEYS, favorites).apply();
    }

    public boolean resolveInbuiltModEnabled(String modId, boolean defaultEnabled) {
        if (modId == null || modId.isEmpty()) return defaultEnabled;
        return prefs.getBoolean(KEY_INBUILT_MOD_ENABLED_PREFIX + modId, defaultEnabled);
    }

    public void setInbuiltModEnabled(String modId, boolean enabled) {
        if (modId == null || modId.isEmpty()) return;
        prefs.edit().putBoolean(KEY_INBUILT_MOD_ENABLED_PREFIX + modId, enabled).apply();
    }

    public boolean resolveExternalModuleEnabled(String moduleId, boolean defaultEnabled) {
        if (moduleId == null || moduleId.isEmpty()) return defaultEnabled;
        String key = KEY_EXTERNAL_MODULE_ENABLED_PREFIX + moduleId;
        if (!prefs.contains(key)) {
            prefs.edit().putBoolean(key, defaultEnabled).apply();
            return defaultEnabled;
        }
        return prefs.getBoolean(key, defaultEnabled);
    }

    public void setExternalModuleEnabled(String moduleId, boolean enabled) {
        if (moduleId == null || moduleId.isEmpty()) return;
        prefs.edit().putBoolean(KEY_EXTERNAL_MODULE_ENABLED_PREFIX + moduleId, enabled).apply();
    }

    public int getZoomLevel() {
        try {
            return prefs.getInt(KEY_ZOOM_LEVEL, DEFAULT_ZOOM_LEVEL);
        } catch (ClassCastException e) {
            prefs.edit().remove(KEY_ZOOM_LEVEL).apply();
            return DEFAULT_ZOOM_LEVEL;
        }
    }

    public void setZoomLevel(int level) {
        prefs.edit().putInt(KEY_ZOOM_LEVEL, Math.max(-20, Math.min(100, level))).apply();
    }

    public int getZoomKeybind() {
        return prefs.getInt(KEY_ZOOM_KEYBIND, KeyEvent.KEYCODE_C);
    }

    public void setZoomKeybind(int keyCode) {
        prefs.edit().putInt(KEY_ZOOM_KEYBIND, keyCode).apply();
    }

    public int getZoomTransitionDuration() {
        return prefs.getInt(KEY_ZOOM_TRANSITION_DURATION, DEFAULT_ZOOM_TRANSITION_DURATION);
    }

    public void setZoomTransitionDuration(int duration) {
        prefs.edit().putInt(KEY_ZOOM_TRANSITION_DURATION, Math.max(0, Math.min(1000, duration))).apply();
    }

    public int getCursorSensitivity() {
        return prefs.getInt(KEY_CURSOR_SENSITIVITY, DEFAULT_CURSOR_SENSITIVITY);
    }

    public void setCursorSensitivity(int sensitivity) {
        prefs.edit().putInt(KEY_CURSOR_SENSITIVITY, Math.max(10, Math.min(300, sensitivity))).apply();
    }

    public int getOverlayPositionX(String modId, int defaultX) {
        return prefs.getInt(KEY_OVERLAY_POSITION_X_PREFIX + modId, defaultX);
    }

    public int getOverlayPositionY(String modId, int defaultY) {
        return prefs.getInt(KEY_OVERLAY_POSITION_Y_PREFIX + modId, defaultY);
    }

    public void setOverlayPosition(String modId, int x, int y) {
        prefs.edit()
            .putInt(KEY_OVERLAY_POSITION_X_PREFIX + modId, x)
            .putInt(KEY_OVERLAY_POSITION_Y_PREFIX + modId, y)
            .apply();
    }

    public boolean isOverlayLocked(String modId) {
        // Position locking was removed from the UI: overlays are always
        // draggable, even if an old preference is still present.
        return false;
    }

    public void setOverlayLocked(String modId, boolean locked) {
        prefs.edit().putBoolean(KEY_OVERLAY_LOCK_PREFIX + sharedOverlaySettingsId(modId), locked).apply();
    }

    public boolean isOverlayShowEverywhere(String modId) {
        // "Show everywhere" was removed from the UI.
        return false;
    }

    public void setOverlayShowEverywhere(String modId, boolean showEverywhere) {
        prefs.edit().putBoolean(KEY_OVERLAY_SHOW_EVERYWHERE_PREFIX + sharedOverlaySettingsId(modId), showEverywhere).apply();
    }

    public void clearOverlaySettings(String modId) {
        if (modId == null || modId.isEmpty()) return;
        prefs.edit()
                .remove(KEY_OVERLAY_BUTTON_SIZE_PREFIX + modId)
                .remove(KEY_OVERLAY_OPACITY_PREFIX + modId)
                .remove(KEY_OVERLAY_SHORTCUT_PREFIX + modId)
                .remove(KEY_OVERLAY_POSITION_X_PREFIX + modId)
                .remove(KEY_OVERLAY_POSITION_Y_PREFIX + modId)
                .remove(KEY_OVERLAY_LOCK_PREFIX + modId)
                .remove(KEY_OVERLAY_SHOW_EVERYWHERE_PREFIX + modId)
                .apply();
        overlayVisibilityRevision.incrementAndGet();
    }

    public long getOverlayVisibilityRevision() {
        return overlayVisibilityRevision.get();
    }

    public int getGyroSensitivityMultiplier() {
        return prefs.getInt(KEY_GYRO_SENSITIVITY_MULTIPLIER, DEFAULT_GYRO_SENSITIVITY_MULTIPLIER);
    }

    public void setGyroSensitivityMultiplier(int multiplier) {
        prefs.edit().putInt(KEY_GYRO_SENSITIVITY_MULTIPLIER, Math.max(25, Math.min(5000, multiplier))).apply();
    }

    public int getGyroSensitivityX() {
        return prefs.getInt(KEY_GYRO_SENSITIVITY_X, DEFAULT_GYRO_SENSITIVITY);
    }

    public void setGyroSensitivityX(int sensitivity) {
        prefs.edit().putInt(KEY_GYRO_SENSITIVITY_X, Math.max(10, Math.min(300, sensitivity))).apply();
    }

    public int getGyroSensitivityY() {
        return prefs.getInt(KEY_GYRO_SENSITIVITY_Y, DEFAULT_GYRO_SENSITIVITY);
    }

    public void setGyroSensitivityY(int sensitivity) {
        prefs.edit().putInt(KEY_GYRO_SENSITIVITY_Y, Math.max(10, Math.min(300, sensitivity))).apply();
    }

    public boolean isGyroInvertX() {
        return prefs.getBoolean(KEY_GYRO_INVERT_X, false);
    }

    public void setGyroInvertX(boolean invert) {
        prefs.edit().putBoolean(KEY_GYRO_INVERT_X, invert).apply();
    }

    public boolean isGyroInvertY() {
        return prefs.getBoolean(KEY_GYRO_INVERT_Y, false);
    }

    public void setGyroInvertY(boolean invert) {
        prefs.edit().putBoolean(KEY_GYRO_INVERT_Y, invert).apply();
    }

    public int getGyroDeadzone() {
        return prefs.getInt(KEY_GYRO_DEADZONE, DEFAULT_GYRO_DEADZONE);
    }

    public void setGyroDeadzone(int deadzone) {
        prefs.edit().putInt(KEY_GYRO_DEADZONE, Math.max(0, Math.min(100, deadzone))).apply();
    }
    public boolean isHotbarItemIconsEnabled() {
        return prefs.getBoolean(KEY_HOTBAR_ITEM_ICONS, false);
    }

    public void setHotbarItemIconsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HOTBAR_ITEM_ICONS, enabled).apply();
    }

    public boolean isHotbarItemCountsEnabled() {
        return prefs.getBoolean(KEY_HOTBAR_ITEM_COUNTS, false);
    }

    public void setHotbarItemCountsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HOTBAR_ITEM_COUNTS, enabled).apply();
    }

    public boolean isHotbarSlotEnabled(int slot) {
        if (slot < 1 || slot > 9) return false;
        return prefs.getBoolean(KEY_HOTBAR_SLOT_ENABLED_PREFIX + slot, true);
    }

    public void setHotbarSlotEnabled(int slot, boolean enabled) {
        if (slot < 1 || slot > 9) return;
        prefs.edit().putBoolean(KEY_HOTBAR_SLOT_ENABLED_PREFIX + slot, enabled).apply();
    }

}
