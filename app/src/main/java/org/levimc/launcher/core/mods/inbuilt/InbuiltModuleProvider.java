package org.levimc.launcher.core.mods.inbuilt;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;
import org.levimc.launcher.core.mods.inbuilt.nativemod.AimAssistMod;
import org.levimc.launcher.core.mods.inbuilt.nativemod.EspMod;
import org.levimc.launcher.core.mods.inbuilt.overlay.InbuiltOverlayManager;
import org.levimc.launcher.core.mods.inbuilt.overlay.MoreButtonsEditor;

import java.util.ArrayList;
import java.util.List;

public final class InbuiltModuleProvider {
    private static final String GROUP_ID = "inbuilt";
    private static final String MOD_ID = "inbuilt";

    private static final String CFG_OVERLAY_SIZE = "overlay_size";
    private static final String CFG_OVERLAY_OPACITY = "overlay_opacity";
    private static final String CFG_OVERLAY_SHORTCUT = "overlay_shortcut";
    private static final String CFG_HIDE_LAUNCHER_HUD = "hide_launcher_hud";
    private static final String CFG_ACTIVATION_KEYBIND = "activation_keybind";
    private static final String CFG_EDIT_CONTROLS = "edit_controls";
    private static final String CFG_AUTO_SPRINT_KEYBIND = "auto_sprint_keybind";
    private static final String CFG_SPEED_MULTIPLIER = "speed_multiplier";
    private static final String CFG_SPEED_S1_ENABLED = "speed_s1_enabled";
    private static final String CFG_SPEED_MODE = "speed_mode";
    private static final String CFG_SPEED_MOTION_BOOST = "speed_motion_boost";
    private static final String CFG_SPEED_MAX_SPEED = "speed_max_speed";
    private static final String CFG_SPEED_GAP_MAX = "speed_gap_max";
    private static final String CFG_SPEED_RESET_INTERVAL = "speed_reset_interval";
    private static final String CFG_SPEED_MAX_RESET_TICKS = "speed_max_reset_ticks";
    private static final String CFG_SPEED_HOP_ENABLED = "speed_hop_enabled";
    private static final String CFG_SPEED_HOP_HEIGHT = "speed_hop_height";
    private static final String CFG_SPEED_HOP_INTERVAL = "speed_hop_interval";
    private static final String CFG_SPEED_MASK_Y = "speed_mask_y";
    private static final String CFG_SPEED_PHANTOM_HOLD = "speed_phantom_hold";
    private static final String CFG_SPEED_FAKE_JUMP_TICKS = "speed_fake_jump_ticks";
    private static final String CFG_SPEED_CANCEL_CORRECTIONS = "speed_cancel_corrections";
    private static final String CFG_SPEED_PAUSE_ON_FALL = "speed_pause_on_fall";
    private static final String CFG_SPEED_PAUSE_IF_SNEAKING = "speed_pause_if_sneaking";
    private static final String CFG_SPEED_PAUSE_IF_NOT_MOVING = "speed_pause_if_not_moving";
    private static final String CFG_SPEED_AUTO_SPRINT = "speed_auto_sprint";
    private static final String CFG_SPEED_DEBUG = "speed_debug";
    private static final String CFG_FLY_MODE = "fly_mode";
    private static final String CFG_FLY_SPEED = "fly_speed";
    private static final String CFG_FLY_UP = "fly_up";
    private static final String CFG_FLY_DOWN = "fly_down";
    private static final String CFG_FLY_CANCEL_CORRECTIONS = "fly_cancel_corrections";
    private static final String CFG_JESUS_MODE = "jesus_mode";
    private static final String CFG_JESUS_DEPTH = "jesus_depth";
    private static final String CFG_SCAFFOLD_MODE = "scaffold_mode";
    private static final String CFG_SCAFFOLD_PITCH = "scaffold_pitch";
    private static final String CFG_SCAFFOLD_INTERVAL = "scaffold_interval";
    private static final String CFG_SCAFFOLD_ROTATE = "scaffold_rotate";
    private static final String CFG_AUTO_JUMP_HEIGHT = "auto_jump_height";
    private static final String CFG_AUTO_JUMP_MODE = "auto_jump_mode";
    private static final String CFG_MOVEMENT_DEBUG = "movement_debug";
    private static final String CFG_AIM_ASSIST_RANGE = "aim_assist_range";
    private static final String CFG_AIM_ASSIST_STRENGTH = "aim_assist_strength";
    private static final String CFG_AIM_ASSIST_LEGIT = "aim_assist_legit";
    private static final String CFG_AIM_ASSIST_FOV = "aim_assist_fov";
    private static final String CFG_AIM_ASSIST_WALL_CHECK = "aim_assist_wall_check";
    private static final String CFG_AIM_ASSIST_IGNORE_INVISIBLE = "aim_assist_ignore_invisible";
    private static final String CFG_AIM_ASSIST_NEURAL = "aim_assist_neural";
    private static final String CFG_AIM_ASSIST_KILLAURA = "aim_assist_killaura";
    private static final String CFG_KILLAURA_RANGE = "killaura_range";
    private static final String CFG_KILLAURA_CONE = "killaura_cone";
    private static final String CFG_KILLAURA_REACTION = "killaura_reaction";
    private static final String CFG_AIM_ASSIST_ROT_RECORDING = "aim_assist_rot_recording";
    private static final String CFG_AIM_ASSIST_TARGET_PLAYERS = "aim_assist_target_players";
    private static final String CFG_AIM_ASSIST_TARGET_MOBS = "aim_assist_target_mobs";
    private static final String CFG_AIM_ASSIST_BODY_PART = "aim_assist_body_part";
    private static final String CFG_AIM_ASSIST_MAX_SPEED = "aim_assist_max_speed";
    private static final String CFG_AIM_ASSIST_SMOOTHING = "aim_assist_smoothing";
    private static final String CFG_AIM_ASSIST_ACCEL = "aim_assist_accel";
    private static final String CFG_AIM_ASSIST_DEADZONE = "aim_assist_deadzone";
    private static final String CFG_AIM_ASSIST_FULL_RANGE = "aim_assist_full_range";
    private static final String CFG_AIM_ASSIST_STICKY = "aim_assist_sticky";
    private static final String CFG_AIM_ASSIST_GRACE = "aim_assist_grace";
    private static final String CFG_AIM_ASSIST_PREFER_CLOSEST = "aim_assist_prefer_closest";
    private static final String CFG_AIM_ASSIST_TARGET_LOCK = "aim_assist_target_lock";
    private static final String CFG_AIM_ASSIST_FAST_AIM = "aim_assist_fast_aim";
    private static final String CFG_AIM_ASSIST_FAST_AIM_TIME = "aim_assist_fast_aim_time";
    private static final String CFG_AIM_ASSIST_FAST_AIM_MAX_SPEED = "aim_assist_fast_aim_max_speed";
    private static final String CFG_AIM_ASSIST_ANTI_BOT = "aim_assist_anti_bot";
    private static final String CFG_AIM_ASSIST_RANDOM_POINT = "aim_assist_random_point";
    private static final String CFG_TRIGGER_BOT_ANTI_BOT = "trigger_bot_anti_bot";
    private static final String CFG_TRIGGER_BOT_RANDOM_POINT = "trigger_bot_random_point";
    private static final String CFG_ESP_SHOW_PLAYERS = "esp_show_players";
    private static final String CFG_ESP_SHOW_MOBS = "esp_show_mobs";
    private static final String CFG_ESP_RANGE = "esp_range";
    private static final String CFG_ESP_BOX_STYLE = "esp_box_style";
    private static final String CFG_ESP_PLAYER_COLOR = "esp_player_color";
    private static final String CFG_ESP_MOB_COLOR = "esp_mob_color";
    private static final String CFG_ESP_ARMOR_COLORS = "esp_armor_colors";
    private static final String CFG_ESP_ARMORED_COLOR = "esp_armored_color";
    private static final String CFG_NAME_TAG_STYLE = "name_tag_style";
    private static final String CFG_NAME_TAG_COLOR = "name_tag_color";
    private static final String CFG_NAME_TAG_SIZE = "name_tag_size";
    private static final String CFG_NAME_TAG_ARMOR_SIZE = "name_tag_armor_size";
    private static final String CFG_NAME_TAG_BLUR = "name_tag_blur";
    private static final String CFG_NAME_TAG_OUTLINE_COLOR = "name_tag_outline_color";
    private static final String CFG_NAME_TAG_SHOW_ARMOR = "name_tag_show_armor";
    private static final String CFG_NAME_TAG_SHOW_OFFHAND = "name_tag_show_offhand";
    private static final String CFG_SWING_STYLE = "swing_style";
    private static final String CFG_SWING_SPEED = "swing_speed";
    private static final String CFG_SWING_AMPLITUDE = "swing_amplitude";
    private static final String CFG_FRIEND_NAMES = "friend_names";
    private static final String CFG_FRIEND_EDIT = "friend_edit";
    private static final String CFG_TARGET_ESP_STYLE = "target_esp_style";
    private static final String CFG_TARGET_ESP_COLOR = "target_esp_color";
    private static final String CFG_TARGET_ESP_HOLD = "target_esp_hold_ms";
    private static final String CFG_TRIGGER_BOT_DELAY = "trigger_bot_delay";
    private static final String CFG_TRIGGER_BOT_REACTION_JITTER = "trigger_bot_reaction_jitter";
    private static final String CFG_TRIGGER_BOT_RANGE = "trigger_bot_range";
    private static final String CFG_TRIGGER_BOT_TOLERANCE = "trigger_bot_tolerance";
    private static final String CFG_TRIGGER_BOT_TARGET_PLAYERS = "trigger_bot_target_players";
    private static final String CFG_TRIGGER_BOT_TARGET_MOBS = "trigger_bot_target_mobs";
    private static final String CFG_TRIGGER_BOT_HUMANIZATION = "trigger_bot_humanization";
    private static final String CFG_TRIGGER_BOT_INTERVAL = "trigger_bot_interval";
    private static final String CFG_TRIGGER_BOT_INTERVAL_JITTER = "trigger_bot_interval_jitter";
    private static final String CFG_TRIGGER_BOT_SKIP_CHANCE = "trigger_bot_skip_chance";
    private static final String CFG_TRIGGER_BOT_ONLY_CRIT = "trigger_bot_only_crit";
    private static final String CFG_CURSOR_SENSITIVITY = "cursor_sensitivity";
    private static final String CFG_ZOOM_LEVEL = "zoom_level";
    private static final String CFG_ZOOM_TRANSITION = "zoom_transition";
    private static final String CFG_ZOOM_KEYBIND = "zoom_keybind";
    private static final String CFG_GYRO_SENSITIVITY_MULTIPLIER = "gyro_sensitivity_multiplier";
    private static final String CFG_GYRO_SENSITIVITY_X = "gyro_sensitivity_x";
    private static final String CFG_GYRO_SENSITIVITY_Y = "gyro_sensitivity_y";
    private static final String CFG_GYRO_INVERT_X = "gyro_invert_x";
    private static final String CFG_GYRO_INVERT_Y = "gyro_invert_y";
    private static final String CFG_GYRO_DEADZONE = "gyro_deadzone";
    private static final String CFG_HOTBAR_ITEM_ICONS = "hotbar_item_icons";
    private static final String CFG_HOTBAR_ITEM_COUNTS = "hotbar_item_counts";
    private static final String CFG_HOTBAR_SLOT_PREFIX = "hotbar_slot_";
    private static final String CFG_HOTBAR_SLOT_ENABLED = "enabled";
    private static final String CFG_HOTBAR_SLOT_SIZE = "size";
    private static final String CFG_HOTBAR_SLOT_OPACITY = "opacity";

    private InbuiltModuleProvider() {
    }

    public static List<UnifiedMod> load(Activity activity) {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
        String groupName = activity.getString(R.string.mod_menu_group_inbuilt);
        List<UnifiedMod> mods = new ArrayList<>();

        mods.add(create(activity, manager, overlayManager, ModIds.AUTO_SPRINT,
                R.string.inbuilt_mod_autosprint, R.string.inbuilt_mod_autosprint_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.SPEED,
                R.string.inbuilt_mod_speed, R.string.inbuilt_mod_speed_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.FLY,
                R.string.inbuilt_mod_fly, R.string.inbuilt_mod_fly_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.JESUS,
                R.string.inbuilt_mod_jesus, R.string.inbuilt_mod_jesus_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.SCAFFOLD,
                R.string.inbuilt_mod_scaffold, R.string.inbuilt_mod_scaffold_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.AUTO_JUMP,
                R.string.inbuilt_mod_autojump, R.string.inbuilt_mod_autojump_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.AIM_ASSIST,
                R.string.inbuilt_mod_aim_assist, R.string.inbuilt_mod_aim_assist_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.KILLAURA,
                R.string.inbuilt_mod_killaura, R.string.inbuilt_mod_killaura_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.ESP,
                R.string.inbuilt_mod_esp, R.string.inbuilt_mod_esp_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.TRIGGER_BOT,
                R.string.inbuilt_mod_trigger_bot, R.string.inbuilt_mod_trigger_bot_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.SWING,
                R.string.inbuilt_mod_swing, R.string.inbuilt_mod_swing_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.TARGET_ESP,
                R.string.inbuilt_mod_target_esp, R.string.inbuilt_mod_target_esp_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.NAME_TAGS,
                R.string.inbuilt_mod_name_tags, R.string.inbuilt_mod_name_tags_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.FRIEND,
                R.string.inbuilt_mod_friend, R.string.inbuilt_mod_friend_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.FULLBRIGHT,
                R.string.inbuilt_mod_fullbright, R.string.inbuilt_mod_fullbright_desc,
                groupName));

        return mods;
    }

    private static UnifiedMod create(Activity activity, InbuiltModManager manager,
                                     InbuiltOverlayManager overlayManager, String id,
                                     int nameRes, int descRes, String groupName) {
        boolean active = ModIds.FRIEND.equals(id)
                ? manager.resolveInbuiltModEnabled(id, false)
                : (overlayManager != null
                        ? overlayManager.isModActive(id)
                        : manager.resolveInbuiltModEnabled(id, false));
        boolean customConfig = ModIds.MORE_BUTTONS.equals(id);
        UnifiedMod result = new UnifiedMod(
                id,
                activity.getString(nameRes),
                activity.getString(descRes),
                MOD_ID,
                UnifiedMod.Source.INBUILT,
                active,
                createConfigs(activity, manager, id),
                customConfig,
                GROUP_ID,
                groupName,
                (mod, enabled) -> setEnabled(manager, mod, enabled),
                (mod, config, value) -> setConfig(activity, manager, mod, config, value),
                ModIds.MORE_BUTTONS.equals(id) ? mod -> MoreButtonsEditor.show(activity) : null
        );
        result.setLocalConfigSchema(createLocalConfigSchema(activity, result));
        return result;
    }

    private static RuntimeConfigSchema createLocalConfigSchema(Context context, UnifiedMod mod) {
        String modId = mod.getId();
        if (ModIds.CHICK_PET.equals(modId) || ModIds.MORE_BUTTONS.equals(modId)) return null;
        boolean hotbar = ModIds.HOTBAR_SLOT.equals(modId);
        boolean gyro = ModIds.GYRO.equals(modId);
        boolean zoom = ModIds.ZOOM.equals(modId);
        boolean cursor = ModIds.VIRTUAL_CURSOR.equals(modId);
        boolean pojav = ModIds.POJAV_CONTROLS.equals(modId);
        boolean aim = ModIds.AIM_ASSIST.equals(modId);
        boolean trigger = ModIds.TRIGGER_BOT.equals(modId);
        boolean esp = ModIds.ESP.equals(modId);
        boolean targetEsp = ModIds.TARGET_ESP.equals(modId);
        try {
            JSONArray categories = new JSONArray();
            JSONArray nodes = new JSONArray();
            String defaultCategory = "behavior";
            if (hotbar) {
                defaultCategory = "slots";
                categories.put(configCategory(context, "slots", R.string.mod_config_category_slots));
                categories.put(configCategory(context, "appearance", R.string.mod_config_category_appearance));
                nodes.put(configNode(mod, CFG_HOTBAR_ITEM_ICONS, "slots"));
                nodes.put(configNode(mod, CFG_HOTBAR_ITEM_COUNTS, "slots"));
                JSONArray slots = new JSONArray();
                for (int slot = 1; slot <= 9; slot++) {
                    String key = hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_ENABLED);
                    slots.put(new JSONObject().put("key", key).put("value", key)
                            .put("label", mod.findConfigEntry(key).displayName));
                    String section = "slot_" + slot;
                    nodes.put(new JSONObject().put("id", section).put("type", "section")
                            .put("category", "appearance").put("collapsible", true)
                            .put("title", context.getString(R.string.mod_config_hotbar_slot_section, slot)));
                    nodes.put(configNode(mod, hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_SIZE), "appearance")
                            .put("section", section));
                    nodes.put(configNode(mod, hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_OPACITY), "appearance")
                            .put("section", section));
                }
                nodes.put(new JSONObject().put("id", "visible_slots").put("type", "toggle_group")
                        .put("category", "slots").put("title", context.getString(R.string.mod_config_visible_slots))
                        .put("options", slots));
            } else if (aim) {
                defaultCategory = "aim_target";
                categories.put(configCategory(context, "aim_target",
                        R.string.mod_config_category_aim_target));
                categories.put(configCategory(context, "aim_motion",
                        R.string.mod_config_category_aim_motion));
                categories.put(configCategory(context, "appearance",
                        R.string.mod_config_category_appearance));
            } else if (trigger) {
                defaultCategory = "trigger_target";
                categories.put(configCategory(context, "trigger_target",
                        R.string.mod_config_category_aim_target));
                categories.put(configCategory(context, "trigger_timing",
                        R.string.mod_config_category_trigger_timing));
                categories.put(configCategory(context, "appearance",
                        R.string.mod_config_category_appearance));
            } else if (esp || targetEsp) {
                defaultCategory = "appearance";
                categories.put(configCategory(context, "appearance",
                        R.string.mod_config_category_appearance));
            } else if (!pojav) {
                if (gyro || cursor || zoom) {
                    defaultCategory = gyro || cursor ? "motion" : "zoom";
                    categories.put(configCategory(context, defaultCategory,
                            zoom ? R.string.inbuilt_mod_zoom : R.string.mod_config_category_motion));
                } else {
                    defaultCategory = "appearance";
                }
                categories.put(configCategory(context, "appearance", R.string.mod_config_category_appearance));
            }
            categories.put(configCategory(context, "behavior", R.string.mod_config_category_behavior));
            for (UnifiedMod.ConfigEntry config : mod.getConfigEntries()) {
                if (hotbar && (config.key.startsWith(CFG_HOTBAR_SLOT_PREFIX)
                        || CFG_HOTBAR_ITEM_ICONS.equals(config.key) || CFG_HOTBAR_ITEM_COUNTS.equals(config.key))) {
                    continue;
                }
                String category = "behavior";
                if (CFG_OVERLAY_SIZE.equals(config.key)
                        || CFG_OVERLAY_OPACITY.equals(config.key)
                        || CFG_OVERLAY_SHORTCUT.equals(config.key)) {
                    category = "appearance";
                } else if (aim && isAimMotionKey(config.key)) {
                    category = "aim_motion";
                } else if (aim && config.key.startsWith("aim_assist_")
                        && !CFG_AIM_ASSIST_LEGIT.equals(config.key)
                        && !CFG_AIM_ASSIST_WALL_CHECK.equals(config.key)) {
                    category = "aim_target";
                } else if (trigger && isTriggerTimingKey(config.key)) {
                    category = "trigger_timing";
                } else if (trigger && config.key.startsWith("trigger_bot_")) {
                    category = "trigger_target";
                } else if ((esp && (CFG_ESP_PLAYER_COLOR.equals(config.key)
                        || CFG_ESP_MOB_COLOR.equals(config.key)))
                        || (targetEsp && config.key.startsWith("target_esp_"))
                        || (ModIds.NAME_TAGS.equals(modId) && config.key.startsWith("name_tag_"))) {
                    category = "appearance";
                } else if (config.key.startsWith("gyro_") || CFG_CURSOR_SENSITIVITY.equals(config.key)) {
                    category = "motion";
                } else if (CFG_ZOOM_LEVEL.equals(config.key) || CFG_ZOOM_TRANSITION.equals(config.key)) {
                    category = "zoom";
                }
                nodes.put(configNode(mod, config.key, category));
            }
            return RuntimeConfigSchema.parse(new JSONObject().put("version", 2)
                    .put("default_category", defaultCategory)
                    .put("categories", categories).put("nodes", nodes).toString());
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to build inbuilt config schema", e);
        }
    }

    private static boolean isAimMotionKey(String key) {
        return CFG_AIM_ASSIST_FAST_AIM.equals(key)
                || CFG_AIM_ASSIST_FAST_AIM_TIME.equals(key)
                || CFG_AIM_ASSIST_FAST_AIM_MAX_SPEED.equals(key)
                || CFG_AIM_ASSIST_STRENGTH.equals(key)
                || CFG_AIM_ASSIST_MAX_SPEED.equals(key)
                || CFG_AIM_ASSIST_SMOOTHING.equals(key)
                || CFG_AIM_ASSIST_ACCEL.equals(key)
                || CFG_AIM_ASSIST_DEADZONE.equals(key)
                || CFG_AIM_ASSIST_FULL_RANGE.equals(key);
    }

    private static boolean isTriggerTimingKey(String key) {
        return CFG_TRIGGER_BOT_DELAY.equals(key)
                || CFG_TRIGGER_BOT_REACTION_JITTER.equals(key)
                || CFG_TRIGGER_BOT_INTERVAL.equals(key)
                || CFG_TRIGGER_BOT_INTERVAL_JITTER.equals(key)
                || CFG_TRIGGER_BOT_SKIP_CHANCE.equals(key);
    }

    private static JSONObject configCategory(Context context, String id, int titleRes) throws JSONException {
        return new JSONObject().put("id", id).put("title", context.getString(titleRes));
    }

    private static JSONObject configNode(UnifiedMod mod, String key, String category) throws JSONException {
        UnifiedMod.ConfigEntry config = mod.findConfigEntry(key);
        String type;
        switch (config.type) {
            case TOGGLE: type = "toggle"; break;
            case KEYBIND: type = "keybind"; break;
            case BUTTON: type = "button"; break;
            case RADIO: type = "choice"; break;
            case COLOR: type = "color"; break;
            case TEXT: type = "text"; break;
            case SLIDER_FLOAT: type = "slider_float"; break;
            default: type = "slider_int"; break;
        }
        JSONObject node = new JSONObject().put("id", key).put("key", key)
                .put("category", category).put("title", config.displayName)
                .put("type", type)
                .put("default_value", config.defaultValue)
                .put("min_value", config.minValue).put("max_value", config.maxValue);
        if (config.type == UnifiedMod.ConfigType.RADIO) {
            node.put("style", "radio");
            JSONArray options = new JSONArray();
            String[] labels = config.minValue.split(",");
            for (int i = 0; i < labels.length; i++) {
                options.put(new JSONObject().put("value", String.valueOf(i))
                        .put("label", labels[i].trim()));
            }
            node.put("options", options);
        }
        if (!config.dependsOn.isEmpty()) {
            node.put("enabled_when", new JSONArray().put(new JSONObject()
                    .put("key", config.dependsOn).put("op", "truthy")));
        }
        return node;
    }

    private static List<UnifiedMod.ConfigEntry> createConfigs(Context context,
                                                              InbuiltModManager manager,
                                                              String modId) {
        List<UnifiedMod.ConfigEntry> configs = new ArrayList<>();

        if (ModIds.KILLAURA.equals(modId)) {
            configs.add(config(CFG_KILLAURA_RANGE,
                    context.getString(R.string.mod_config_killaura_range),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "4", "3", "6",
                    String.valueOf(manager.getKillauraRange())));
            configs.add(config(CFG_KILLAURA_CONE,
                    context.getString(R.string.mod_config_killaura_cone),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "90", "20", "180",
                    String.valueOf(manager.getKillauraCone())));
            configs.add(config(CFG_KILLAURA_REACTION,
                    context.getString(R.string.mod_config_killaura_reaction),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "150", "0", "500",
                    String.valueOf(manager.getKillauraReaction())));
            configs.add(config(CFG_AIM_ASSIST_TARGET_PLAYERS,
                    context.getString(R.string.mod_config_aim_assist_target_players),
                    UnifiedMod.ConfigType.TOGGLE, "true", "", "",
                    String.valueOf(manager.isAimAssistTargetPlayers())));
            configs.add(config(CFG_AIM_ASSIST_TARGET_MOBS,
                    context.getString(R.string.mod_config_aim_assist_target_mobs),
                    UnifiedMod.ConfigType.TOGGLE, "true", "", "",
                    String.valueOf(manager.isAimAssistTargetMobs())));
            configs.add(config(CFG_AIM_ASSIST_RANDOM_POINT,
                    context.getString(R.string.mod_config_aim_assist_random_point),
                    UnifiedMod.ConfigType.TOGGLE, "true", "", "",
                    String.valueOf(manager.isAimAssistRandomPoint())));
            return configs;
        }
        if (ModIds.MORE_BUTTONS.equals(modId)) return configs;
        if (ModIds.POJAV_CONTROLS.equals(modId)) {
            configs.add(activationKeybind(context, manager, modId));
            configs.add(config(CFG_EDIT_CONTROLS,
                    context.getString(R.string.mod_config_edit_controls),
                    UnifiedMod.ConfigType.BUTTON, "", "", "", ""));
            return configs;
        }
        if (ModIds.AUTO_SPRINT.equals(modId) || ModIds.SPEED.equals(modId)
                || ModIds.FLY.equals(modId) || ModIds.JESUS.equals(modId)
                || ModIds.SCAFFOLD.equals(modId) || ModIds.AUTO_JUMP.equals(modId)
                || ModIds.AIM_ASSIST.equals(modId) || ModIds.ESP.equals(modId)
                || ModIds.FULLBRIGHT.equals(modId) || ModIds.TRIGGER_BOT.equals(modId)
                || ModIds.SWING.equals(modId) || ModIds.NAME_TAGS.equals(modId)
                || ModIds.TARGET_ESP.equals(modId)) {
            configs.add(config(CFG_OVERLAY_SHORTCUT,
                    context.getString(R.string.mod_config_overlay_shortcut),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isOverlayShortcutVisible(modId))));
        }

        if (ModIds.TOGGLE_HUD.equals(modId)) {
            configs.add(config(CFG_HIDE_LAUNCHER_HUD,
                    context.getString(R.string.mod_config_hide_launcher_hud),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isLauncherHudHideEnabled())));
        }

        if (ModIds.HOTBAR_SLOT.equals(modId)) {
            configs.add(config(CFG_HOTBAR_ITEM_ICONS,
                    context.getString(R.string.mod_config_hotbar_item_icons),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isHotbarItemIconsEnabled())));
            configs.add(config(CFG_HOTBAR_ITEM_COUNTS,
                    context.getString(R.string.mod_config_hotbar_item_counts),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isHotbarItemCountsEnabled())));
            for (int slot = 1; slot <= 9; slot++) {
                String enabledKey = hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_ENABLED);
                String overlayKey = ModIds.HOTBAR_SLOT + ":" + slot;
                configs.add(config(enabledKey,
                        context.getString(R.string.mod_config_hotbar_slot_enabled, slot),
                        UnifiedMod.ConfigType.TOGGLE,
                        "true", "", "",
                        String.valueOf(manager.isHotbarSlotEnabled(slot))));
                configs.add(config(hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_SIZE),
                        context.getString(R.string.mod_config_hotbar_slot_size, slot),
                        UnifiedMod.ConfigType.SLIDER_INT,
                        "56", "20", "100",
                        String.valueOf(manager.getOverlayButtonSize(overlayKey)), enabledKey));
                configs.add(config(hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_OPACITY),
                        context.getString(R.string.mod_config_hotbar_slot_opacity, slot),
                        UnifiedMod.ConfigType.SLIDER_INT,
                        "100", "0", "100",
                        String.valueOf(manager.getOverlayOpacity(overlayKey)), enabledKey));
            }
        }

        if (ModIds.AUTO_SPRINT.equals(modId)) {
            configs.add(config(CFG_AUTO_SPRINT_KEYBIND,
                    context.getString(R.string.mod_config_auto_sprint_keybind),
                    UnifiedMod.ConfigType.KEYBIND,
                    String.valueOf(manager.getDefaultInbuiltModKeybind(modId)), "", "",
                    String.valueOf(manager.getAutoSprintKeybind())));
        } else if (ModIds.SPEED.equals(modId)) {
            configs.add(config(CFG_SPEED_MULTIPLIER,
                    context.getString(R.string.mod_config_speed_multiplier),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "200", "100", "500",
                    String.valueOf(manager.getSpeedMultiplier())));
            configs.add(config(CFG_SPEED_S1_ENABLED,
                    context.getString(R.string.mod_config_speed_s1_enabled),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isSpeedS1Enabled())));
            configs.add(config(CFG_SPEED_MODE,
                    context.getString(R.string.mod_config_speed_mode),
                    UnifiedMod.ConfigType.RADIO,
                    "0",
                    context.getString(R.string.mod_config_speed_mode_air) + ","
                            + context.getString(R.string.mod_config_speed_mode_ground) + ","
                            + context.getString(R.string.mod_config_speed_mode_both),
                    "",
                    String.valueOf(manager.getSpeedMode())));
            configs.add(config(CFG_SPEED_MOTION_BOOST,
                    context.getString(R.string.mod_config_speed_motion_boost),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "4", "1", "200",
                    String.valueOf(manager.getSpeedMotionBoostRaw())));
            configs.add(config(CFG_SPEED_MAX_SPEED,
                    context.getString(R.string.mod_config_speed_max_speed),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "34", "28", "200",
                    String.valueOf(manager.getSpeedMaxSpeedRaw())));
            configs.add(config(CFG_SPEED_GAP_MAX,
                    context.getString(R.string.mod_config_speed_gap_max),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "0", "0", "300",
                    String.valueOf(manager.getSpeedGapMaxRaw())));
            configs.add(config(CFG_SPEED_RESET_INTERVAL,
                    context.getString(R.string.mod_config_speed_reset_interval),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "3500", "1000", "10000",
                    String.valueOf(manager.getSpeedResetInterval())));
            configs.add(config(CFG_SPEED_MAX_RESET_TICKS,
                    context.getString(R.string.mod_config_speed_max_reset_ticks),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "3", "2", "20",
                    String.valueOf(manager.getSpeedMaxResetTicks())));
            configs.add(config(CFG_SPEED_HOP_ENABLED,
                    context.getString(R.string.mod_config_speed_hop_enabled),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isSpeedHopEnabled())));
            configs.add(config(CFG_SPEED_HOP_HEIGHT,
                    context.getString(R.string.mod_config_speed_hop_height),
                    UnifiedMod.ConfigType.SLIDER_FLOAT,
                    "0.42", "0.02", "1.50",
                    String.format(java.util.Locale.US, "%.8f",
                            manager.getSpeedHopHeight())));
            configs.add(config(CFG_SPEED_HOP_INTERVAL,
                    context.getString(R.string.mod_config_speed_hop_interval),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "1", "0", "20",
                    String.valueOf(manager.getSpeedHopInterval())));
            configs.add(config(CFG_SPEED_MASK_Y,
                    context.getString(R.string.mod_config_speed_mask_y),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isSpeedMaskY())));
            configs.add(config(CFG_SPEED_PHANTOM_HOLD,
                    context.getString(R.string.mod_config_speed_phantom_hold),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "3", "1", "8",
                    String.valueOf(manager.getSpeedPhantomHoldTicks())));
            configs.add(config(CFG_SPEED_FAKE_JUMP_TICKS,
                    context.getString(R.string.mod_config_speed_fake_jump_ticks),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "4", "1", "12",
                    String.valueOf(manager.getSpeedFakeJumpTicks())));
            configs.add(config(CFG_SPEED_CANCEL_CORRECTIONS,
                    context.getString(R.string.mod_config_speed_cancel_corrections),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isSpeedCancelCorrections())));
            configs.add(config(CFG_SPEED_PAUSE_ON_FALL,
                    context.getString(R.string.mod_config_speed_pause_on_fall),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isSpeedPauseOnFall())));
            configs.add(config(CFG_SPEED_PAUSE_IF_SNEAKING,
                    context.getString(R.string.mod_config_speed_pause_if_sneaking),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isSpeedPauseIfSneaking())));
            configs.add(config(CFG_SPEED_PAUSE_IF_NOT_MOVING,
                    context.getString(R.string.mod_config_speed_pause_if_not_moving),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isSpeedPauseIfNotMoving())));
            configs.add(config(CFG_SPEED_AUTO_SPRINT,
                    context.getString(R.string.mod_config_speed_auto_sprint),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isSpeedAutoSprint())));
            configs.add(config(CFG_SPEED_DEBUG,
                    context.getString(R.string.mod_config_speed_debug),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isSpeedDebug())));
        } else if (ModIds.FLY.equals(modId)) {
            configs.add(config(CFG_FLY_MODE,
                    context.getString(R.string.mod_config_fly_mode),
                    UnifiedMod.ConfigType.RADIO,
                    "0",
                    context.getString(R.string.mod_config_fly_mode_normal) + ","
                            + context.getString(R.string.mod_config_fly_mode_bypass),
                    "",
                    String.valueOf(manager.getFlyMode())));
            configs.add(config(CFG_FLY_SPEED,
                    context.getString(R.string.mod_config_fly_speed),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "100", "10", "500",
                    String.valueOf(manager.getFlySpeedRaw())));
            configs.add(config(CFG_FLY_UP,
                    context.getString(R.string.mod_config_fly_up),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "42", "5", "150",
                    String.valueOf(manager.getFlyUpRaw())));
            configs.add(config(CFG_FLY_DOWN,
                    context.getString(R.string.mod_config_fly_down),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "42", "5", "150",
                    String.valueOf(manager.getFlyDownRaw())));
            configs.add(config(CFG_FLY_CANCEL_CORRECTIONS,
                    context.getString(R.string.mod_config_fly_cancel_corrections),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isFlyCancelCorrections())));
        } else if (ModIds.JESUS.equals(modId)) {
            configs.add(config(CFG_JESUS_MODE,
                    context.getString(R.string.mod_config_jesus_mode),
                    UnifiedMod.ConfigType.RADIO,
                    "0",
                    context.getString(R.string.mod_config_jesus_mode_surface) + ","
                            + context.getString(R.string.mod_config_jesus_mode_hover),
                    "",
                    String.valueOf(manager.getJesusMode())));
            configs.add(config(CFG_JESUS_DEPTH,
                    context.getString(R.string.mod_config_jesus_depth),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "0", "0", "100",
                    String.valueOf(manager.getJesusDepthRaw())));
        } else if (ModIds.SCAFFOLD.equals(modId)) {
            configs.add(config(CFG_SCAFFOLD_MODE,
                    context.getString(R.string.mod_config_scaffold_mode),
                    UnifiedMod.ConfigType.RADIO,
                    "0",
                    context.getString(R.string.mod_config_scaffold_mode_below) + ","
                            + context.getString(R.string.mod_config_scaffold_mode_ahead),
                    "",
                    String.valueOf(manager.getScaffoldMode())));
            configs.add(config(CFG_SCAFFOLD_PITCH,
                    context.getString(R.string.mod_config_scaffold_pitch),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "85", "0", "89",
                    String.valueOf(manager.getScaffoldPitch())));
            configs.add(config(CFG_SCAFFOLD_INTERVAL,
                    context.getString(R.string.mod_config_scaffold_interval),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "0", "0", "10",
                    String.valueOf(manager.getScaffoldInterval())));
            configs.add(config(CFG_SCAFFOLD_ROTATE,
                    context.getString(R.string.mod_config_scaffold_rotate),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isScaffoldRotate())));
        } else if (ModIds.AUTO_JUMP.equals(modId)) {
            configs.add(config(CFG_AUTO_JUMP_HEIGHT,
                    context.getString(R.string.mod_config_autojump_height),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "125", "20", "400",
                    String.valueOf(manager.getAutoJumpHeightRaw())));
            configs.add(config(CFG_AUTO_JUMP_MODE,
                    context.getString(R.string.mod_config_autojump_mode),
                    UnifiedMod.ConfigType.RADIO,
                    "0",
                    context.getString(R.string.mod_config_autojump_mode_manual) + ","
                            + context.getString(R.string.mod_config_autojump_mode_auto),
                    "",
                    String.valueOf(manager.getAutoJumpMode())));
            configs.add(config(CFG_MOVEMENT_DEBUG,
                    context.getString(R.string.mod_config_movement_debug),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isMovementDebug())));
        } else if (ModIds.AIM_ASSIST.equals(modId)) {
            configs.add(config(CFG_AIM_ASSIST_RANGE,
                    context.getString(R.string.mod_config_aim_assist_range),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "12", "3", "20",
                    String.valueOf(manager.getAimAssistRange())));
            configs.add(config(CFG_AIM_ASSIST_FOV,
                    context.getString(R.string.mod_config_aim_assist_fov),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "360", "30", "360",
                    String.valueOf(manager.getAimAssistFov())));
            configs.add(config(CFG_AIM_ASSIST_TARGET_PLAYERS,
                    context.getString(R.string.mod_config_aim_assist_target_players),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isAimAssistTargetPlayers())));
            configs.add(config(CFG_AIM_ASSIST_TARGET_MOBS,
                    context.getString(R.string.mod_config_aim_assist_target_mobs),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isAimAssistTargetMobs())));
            configs.add(config(CFG_AIM_ASSIST_PREFER_CLOSEST,
                    context.getString(R.string.mod_config_aim_assist_prefer_closest),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isAimAssistPreferClosest())));
            configs.add(config(CFG_AIM_ASSIST_TARGET_LOCK,
                    context.getString(R.string.mod_config_aim_assist_target_lock),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isAimAssistTargetLock())));
            configs.add(config(CFG_AIM_ASSIST_STICKY,
                    context.getString(R.string.mod_config_aim_assist_sticky),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "3", "0", "20",
                    String.valueOf(manager.getAimAssistSticky())));
            configs.add(config(CFG_AIM_ASSIST_GRACE,
                    context.getString(R.string.mod_config_aim_assist_grace),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "3", "0", "30",
                    String.valueOf(manager.getAimAssistGrace())));
            configs.add(config(CFG_AIM_ASSIST_FAST_AIM,
                    context.getString(R.string.mod_config_aim_assist_fast_aim),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isAimAssistFastAim())));
            configs.add(config(CFG_AIM_ASSIST_FAST_AIM_TIME,
                    context.getString(R.string.mod_config_aim_assist_fast_aim_time),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "35", "20", "200",
                    String.valueOf(manager.getAimAssistFastAimTime())));
            configs.add(config(CFG_AIM_ASSIST_FAST_AIM_MAX_SPEED,
                    context.getString(R.string.mod_config_aim_assist_fast_aim_speed),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "2600", "300", "4000",
                    String.valueOf(manager.getAimAssistFastAimMaxSpeed())));
            configs.add(config(CFG_AIM_ASSIST_ANTI_BOT,
                    context.getString(R.string.mod_config_aim_assist_anti_bot),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isAimAssistAntiBot())));
            configs.add(config(CFG_AIM_ASSIST_RANDOM_POINT,
                    context.getString(R.string.mod_config_aim_assist_random_point),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isAimAssistRandomPoint())));
            configs.add(config(CFG_AIM_ASSIST_MAX_SPEED,
                    context.getString(R.string.mod_config_aim_assist_max_speed),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "2200", "100", "3000",
                    String.valueOf(manager.getAimAssistMaxSpeed())));
            configs.add(config(CFG_AIM_ASSIST_SMOOTHING,
                    context.getString(R.string.mod_config_aim_assist_smoothing),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "60", "1", "100",
                    String.valueOf(manager.getAimAssistSmoothing())));
            configs.add(config(CFG_AIM_ASSIST_ACCEL,
                    context.getString(R.string.mod_config_aim_assist_accel),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "12000", "200", "20000",
                    String.valueOf(manager.getAimAssistAcceleration())));
            configs.add(config(CFG_AIM_ASSIST_DEADZONE,
                    context.getString(R.string.mod_config_aim_assist_deadzone),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "0", "0", "50",
                    String.valueOf(manager.getAimAssistDeadzone())));
            configs.add(config(CFG_AIM_ASSIST_FULL_RANGE,
                    context.getString(R.string.mod_config_aim_assist_full_range),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "60", "1", "90",
                    String.valueOf(manager.getAimAssistFullRange())));
            configs.add(config(CFG_AIM_ASSIST_STRENGTH,
                    context.getString(R.string.mod_config_aim_assist_strength),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "100", "0", "100",
                    String.valueOf(manager.getAimAssistStrength())));
            configs.add(config(CFG_AIM_ASSIST_IGNORE_INVISIBLE,
                    context.getString(R.string.mod_config_aim_assist_ignore_invisible),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isAimAssistIgnoreInvisible())));
            configs.add(config(CFG_AIM_ASSIST_WALL_CHECK,
                    context.getString(R.string.mod_config_aim_assist_wall_check),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isAimAssistWallCheck())));
            configs.add(config(CFG_AIM_ASSIST_LEGIT,
                    context.getString(R.string.mod_config_aim_assist_legit),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isAimAssistLegitMode())));
        } else if (ModIds.ESP.equals(modId)) {
            configs.add(config(CFG_ESP_RANGE,
                    context.getString(R.string.mod_config_esp_range),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "32", "4", "512",
                    String.valueOf(manager.getEspRange())));
            configs.add(config(CFG_ESP_BOX_STYLE,
                    context.getString(R.string.mod_config_esp_box_style),
                    UnifiedMod.ConfigType.RADIO,
                    "0",
                    context.getString(R.string.mod_config_esp_box_3d) + ","
                            + context.getString(R.string.mod_config_esp_box_2d) + ","
                            + context.getString(R.string.mod_config_esp_box_corners) + ","
                            + context.getString(R.string.mod_config_esp_box_glow) + ","
                            + context.getString(R.string.mod_config_esp_box_filled),
                    "",
                    String.valueOf(manager.getEspBoxStyle())));
            configs.add(config(CFG_ESP_SHOW_PLAYERS,
                    context.getString(R.string.mod_config_esp_players),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isEspShowPlayers())));
            configs.add(config(CFG_ESP_SHOW_MOBS,
                    context.getString(R.string.mod_config_esp_mobs),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isEspShowMobs())));
            configs.add(config(CFG_ESP_ARMOR_COLORS,
                    context.getString(R.string.mod_config_esp_armor_colors),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isEspArmorColors())));
            configs.add(config(CFG_ESP_PLAYER_COLOR,
                    context.getString(R.string.mod_config_esp_player_color),
                    UnifiedMod.ConfigType.COLOR,
                    formatColor(manager.getEspPlayerColor()), "", "",
                    formatColor(manager.getEspPlayerColor())));
            configs.add(config(CFG_ESP_MOB_COLOR,
                    context.getString(R.string.mod_config_esp_mob_color),
                    UnifiedMod.ConfigType.COLOR,
                    formatColor(manager.getEspMobColor()), "", "",
                    formatColor(manager.getEspMobColor())));
            configs.add(config(CFG_ESP_ARMORED_COLOR,
                    context.getString(R.string.mod_config_esp_armored_color),
                    UnifiedMod.ConfigType.COLOR,
                    formatColor(manager.getEspArmoredColor()), "", "",
                    formatColor(manager.getEspArmoredColor())));
        } else if (ModIds.SWING.equals(modId)) {
            configs.add(config(CFG_SWING_STYLE,
                    context.getString(R.string.mod_config_swing_style),
                    UnifiedMod.ConfigType.RADIO,
                    "1",
                    context.getString(R.string.mod_config_swing_default) + ","
                            + context.getString(R.string.mod_config_swing_slow) + ","
                            + context.getString(R.string.mod_config_swing_fast) + ","
                            + context.getString(R.string.mod_config_swing_heavy) + ","
                            + context.getString(R.string.mod_config_swing_snap) + ","
                            + context.getString(R.string.mod_config_swing_twitch),
                    "",
                    String.valueOf(manager.getSwingStyle())));
            configs.add(config(CFG_SWING_SPEED,
                    context.getString(R.string.mod_config_swing_speed),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "100", "50", "200",
                    String.valueOf(manager.getSwingSpeed())));
            configs.add(config(CFG_SWING_AMPLITUDE,
                    context.getString(R.string.mod_config_swing_amplitude),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "100", "0", "150",
                    String.valueOf(manager.getSwingAmplitude())));
        } else if (ModIds.NAME_TAGS.equals(modId)) {
            configs.add(config(CFG_NAME_TAG_STYLE,
                    context.getString(R.string.mod_config_name_tag_style),
                    UnifiedMod.ConfigType.RADIO,
                    "0",
                    context.getString(R.string.mod_config_name_tag_plate) + ","
                            + context.getString(R.string.mod_config_name_tag_glass) + ","
                            + context.getString(R.string.mod_config_name_tag_neon) + ","
                            + context.getString(R.string.mod_config_name_tag_minimal) + ","
                            + context.getString(R.string.mod_config_name_tag_bracket),
                    "",
                    String.valueOf(manager.getNameTagStyle())));
            configs.add(config(CFG_NAME_TAG_COLOR,
                    context.getString(R.string.mod_config_name_tag_color),
                    UnifiedMod.ConfigType.COLOR,
                    formatColor(manager.getNameTagColor()), "", "",
                    formatColor(manager.getNameTagColor())));
            configs.add(config(CFG_NAME_TAG_OUTLINE_COLOR,
                    context.getString(R.string.mod_config_name_tag_outline_color),
                    UnifiedMod.ConfigType.COLOR,
                    formatColor(manager.getNameTagOutlineColor()), "", "",
                    formatColor(manager.getNameTagOutlineColor())));
            configs.add(config(CFG_NAME_TAG_SIZE,
                    context.getString(R.string.mod_config_name_tag_size),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "11", "7", "24",
                    String.valueOf(manager.getNameTagSize())));
            configs.add(config(CFG_NAME_TAG_ARMOR_SIZE,
                    context.getString(R.string.mod_config_name_tag_armor_size),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "15", "8", "30",
                    String.valueOf(manager.getNameTagArmorSize())));
            configs.add(config(CFG_NAME_TAG_SHOW_ARMOR,
                    context.getString(R.string.mod_config_name_tag_show_armor),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isNameTagShowArmor())));
            configs.add(config(CFG_NAME_TAG_SHOW_OFFHAND,
                    context.getString(R.string.mod_config_name_tag_show_offhand),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isNameTagShowOffhand())));
        } else if (ModIds.TARGET_ESP.equals(modId)) {
            configs.add(config(CFG_TARGET_ESP_STYLE,
                    context.getString(R.string.mod_config_target_esp_style),
                    UnifiedMod.ConfigType.RADIO,
                    "0",
                    context.getString(R.string.mod_config_target_esp_brackets) + ","
                            + context.getString(R.string.mod_config_target_esp_orbital) + ","
                            + context.getString(R.string.mod_config_target_esp_scan),
                    "",
                    String.valueOf(manager.getTargetEspStyle())));
            configs.add(config(CFG_TARGET_ESP_COLOR,
                    context.getString(R.string.mod_config_target_esp_color),
                    UnifiedMod.ConfigType.COLOR,
                    formatColor(manager.getTargetEspColor()), "", "",
                    formatColor(manager.getTargetEspColor())));
            configs.add(config(CFG_TARGET_ESP_HOLD,
                    context.getString(R.string.mod_config_target_esp_hold),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "650", "200", "3000",
                    String.valueOf(manager.getTargetEspHoldMs())));
        } else if (ModIds.TRIGGER_BOT.equals(modId)) {
            configs.add(config(CFG_TRIGGER_BOT_DELAY,
                    context.getString(R.string.mod_config_trigger_bot_delay),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "0", "0", "400",
                    String.valueOf(manager.getTriggerBotDelay())));
            configs.add(config(CFG_TRIGGER_BOT_REACTION_JITTER,
                    context.getString(R.string.mod_config_trigger_bot_reaction_jitter),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "70", "0", "300",
                    String.valueOf(manager.getTriggerBotReactionJitter())));
            configs.add(config(CFG_TRIGGER_BOT_RANGE,
                    context.getString(R.string.mod_config_trigger_bot_range),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "30", "20", "50",
                    String.valueOf(manager.getTriggerBotRangeRaw())));
            configs.add(config(CFG_TRIGGER_BOT_TOLERANCE,
                    context.getString(R.string.mod_config_trigger_bot_tolerance),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "8", "0", "25",
                    String.valueOf(manager.getTriggerBotToleranceRaw())));
            configs.add(config(CFG_TRIGGER_BOT_TARGET_PLAYERS,
                    context.getString(R.string.mod_config_trigger_bot_target_players),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isTriggerBotTargetPlayers())));
            configs.add(config(CFG_TRIGGER_BOT_TARGET_MOBS,
                    context.getString(R.string.mod_config_trigger_bot_target_mobs),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isTriggerBotTargetMobs())));
            configs.add(config(CFG_TRIGGER_BOT_HUMANIZATION,
                    context.getString(R.string.mod_config_trigger_bot_humanization),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isTriggerBotHumanization())));
            configs.add(config(CFG_TRIGGER_BOT_INTERVAL,
                    context.getString(R.string.mod_config_trigger_bot_interval),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "220", "100", "1200",
                    String.valueOf(manager.getTriggerBotInterval())));
            configs.add(config(CFG_TRIGGER_BOT_INTERVAL_JITTER,
                    context.getString(R.string.mod_config_trigger_bot_interval_jitter),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "50", "0", "400",
                    String.valueOf(manager.getTriggerBotIntervalJitter())));
            configs.add(config(CFG_TRIGGER_BOT_SKIP_CHANCE,
                    context.getString(R.string.mod_config_trigger_bot_skip_chance),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "1", "0", "50",
                    String.valueOf(manager.getTriggerBotSkipChance())));
            configs.add(config(CFG_TRIGGER_BOT_ONLY_CRIT,
                    context.getString(R.string.mod_config_trigger_bot_only_crit),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isTriggerBotOnlyCrit())));
            configs.add(config(CFG_TRIGGER_BOT_ANTI_BOT,
                    context.getString(R.string.mod_config_trigger_bot_anti_bot),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isTriggerBotAntiBot())));
            configs.add(config(CFG_TRIGGER_BOT_RANDOM_POINT,
                    context.getString(R.string.mod_config_trigger_bot_random_point),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isTriggerBotRandomPoint())));
        } else if (ModIds.VIRTUAL_CURSOR.equals(modId)) {
            configs.add(config(CFG_CURSOR_SENSITIVITY,
                    context.getString(R.string.mod_config_cursor_sensitivity_percent),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "120", "10", "200",
                    String.valueOf(manager.getCursorSensitivity())));
        } else if (ModIds.ZOOM.equals(modId)) {
            configs.add(config(CFG_ZOOM_LEVEL,
                    context.getString(R.string.mod_config_zoom_level_percent),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "10", "-20", "100",
                    String.valueOf(manager.getZoomLevel())));
            configs.add(config(CFG_ZOOM_TRANSITION,
                    context.getString(R.string.mod_config_zoom_transition),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "150", "0", "1000",
                    String.valueOf(manager.getZoomTransitionDuration())));
            configs.add(config(CFG_ZOOM_KEYBIND,
                    context.getString(R.string.mod_config_zoom_keybind),
                    UnifiedMod.ConfigType.KEYBIND,
                    String.valueOf(manager.getDefaultInbuiltModKeybind(modId)), "", "",
                    String.valueOf(manager.getZoomKeybind())));
        } else if (ModIds.GYRO.equals(modId)) {
            configs.add(config(CFG_GYRO_SENSITIVITY_MULTIPLIER,
                    context.getString(R.string.mod_config_gyro_sensitivity_multiplier),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "100", "25", "5000",
                    String.valueOf(manager.getGyroSensitivityMultiplier())));
            configs.add(config(CFG_GYRO_SENSITIVITY_X,
                    context.getString(R.string.mod_config_gyro_horizontal_speed),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "100", "10", "300",
                    String.valueOf(manager.getGyroSensitivityX())));
            configs.add(config(CFG_GYRO_SENSITIVITY_Y,
                    context.getString(R.string.mod_config_gyro_vertical_speed),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "100", "10", "300",
                    String.valueOf(manager.getGyroSensitivityY())));
            configs.add(config(CFG_GYRO_INVERT_X,
                    context.getString(R.string.mod_config_gyro_invert_x),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isGyroInvertX())));
            configs.add(config(CFG_GYRO_INVERT_Y,
                    context.getString(R.string.mod_config_gyro_invert_y),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isGyroInvertY())));
            configs.add(config(CFG_GYRO_DEADZONE,
                    context.getString(R.string.mod_config_gyro_small_movement_filter),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "5", "0", "100",
                    String.valueOf(manager.getGyroDeadzone())));
        }
        if (ModIds.FRIEND.equals(modId)) {
            configs.add(config(CFG_FRIEND_NAMES,
                    context.getString(R.string.mod_config_friend_names),
                    UnifiedMod.ConfigType.TEXT, "", "", "",
                    manager.getFriendNames()));
            configs.add(config(CFG_FRIEND_EDIT,
                    context.getString(R.string.mod_config_friend_edit),
                    UnifiedMod.ConfigType.BUTTON, "", "", "",
                    ""));
        }
        if (!ModIds.CHICK_PET.equals(modId) && !ModIds.AUTO_SPRINT.equals(modId)
                && !ModIds.ZOOM.equals(modId) && !ModIds.FRIEND.equals(modId)) {
            configs.add(activationKeybind(context, manager, modId));
        }
        return configs;
    }

    private static void applyFriendNames(InbuiltModManager manager) {
        EspMod.setFriendNames(manager.isFriendModuleEnabled()
                ? manager.getFriendNames() : "");
    }

    private static UnifiedMod.ConfigEntry activationKeybind(Context context, InbuiltModManager manager, String modId) {
        return config(CFG_ACTIVATION_KEYBIND,
                context.getString(R.string.mod_config_activation_keybind),
                UnifiedMod.ConfigType.KEYBIND,
                String.valueOf(manager.getDefaultInbuiltModKeybind(modId)), "", "",
                String.valueOf(manager.getInbuiltModKeybind(modId)));
    }

    private static UnifiedMod.ConfigEntry config(String key, String displayName,
                                                 UnifiedMod.ConfigType type,
                                                 String defaultValue, String minValue,
                                                 String maxValue, String currentValue) {
        return config(key, displayName, type, defaultValue, minValue, maxValue, currentValue, "");
    }

    private static UnifiedMod.ConfigEntry config(String key, String displayName,
                                                 UnifiedMod.ConfigType type,
                                                 String defaultValue, String minValue,
                                                 String maxValue, String currentValue,
                                                 String dependsOn) {
        return new UnifiedMod.ConfigEntry(
                key, displayName, type, defaultValue, minValue, maxValue,
                currentValue, dependsOn
        );
    }

    private static String hotbarSlotConfigKey(int slot, String setting) {
        return CFG_HOTBAR_SLOT_PREFIX + slot + "_" + setting;
    }

    private static void setEnabled(InbuiltModManager manager, UnifiedMod mod, boolean enabled) {
        manager.setInbuiltModEnabled(mod.getId(), enabled);
        if (ModIds.FRIEND.equals(mod.getId())) applyFriendNames(manager);
        InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
        if (overlayManager != null) {
            overlayManager.handleModToggle(mod.getId(), enabled);
        }
    }

    private static void setConfig(android.app.Activity activity, InbuiltModManager manager,
                                  UnifiedMod mod, UnifiedMod.ConfigEntry config, String value) {
        if (ModIds.HOTBAR_SLOT.equals(mod.getId()) && setHotbarSlotConfig(manager, config.key, value)) return;
        switch (config.key) {
            case CFG_FRIEND_NAMES:
                manager.setFriendNames(value);
                applyFriendNames(manager);
                break;
            case CFG_FRIEND_EDIT:
                FriendEditor.show(activity, manager, mod, CFG_FRIEND_NAMES);
                break;
            case CFG_ACTIVATION_KEYBIND:
                manager.setInbuiltModKeybind(mod.getId(), parseInt(value, manager.getInbuiltModKeybind(mod.getId())));
                break;
            case CFG_EDIT_CONTROLS:
                InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
                if (overlayManager != null) overlayManager.openPojavControlsEditor();
                break;
            case CFG_OVERLAY_SIZE:
                manager.setOverlayButtonSize(mod.getId(), parseInt(value, manager.getOverlayButtonSize(mod.getId())));
                break;
            case CFG_OVERLAY_OPACITY:
                manager.setOverlayOpacity(mod.getId(), parseInt(value, manager.getOverlayOpacity(mod.getId())));
                break;
            case CFG_OVERLAY_SHORTCUT:
                manager.setOverlayShortcutVisible(mod.getId(), parseBoolean(value));
                {
                    InbuiltOverlayManager shortcutManager =
                            InbuiltOverlayManager.getInstance();
                    if (shortcutManager != null) {
                        shortcutManager.syncShortcutVisibility(mod.getId());
                    }
                }
                break;
            case CFG_HIDE_LAUNCHER_HUD:
                manager.setLauncherHudHideEnabled(parseBoolean(value));
                InbuiltOverlayManager hudManager = InbuiltOverlayManager.getInstance();
                if (hudManager != null) hudManager.refreshRuntimeVisibility();
                break;
            case CFG_AUTO_SPRINT_KEYBIND:
                manager.setAutoSprintKeybind(parseInt(value, manager.getAutoSprintKeybind()));
                break;
            case CFG_SPEED_MULTIPLIER:
                manager.setSpeedMultiplier(parseInt(value, manager.getSpeedMultiplier()));
                break;
            case CFG_SPEED_S1_ENABLED:
                manager.setSpeedS1Enabled(parseBoolean(value));
                break;
            case CFG_SPEED_MODE:
                manager.setSpeedMode(parseInt(value, manager.getSpeedMode()));
                break;
            case CFG_SPEED_MOTION_BOOST:
                manager.setSpeedMotionBoostRaw(parseInt(value, manager.getSpeedMotionBoostRaw()));
                break;
            case CFG_SPEED_MAX_SPEED:
                manager.setSpeedMaxSpeedRaw(parseInt(value, manager.getSpeedMaxSpeedRaw()));
                break;
            case CFG_SPEED_GAP_MAX:
                manager.setSpeedGapMaxRaw(parseInt(value, manager.getSpeedGapMaxRaw()));
                break;
            case CFG_SPEED_RESET_INTERVAL:
                manager.setSpeedResetInterval(parseInt(value, manager.getSpeedResetInterval()));
                break;
            case CFG_SPEED_MAX_RESET_TICKS:
                manager.setSpeedMaxResetTicks(parseInt(value, manager.getSpeedMaxResetTicks()));
                break;
            case CFG_SPEED_HOP_ENABLED:
                manager.setSpeedHopEnabled(parseBoolean(value));
                break;
            case CFG_SPEED_HOP_HEIGHT:
                try {
                    manager.setSpeedHopHeight(Float.parseFloat(value));
                } catch (NumberFormatException ignored) {
                }
                break;
            case CFG_SPEED_HOP_INTERVAL:
                manager.setSpeedHopInterval(parseInt(value, manager.getSpeedHopInterval()));
                break;
            case CFG_SPEED_MASK_Y:
                manager.setSpeedMaskY(parseBoolean(value));
                break;
            case CFG_SPEED_PHANTOM_HOLD:
                manager.setSpeedPhantomHoldTicks(parseInt(value, manager.getSpeedPhantomHoldTicks()));
                break;
            case CFG_SPEED_FAKE_JUMP_TICKS:
                manager.setSpeedFakeJumpTicks(parseInt(value, manager.getSpeedFakeJumpTicks()));
                break;
            case CFG_SPEED_CANCEL_CORRECTIONS:
                manager.setSpeedCancelCorrections(parseBoolean(value));
                break;
            case CFG_SPEED_PAUSE_ON_FALL:
                manager.setSpeedPauseOnFall(parseBoolean(value));
                break;
            case CFG_SPEED_PAUSE_IF_SNEAKING:
                manager.setSpeedPauseIfSneaking(parseBoolean(value));
                break;
            case CFG_SPEED_PAUSE_IF_NOT_MOVING:
                manager.setSpeedPauseIfNotMoving(parseBoolean(value));
                break;
            case CFG_SPEED_AUTO_SPRINT:
                manager.setSpeedAutoSprint(parseBoolean(value));
                break;
            case CFG_SPEED_DEBUG:
                manager.setSpeedDebug(parseBoolean(value));
                break;
            case CFG_FLY_MODE:
                manager.setFlyMode(parseInt(value, manager.getFlyMode()));
                break;
            case CFG_FLY_SPEED:
                manager.setFlySpeedRaw(parseInt(value, manager.getFlySpeedRaw()));
                break;
            case CFG_FLY_UP:
                manager.setFlyUpRaw(parseInt(value, manager.getFlyUpRaw()));
                break;
            case CFG_FLY_DOWN:
                manager.setFlyDownRaw(parseInt(value, manager.getFlyDownRaw()));
                break;
            case CFG_FLY_CANCEL_CORRECTIONS:
                manager.setFlyCancelCorrections(parseBoolean(value));
                break;
            case CFG_JESUS_MODE:
                manager.setJesusMode(parseInt(value, manager.getJesusMode()));
                break;
            case CFG_JESUS_DEPTH:
                manager.setJesusDepthRaw(parseInt(value, manager.getJesusDepthRaw()));
                break;
            case CFG_SCAFFOLD_MODE:
                manager.setScaffoldMode(parseInt(value, manager.getScaffoldMode()));
                break;
            case CFG_SCAFFOLD_PITCH:
                manager.setScaffoldPitch(parseInt(value, manager.getScaffoldPitch()));
                break;
            case CFG_SCAFFOLD_INTERVAL:
                manager.setScaffoldInterval(parseInt(value, manager.getScaffoldInterval()));
                break;
            case CFG_SCAFFOLD_ROTATE:
                manager.setScaffoldRotate(parseBoolean(value));
                break;
            case CFG_AUTO_JUMP_HEIGHT:
                manager.setAutoJumpHeightRaw(parseInt(value, manager.getAutoJumpHeightRaw()));
                break;
            case CFG_AUTO_JUMP_MODE:
                manager.setAutoJumpMode(parseInt(value, manager.getAutoJumpMode()));
                break;
            case CFG_MOVEMENT_DEBUG:
                manager.setMovementDebug(parseBoolean(value));
                break;
            case CFG_AIM_ASSIST_RANGE:
                manager.setAimAssistRange(parseInt(value, manager.getAimAssistRange()));
                break;
            case CFG_AIM_ASSIST_FOV:
                manager.setAimAssistFov(parseInt(value, manager.getAimAssistFov()));
                break;
            case CFG_AIM_ASSIST_STRENGTH:
                manager.setAimAssistStrength(parseInt(value, manager.getAimAssistStrength()));
                break;
            case CFG_AIM_ASSIST_WALL_CHECK:
                manager.setAimAssistWallCheck(parseBoolean(value));
                break;
            case CFG_AIM_ASSIST_IGNORE_INVISIBLE:
                manager.setAimAssistIgnoreInvisible(parseBoolean(value));
                break;
            case CFG_AIM_ASSIST_NEURAL:
                manager.setAimAssistNeuralRotation(parseBoolean(value));
                break;
            case CFG_AIM_ASSIST_ROT_RECORDING:
                manager.setAimAssistRotationRecording(parseBoolean(value));
                break;
            case CFG_AIM_ASSIST_KILLAURA:
                manager.setAimAssistKillaura(parseBoolean(value));
                break;
            case CFG_KILLAURA_RANGE:
                manager.setKillauraRange(parseInt(value, manager.getKillauraRange()));
                AimAssistMod.setRange(manager.getKillauraRange());
                break;
            case CFG_KILLAURA_CONE:
                manager.setKillauraCone(parseInt(value, manager.getKillauraCone()));
                AimAssistMod.setKillauraConeDeg(manager.getKillauraCone());
                break;
            case CFG_KILLAURA_REACTION:
                manager.setKillauraReaction(parseInt(value, manager.getKillauraReaction()));
                AimAssistMod.setKillauraReactionMs(manager.getKillauraReaction());
                break;
            case CFG_AIM_ASSIST_TARGET_PLAYERS:
                manager.setAimAssistTargetPlayers(parseBoolean(value));
                break;
            case CFG_AIM_ASSIST_TARGET_MOBS:
                manager.setAimAssistTargetMobs(parseBoolean(value));
                break;
            case CFG_AIM_ASSIST_BODY_PART:
                manager.setAimAssistBodyPart(parseInt(value, manager.getAimAssistBodyPart()));
                break;
            case CFG_AIM_ASSIST_MAX_SPEED:
                manager.setAimAssistMaxSpeed(parseInt(value, manager.getAimAssistMaxSpeed()));
                break;
            case CFG_AIM_ASSIST_SMOOTHING:
                manager.setAimAssistSmoothing(parseInt(value, manager.getAimAssistSmoothing()));
                break;
            case CFG_AIM_ASSIST_ACCEL:
                manager.setAimAssistAcceleration(parseInt(value, manager.getAimAssistAcceleration()));
                break;
            case CFG_AIM_ASSIST_DEADZONE:
                manager.setAimAssistDeadzone(parseInt(value, manager.getAimAssistDeadzone()));
                break;
            case CFG_AIM_ASSIST_FULL_RANGE:
                manager.setAimAssistFullRange(parseInt(value, manager.getAimAssistFullRange()));
                break;
            case CFG_AIM_ASSIST_STICKY:
                manager.setAimAssistSticky(parseInt(value, manager.getAimAssistSticky()));
                break;
            case CFG_AIM_ASSIST_GRACE:
                manager.setAimAssistGrace(parseInt(value, manager.getAimAssistGrace()));
                break;
            case CFG_AIM_ASSIST_PREFER_CLOSEST:
                manager.setAimAssistPreferClosest(parseBoolean(value));
                break;
            case CFG_AIM_ASSIST_TARGET_LOCK:
                manager.setAimAssistTargetLock(parseBoolean(value));
                break;
            case CFG_AIM_ASSIST_FAST_AIM:
                manager.setAimAssistFastAim(parseBoolean(value));
                break;
            case CFG_AIM_ASSIST_FAST_AIM_TIME:
                manager.setAimAssistFastAimTime(parseInt(value, manager.getAimAssistFastAimTime()));
                break;
            case CFG_AIM_ASSIST_FAST_AIM_MAX_SPEED:
                manager.setAimAssistFastAimMaxSpeed(parseInt(value, manager.getAimAssistFastAimMaxSpeed()));
                break;
            case CFG_AIM_ASSIST_ANTI_BOT:
                manager.setAimAssistAntiBot(parseBoolean(value));
                break;
            case CFG_AIM_ASSIST_RANDOM_POINT:
                manager.setAimAssistRandomPoint(parseBoolean(value));
                break;
            case CFG_AIM_ASSIST_LEGIT:
                manager.setAimAssistLegitMode(parseBoolean(value));
                break;
            case CFG_ESP_RANGE:
                manager.setEspRange(parseInt(value, manager.getEspRange()));
                break;
            case CFG_ESP_BOX_STYLE:
                manager.setEspBoxStyle(parseInt(value, manager.getEspBoxStyle()));
                EspMod.setEspBoxStyle(manager.getEspBoxStyle());
                break;
            case CFG_ESP_SHOW_PLAYERS:
                manager.setEspShowPlayers(parseBoolean(value));
                break;
            case CFG_ESP_SHOW_MOBS:
                manager.setEspShowMobs(parseBoolean(value));
                break;
            case CFG_ESP_ARMOR_COLORS:
                manager.setEspArmorColors(parseBoolean(value));
                EspMod.setArmorColors(parseBoolean(value));
                break;
            case CFG_ESP_ARMORED_COLOR:
                manager.setEspArmoredColor(parseColor(value, manager.getEspArmoredColor()));
                EspMod.setArmoredColor(manager.getEspArmoredColor());
                break;
            case CFG_ESP_PLAYER_COLOR:
                manager.setEspPlayerColor(parseColor(value, manager.getEspPlayerColor()));
                break;
            case CFG_ESP_MOB_COLOR:
                manager.setEspMobColor(parseColor(value, manager.getEspMobColor()));
                break;
            case CFG_NAME_TAG_STYLE:
                manager.setNameTagStyle(parseInt(value, manager.getNameTagStyle()));
                break;
            case CFG_NAME_TAG_OUTLINE_COLOR:
                manager.setNameTagOutlineColor(parseColor(value, manager.getNameTagOutlineColor()));
                break;
            case CFG_NAME_TAG_COLOR:
                manager.setNameTagColor(parseColor(value, manager.getNameTagColor()));
                break;
            case CFG_NAME_TAG_SIZE:
                manager.setNameTagSize(parseInt(value, manager.getNameTagSize()));
                break;
            case CFG_NAME_TAG_ARMOR_SIZE:
                manager.setNameTagArmorSize(parseInt(value,
                        manager.getNameTagArmorSize()));
                break;
            case CFG_NAME_TAG_SHOW_ARMOR:
                manager.setNameTagShowArmor(parseBoolean(value));
                break;
            case CFG_NAME_TAG_SHOW_OFFHAND:
                manager.setNameTagShowOffhand(parseBoolean(value));
                break;
            case CFG_SWING_STYLE:
                manager.setSwingStyle(parseInt(value, manager.getSwingStyle()));
                break;
            case CFG_SWING_SPEED:
                manager.setSwingSpeed(parseInt(value, manager.getSwingSpeed()));
                break;
            case CFG_SWING_AMPLITUDE:
                manager.setSwingAmplitude(parseInt(value, manager.getSwingAmplitude()));
                break;
            case CFG_TARGET_ESP_STYLE:
                manager.setTargetEspStyle(parseInt(value, manager.getTargetEspStyle()));
                break;
            case CFG_TARGET_ESP_COLOR:
                manager.setTargetEspColor(parseColor(value, manager.getTargetEspColor()));
                break;
            case CFG_TARGET_ESP_HOLD:
                manager.setTargetEspHoldMs(parseInt(value, manager.getTargetEspHoldMs()));
                break;
            case CFG_TRIGGER_BOT_DELAY:
                manager.setTriggerBotDelay(parseInt(value, manager.getTriggerBotDelay()));
                break;
            case CFG_TRIGGER_BOT_REACTION_JITTER:
                manager.setTriggerBotReactionJitter(parseInt(value, manager.getTriggerBotReactionJitter()));
                break;
            case CFG_TRIGGER_BOT_RANGE:
                manager.setTriggerBotRange(parseInt(value, manager.getTriggerBotRangeRaw()));
                break;
            case CFG_TRIGGER_BOT_TOLERANCE:
                manager.setTriggerBotTolerance(parseInt(value, manager.getTriggerBotToleranceRaw()));
                break;
            case CFG_TRIGGER_BOT_TARGET_PLAYERS:
                manager.setTriggerBotTargetPlayers(parseBoolean(value));
                break;
            case CFG_TRIGGER_BOT_TARGET_MOBS:
                manager.setTriggerBotTargetMobs(parseBoolean(value));
                break;
            case CFG_TRIGGER_BOT_HUMANIZATION:
                manager.setTriggerBotHumanization(parseBoolean(value));
                break;
            case CFG_TRIGGER_BOT_INTERVAL:
                manager.setTriggerBotInterval(parseInt(value, manager.getTriggerBotInterval()));
                break;
            case CFG_TRIGGER_BOT_INTERVAL_JITTER:
                manager.setTriggerBotIntervalJitter(parseInt(value, manager.getTriggerBotIntervalJitter()));
                break;
            case CFG_TRIGGER_BOT_SKIP_CHANCE:
                manager.setTriggerBotSkipChance(parseInt(value, manager.getTriggerBotSkipChance()));
                break;
            case CFG_TRIGGER_BOT_ONLY_CRIT:
                manager.setTriggerBotOnlyCrit(parseBoolean(value));
                break;
            case CFG_TRIGGER_BOT_ANTI_BOT:
                manager.setTriggerBotAntiBot(parseBoolean(value));
                break;
            case CFG_TRIGGER_BOT_RANDOM_POINT:
                manager.setTriggerBotRandomPoint(parseBoolean(value));
                break;
            case CFG_CURSOR_SENSITIVITY:
                manager.setCursorSensitivity(parseInt(value, manager.getCursorSensitivity()));
                break;
            case CFG_ZOOM_LEVEL:
                manager.setZoomLevel(parseInt(value, manager.getZoomLevel()));
                break;
            case CFG_ZOOM_TRANSITION:
                manager.setZoomTransitionDuration(parseInt(value, manager.getZoomTransitionDuration()));
                break;
            case CFG_ZOOM_KEYBIND:
                manager.setZoomKeybind(parseInt(value, manager.getZoomKeybind()));
                break;
            case CFG_GYRO_SENSITIVITY_MULTIPLIER:
                manager.setGyroSensitivityMultiplier(parseInt(value, manager.getGyroSensitivityMultiplier()));
                break;
            case CFG_GYRO_SENSITIVITY_X:
                manager.setGyroSensitivityX(parseInt(value, manager.getGyroSensitivityX()));
                break;
            case CFG_GYRO_SENSITIVITY_Y:
                manager.setGyroSensitivityY(parseInt(value, manager.getGyroSensitivityY()));
                break;
            case CFG_GYRO_INVERT_X:
                manager.setGyroInvertX(parseBoolean(value));
                break;
            case CFG_GYRO_INVERT_Y:
                manager.setGyroInvertY(parseBoolean(value));
                break;
            case CFG_GYRO_DEADZONE:
                manager.setGyroDeadzone(parseInt(value, manager.getGyroDeadzone()));
                break;
            case CFG_HOTBAR_ITEM_ICONS:
                manager.setHotbarItemIconsEnabled(parseBoolean(value));
                break;
            case CFG_HOTBAR_ITEM_COUNTS:
                manager.setHotbarItemCountsEnabled(parseBoolean(value));
                break;
            default:
                break;
        }

        // Push the new config to the live overlay/native (colors, model, etc.).
        // Without this the value only changed in prefs, so the game kept the
        // old color/style until the module was restarted.
        InbuiltOverlayManager changedManager = InbuiltOverlayManager.getInstance();
        if (changedManager != null) {
            changedManager.applyConfigurationChanges(mod.getId());
        }
    }

    private static boolean setHotbarSlotConfig(InbuiltModManager manager, String key, String value) {
        for (int slot = 1; slot <= 9; slot++) {
            String overlayKey = ModIds.HOTBAR_SLOT + ":" + slot;
            if (hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_ENABLED).equals(key)) {
                manager.setHotbarSlotEnabled(slot, parseBoolean(value));
                return true;
            }
            if (hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_SIZE).equals(key)) {
                manager.setOverlayButtonSize(overlayKey, parseInt(value, manager.getOverlayButtonSize(overlayKey)));
                return true;
            }
            if (hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_OPACITY).equals(key)) {
                manager.setOverlayOpacity(overlayKey, parseInt(value, manager.getOverlayOpacity(overlayKey)));
                return true;
            }
        }
        return false;
    }

    private static String formatColor(int argb) {
        return String.format(java.util.Locale.US, "#%08X", argb);
    }

    private static int parseColor(String value, int fallback) {
        try {
            return Color.parseColor(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }
}
