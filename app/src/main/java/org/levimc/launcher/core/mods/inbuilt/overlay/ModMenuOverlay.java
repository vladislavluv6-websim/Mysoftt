package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.ExternalModuleProvider;
import org.levimc.launcher.core.mods.inbuilt.InbuiltModuleProvider;
import org.levimc.launcher.core.mods.inbuilt.UnifiedMod;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ModMenuOverlay {
    private static final String TAG = "ModMenuOverlay";

    private enum ModuleFilter {
        ALL,
        FAVORITES,
        ENABLED,
        INBUILT,
        EXTERNAL
    }

    private enum MenuSection {
        MODULES,
        SETTINGS,
        CONFIG,
        HUD_EDITOR
    }

    private final Activity activity;
    private View overlayView;
    private WindowManager windowManager;
    private WindowManager.LayoutParams wmParams;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isShowing = false;
    
    private RecyclerView modsRecycler;
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable searchRunnable = this::applyFilters;
    private ModMenuAdapter adapter;
    private EditText searchInput;
    private ImageButton clearSearchBtn;
    private TextView navModules, navSettings, navHudEditor;
    private ImageButton compactNavModules, compactNavSettings, compactNavHudEditor;
    private TextView filterAll, filterFavorites, filterEnabled, filterInbuilt, filterExternal;
    private TextView moduleCountText, emptyStateText;
    private TextView compactFilterSelector, compactModuleCount;
    private LinearLayout navCategories;
    private TextView modSectionTitle;
    private View detailsPanel;
    private final java.util.Map<String, TextView> categoryNavViews = new java.util.LinkedHashMap<>();
    private String activeCategory = "combat";
    private static final String[] CATEGORY_KEYS = {"combat", "movement", "misc", "visuals", "external"};
    private static final int[] CATEGORY_LABELS = {
        R.string.mod_menu_category_combat,
        R.string.mod_menu_category_movement,
        R.string.mod_menu_category_misc,
        R.string.mod_menu_category_visuals,
        R.string.mod_menu_category_external
    };
    private static final int[] CATEGORY_ICONS = {
        R.drawable.ic_aimassist_normal,
        R.drawable.ic_speed_normal,
        R.drawable.ic_settings,
        R.drawable.ic_esp_normal,
        R.drawable.ic_modules
    };
    private View settingsContainer;
    private View modulesContainer;
    private View emptyState;
    private View menuContainer;
    private View modMenuSidebar;
    private View modMenuLogo;
    private View filterBar;
    private View compactFilterBar;
    private Switch notificationsSwitch;
    private Switch pauseMenuOnlySwitch;
    private Switch compactModeSwitch;
    private TextView modMenuKeybindValue;
    private SeekBar modMenuOpacitySeekBar;
    private TextView modMenuOpacityText;
    private SeekBar modMenuButtonOpacitySeekBar;
    private TextView modMenuButtonOpacityText;
    private SeekBar hudButtonSizeSeekBar;
    private TextView hudButtonSizeText;
    private boolean updatingHudButtonSize = false;
    private boolean compactMode = false;
    private GridLayoutManager modsLayoutManager;
    private NeverloseModulesView solsticeView;
    
    private List<UnifiedMod> allMods = new ArrayList<>();
    private List<UnifiedMod> filteredMods = new ArrayList<>();
    private final Set<String> favoriteKeys = new HashSet<>();
    private ModuleFilter activeFilter = ModuleFilter.ALL;
    private MenuSection activeSection = MenuSection.MODULES;
    private String lastConfigModKey;
    private String lastSearchQuery = "";
    private Parcelable fullModsScrollState;
    private Parcelable compactModsScrollState;
    private int settingsScrollPosition;
    private final Map<String, Integer> legacyConfigScrollPositions = new HashMap<>();
    private boolean restoringLastSection;
    private View inlineConfigCard;
    private String inlineConfigKey;
    private FrameLayout inlineConfigHost;
    private ValueAnimator inlineConfigAnimator;
    
    private ModMenuCallback callback;
    private ModNotificationManager notificationManager;
    
    private void crossfade(View view) {
        view.setAlpha(0f);
        view.setTranslationX(30f);
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(250)
            .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
            .start();
    }

    private void animateMenuEnter(final View menuContainer) {
        menuContainer.setAlpha(0f);
        menuContainer.setScaleX(0.85f);
        menuContainer.setScaleY(0.85f);
        
        menuContainer.post(() -> {
            menuContainer.setPivotX(menuContainer.getWidth() / 2f);
            menuContainer.setPivotY(menuContainer.getHeight() / 2f);
            
            int opacity = InbuiltModManager.getInstance(activity).getModMenuOpacity();
            float targetAlpha = opacity / 100f;
            
            menuContainer.animate()
                .alpha(targetAlpha)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                .withLayer()
                .start();
        });
    }
    
    private void animateMenuExit(final View menuContainer, Runnable onEnd) {
        menuContainer.setPivotX(menuContainer.getWidth() / 2f);
        menuContainer.setPivotY(menuContainer.getHeight() / 2f);
        
        menuContainer.animate()
            .alpha(0f)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(180)
            .setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f))
            .withLayer()
            .setListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    menuContainer.animate().setListener(null);
                    onEnd.run();
                }
            })
            .start();
    }

    private int getAccentColor() {
        return 0xFF4E83FF;
    }
    
    public interface ModMenuCallback {
        void onModToggled(String modId, boolean enabled);
        void onButtonOpacityChanged(int opacity);
    }
    
    public ModMenuOverlay(Activity activity) {
        this.activity = activity;
        this.windowManager = (WindowManager) activity.getSystemService(Activity.WINDOW_SERVICE);
        this.notificationManager = new ModNotificationManager(activity);
    }
    
    public void setCallback(ModMenuCallback callback) {
        this.callback = callback;
    }
    
    public void show() {
        if (isShowing) {
            refreshMods();
            return;
        }
        showInternal();
    }
    
    private void prepareMenuKeyboard() {
        ModMenuKeyRoot root = (ModMenuKeyRoot) overlayView;
        root.setShortcutHandler(event -> {
            InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
            return overlayManager != null && overlayManager.handleModMenuShortcut(event);
        });
        root.setFocusableInTouchMode(true);
        root.requestFocus();
    }

    private void showInternal() {
        if (isShowing || activity.isFinishing() || activity.isDestroyed()) return;
        
        try {
            overlayView = LayoutInflater.from(activity).inflate(R.layout.overlay_mod_menu, null);
            prepareMenuKeyboard();
            
            int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            overlayView.setSystemUiVisibility(uiOptions);
            
            overlayView.setOnSystemUiVisibilityChangeListener(visibility -> {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    if (overlayView != null) {
                        overlayView.setSystemUiVisibility(uiOptions);
                    }
                }
            });
            
            setupViews();
            loadMods();
            restoreModsScrollPosition();
            
            wmParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            );
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                wmParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            wmParams.gravity = Gravity.CENTER;
            wmParams.token = activity.getWindow().getDecorView().getWindowToken();
            
            windowManager.addView(overlayView, wmParams);
            isShowing = true;
            restoreLastSection();
            overlayView.requestFocus();
            
            overlayView.setAlpha(0f);
            overlayView.animate().alpha(1f).setDuration(220).start();
            
            View menuContainer = overlayView.findViewById(R.id.mod_menu_container);
            if (menuContainer != null && activeSection != MenuSection.HUD_EDITOR) {
                animateMenuEnter(menuContainer);
            }
        } catch (Throwable e) {
            Log.e(TAG, "showInternal failed, using in-content fallback", e);
            try {
                showFallback();
            } catch (Throwable fallbackError) {
                Log.e(TAG, "showFallback failed", fallbackError);
                overlayView = null;
                wmParams = null;
                isShowing = false;
            }
        }
    }
    
    private void showFallback() {
        if (isShowing) return;
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) return;
        
        overlayView = LayoutInflater.from(activity).inflate(R.layout.overlay_mod_menu, null);
        prepareMenuKeyboard();
        setupViews();
        loadMods();
        restoreModsScrollPosition();
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        rootView.addView(overlayView, params);
        isShowing = true;
        overlayView.requestFocus();
        wmParams = null;
        restoreLastSection();
        
        overlayView.setAlpha(0f);
        overlayView.animate().alpha(1f).setDuration(220).start();
        
        View menuContainer = overlayView.findViewById(R.id.mod_menu_container);
        if (menuContainer != null && activeSection != MenuSection.HUD_EDITOR) {
            animateMenuEnter(menuContainer);
        }
    }
    
    private void setupViews() {
        menuContainer = overlayView.findViewById(R.id.mod_menu_container);
        modMenuSidebar = overlayView.findViewById(R.id.mod_menu_sidebar);
        modMenuLogo = overlayView.findViewById(R.id.mod_menu_logo);
        filterBar = overlayView.findViewById(R.id.filter_bar);
        compactFilterBar = overlayView.findViewById(R.id.compact_filter_bar);
        navCategories = overlayView.findViewById(R.id.nav_categories);
        modSectionTitle = overlayView.findViewById(R.id.mod_section_title);
        detailsPanel = overlayView.findViewById(R.id.mod_menu_details);
        compactFilterSelector = overlayView.findViewById(R.id.compact_filter_selector);
        compactModuleCount = overlayView.findViewById(R.id.compact_module_count);
        ImageButton closeBtn = overlayView.findViewById(R.id.btn_close_menu);
        searchInput = overlayView.findViewById(R.id.search_input);
        clearSearchBtn = overlayView.findViewById(R.id.btn_clear_search);
        modsRecycler = overlayView.findViewById(R.id.mods_grid_recycler);
        solsticeView = overlayView.findViewById(R.id.neverlose_modules);
        navModules = overlayView.findViewById(R.id.nav_modules);
        navSettings = overlayView.findViewById(R.id.nav_settings);
        navHudEditor = overlayView.findViewById(R.id.nav_hud_editor);
        compactNavModules = overlayView.findViewById(R.id.nav_modules_compact);
        compactNavSettings = overlayView.findViewById(R.id.nav_settings_compact);
        compactNavHudEditor = overlayView.findViewById(R.id.nav_hud_editor_compact);
        filterAll = overlayView.findViewById(R.id.filter_all);
        filterFavorites = overlayView.findViewById(R.id.filter_favorites);
        filterEnabled = overlayView.findViewById(R.id.filter_enabled);
        filterInbuilt = overlayView.findViewById(R.id.filter_inbuilt);
        filterExternal = overlayView.findViewById(R.id.filter_external);
        moduleCountText = overlayView.findViewById(R.id.module_count_text);
        settingsContainer = overlayView.findViewById(R.id.settings_container);
        modulesContainer = overlayView.findViewById(R.id.modules_container);
        emptyState = overlayView.findViewById(R.id.empty_state);
        emptyStateText = overlayView.findViewById(R.id.empty_state_text);
        notificationsSwitch = overlayView.findViewById(R.id.switch_notifications);
        pauseMenuOnlySwitch = overlayView.findViewById(R.id.switch_pause_menu_only);
        compactModeSwitch = overlayView.findViewById(R.id.switch_compact_mod_menu);

        View hudEditorTools = overlayView.findViewById(R.id.hud_editor_tools);
        View hudEditorDragHandle = overlayView.findViewById(R.id.hud_editor_drag_handle);
        if (hudEditorDragHandle != null) setupHudEditorDragHandle(hudEditorDragHandle, hudEditorTools);
        View btnHudSave = overlayView.findViewById(R.id.btn_hud_save);
        View btnHudCancel = overlayView.findViewById(R.id.btn_hud_cancel);
        View modMenuContainer = overlayView.findViewById(R.id.mod_menu_container);
        hudButtonSizeSeekBar = overlayView.findViewById(R.id.seekbar_hud_button_size);
        hudButtonSizeText = overlayView.findViewById(R.id.text_hud_button_size);

        if (hudButtonSizeSeekBar != null) {
            hudButtonSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser || updatingHudButtonSize) return;
                    InbuiltOverlayManager manager = InbuiltOverlayManager.getInstance();
                    if (manager != null) {
                        manager.setSelectedHudEditorButtonSize(progress);
                    }
                    updateHudButtonSizeText(progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            updateHudEditorSizeControls(0);
        }

        if (navHudEditor != null) {
            navHudEditor.setOnClickListener(v -> enterHudEditorMode(modMenuContainer, hudEditorTools));
        }
        if (compactNavHudEditor != null) {
            compactNavHudEditor.setOnClickListener(v -> enterHudEditorMode(modMenuContainer, hudEditorTools));
        }
        
        if (btnHudSave != null) {
            btnHudSave.setOnClickListener(v -> {
                exitHudEditorMode(modMenuContainer, hudEditorTools);
            });
        }
        
        View btnHudReset = overlayView.findViewById(R.id.btn_hud_reset);
        if (btnHudReset != null) {
            btnHudReset.setOnClickListener(v -> {
                InbuiltOverlayManager.getInstance().resetAllPositionsToCenter();
            });
        }

        if (btnHudCancel != null) {
            btnHudCancel.setOnClickListener(v -> {
                exitHudEditorMode(modMenuContainer, hudEditorTools);
            });
        }
        
        // Close on background tap
        overlayView.setOnClickListener(v -> {
            // Only hide if not in HUD editor mode
            if (hudEditorTools == null || hudEditorTools.getVisibility() != View.VISIBLE) {
                hide();
            }
        });
        menuContainer.setOnClickListener(v -> {}); // Consume clicks
        if (hudEditorTools != null) {
            hudEditorTools.setOnClickListener(v -> {}); // Consume clicks
        }
        
        closeBtn.setOnClickListener(v -> hide());
        
        // Search functionality
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                lastSearchQuery = s.toString();
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, 60L);
                clearSearchBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        clearSearchBtn.setOnClickListener(v -> {
            searchInput.setText("");
            clearSearchBtn.setVisibility(View.GONE);
        });
        if (!lastSearchQuery.isEmpty()) searchInput.setText(lastSearchQuery);
        setupFilterButtons();
        setupCompactFilter();
        
        View btnBackToModules = overlayView.findViewById(R.id.btn_back_to_modules);
        if (btnBackToModules != null) {
            btnBackToModules.setOnClickListener(v -> showModulesSection());
        }
        
        // Navigation
        navModules.setOnClickListener(v -> {
            activeCategory = null;
            updateSectionTitle();
            showModulesSection();
        });
        navSettings.setOnClickListener(v -> showSettingsSection());
        if (compactNavModules != null) {
            compactNavModules.setOnClickListener(v -> {
                activeCategory = null;
                updateSectionTitle();
                showModulesSection();
            });
        }
        if (compactNavSettings != null) compactNavSettings.setOnClickListener(v -> showSettingsSection());

        // Settings
        InbuiltModManager modManager = InbuiltModManager.getInstance(activity);
        compactMode = modManager.isModMenuCompact();
        notificationsSwitch.setChecked(modManager.isNotificationsEnabled());
        notificationsSwitch.setOnCheckedChangeListener((btn, checked) -> {
            modManager.setNotificationsEnabled(checked);
        });

        if (pauseMenuOnlySwitch != null) {
            pauseMenuOnlySwitch.setChecked(modManager.isPauseMenuOnly());
            pauseMenuOnlySwitch.setOnCheckedChangeListener((btn, checked) -> {
                modManager.setPauseMenuOnly(checked);
            });
        }

        modMenuKeybindValue = overlayView.findViewById(R.id.mod_menu_keybind_value);
        View modMenuKeybindSetting = overlayView.findViewById(R.id.setting_mod_menu_keybind);
        updateModMenuKeybindValue();
        if (modMenuKeybindSetting != null) {
            modMenuKeybindSetting.setOnClickListener(v -> showModMenuKeybindDialog());
        }

        if (compactModeSwitch != null) {
            compactModeSwitch.setChecked(compactMode);
            compactModeSwitch.setOnCheckedChangeListener((btn, checked) -> {
                modManager.setModMenuCompact(checked);
                setCompactMode(checked);
            });
        }

        modMenuOpacitySeekBar = overlayView.findViewById(R.id.seekbar_mod_menu_opacity);
        modMenuOpacityText = overlayView.findViewById(R.id.text_mod_menu_opacity);
        int currentMenuOpacity = modManager.getModMenuOpacity();
        modMenuOpacitySeekBar.setProgress(currentMenuOpacity);
        modMenuOpacityText.setText(activity.getString(R.string.mod_menu_percent_value, currentMenuOpacity));
        modMenuOpacitySeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    modMenuOpacityText.setText(activity.getString(R.string.mod_menu_percent_value, progress));
                    modManager.setModMenuOpacity(progress);
                    applyMenuOpacity();
                }
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        modMenuButtonOpacitySeekBar = overlayView.findViewById(R.id.seekbar_mod_menu_button_opacity);
        modMenuButtonOpacityText = overlayView.findViewById(R.id.text_mod_menu_button_opacity);
        int currentButtonOpacity = modManager.getModMenuButtonOpacity();
        modMenuButtonOpacitySeekBar.setProgress(currentButtonOpacity);
        modMenuButtonOpacityText.setText(activity.getString(R.string.mod_menu_percent_value, currentButtonOpacity));
        modMenuButtonOpacitySeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    modMenuButtonOpacityText.setText(activity.getString(R.string.mod_menu_percent_value, progress));
                    modManager.setModMenuButtonOpacity(progress);
                    if (callback != null) {
                        callback.onButtonOpacityChanged(progress);
                    }
                }
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        
        applyMenuOpacity();
        
        adapter = new ModMenuAdapter();
        adapter.setCompactMode(compactMode);
        modsLayoutManager = new GridLayoutManager(activity, compactMode ? 1 : 2);
        modsLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter != null && adapter.isGroupHeader(position)
                    ? modsLayoutManager.getSpanCount()
                    : 1;
            }
        });
        modsRecycler.setLayoutManager(modsLayoutManager);
        modsRecycler.setItemAnimator(null);
        modsRecycler.setHasFixedSize(true);
        adapter.setOnModActionListener(new ModMenuAdapter.OnModActionListener() {
            @Override
            public void onToggle(UnifiedMod mod, boolean enabled) {
                mod.applyEnabled(enabled);
                InbuiltModManager modManager = InbuiltModManager.getInstance(activity);
                if (enabled && modManager.isNotificationsEnabled()) {
                    notificationManager.show(mod.getName(), mod.getStableKey());
                }
                if (callback != null) {
                    callback.onModToggled(mod.getStableKey(), enabled);
                }
                if (activeFilter == ModuleFilter.ENABLED) {
                    applyFilters();
                }
            }
            @Override
            public void onConfig(UnifiedMod mod) {
                showConfigSection(mod);
            }
            @Override
            public void onFavoriteChanged(UnifiedMod mod, boolean favorite) {
                InbuiltModManager.getInstance(activity).setModFavorite(mod.getStableKey(), favorite);
                if (favorite) {
                    favoriteKeys.add(mod.getStableKey());
                } else {
                    favoriteKeys.remove(mod.getStableKey());
                }
                if (activeFilter == ModuleFilter.FAVORITES) {
                    applyFilters();
                }
            }
        });
        modsRecycler.setAdapter(adapter);
        if (solsticeView != null) {
            solsticeView.setListener(new NeverloseModulesView.Listener() {
                @Override
                public void onToggle(UnifiedMod mod, boolean enabled) {
                    InbuiltModManager modManager = InbuiltModManager.getInstance(activity);
                    if (enabled && modManager.isNotificationsEnabled()) {
                        notificationManager.show(mod.getName(), mod.getStableKey());
                    }
                    if (callback != null) {
                        callback.onModToggled(mod.getStableKey(), enabled);
                    }
                    if (activeFilter == ModuleFilter.ENABLED) {
                        applyFilters();
                    }
                }

                @Override
                public void onConfig(UnifiedMod mod, float anchorX, float anchorY) {
                    toggleInlineConfig(mod, anchorX, anchorY);
                }
            });
            solsticeView.setOnScrollListener(this::dismissInlineConfigImmediate);
        }
        applyCompactModeLayout(compactMode);
    }

    private void updateModMenuKeybindValue() {
        if (modMenuKeybindValue == null) return;
        int keyCode = InbuiltModManager.getInstance(activity).getModMenuKeybind();
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            modMenuKeybindValue.setText(R.string.mod_menu_keybind_unbound);
            return;
        }
        String keyLabel = KeyEvent.keyCodeToString(keyCode);
        if (keyLabel != null && keyLabel.startsWith("KEYCODE_")) {
            keyLabel = keyLabel.substring(8);
        }
        modMenuKeybindValue.setText(keyLabel == null ? "" : keyLabel.replace('_', ' '));
    }

    private void showModMenuKeybindDialog() {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.mod_menu_keybind)
                .setMessage(R.string.mod_config_press_any_key)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.mod_menu_keybind_reset, (d, which) -> {
                    InbuiltModManager.getInstance(activity).setModMenuKeybind(KeyEvent.KEYCODE_M);
                    updateModMenuKeybindValue();
                })
                .create();
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK) {
                dialog.dismiss();
                return true;
            }
            if (event.getRepeatCount() != 0 || keyCode == KeyEvent.KEYCODE_UNKNOWN
                    || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                    || keyCode == KeyEvent.KEYCODE_POWER || keyCode == KeyEvent.KEYCODE_HOME) {
                return true;
            }
            InbuiltModManager.getInstance(activity).setModMenuKeybind(keyCode);
            updateModMenuKeybindValue();
            dialog.dismiss();
            return true;
        });
        dialog.show();
    }

    private void restoreLastSection() {
        restoringLastSection = true;
        try {
            switch (activeSection) {
                case SETTINGS:
                    showSettingsSection();
                    break;
                case CONFIG:
                    boolean found = false;
                    for (UnifiedMod mod : allMods) {
                        if (mod.getStableKey().equals(lastConfigModKey) && mod.hasConfig()) {
                            showConfigSection(mod);
                            found = true;
                            break;
                        }
                    }
                    if (!found) showModulesSection();
                    break;
                case HUD_EDITOR:
                    enterHudEditorMode(menuContainer, overlayView.findViewById(R.id.hud_editor_tools));
                    break;
                case MODULES:
                default:
                    showModulesSection();
                    break;
            }
        } finally {
            restoringLastSection = false;
        }
    }

    private void saveVisibleScrollPosition() {
        if (restoringLastSection || !isShowing || overlayView == null) return;
        if (activeSection == MenuSection.MODULES && modulesContainer != null
                && modulesContainer.getVisibility() == View.VISIBLE && modsLayoutManager != null
                && modsRecycler != null && modsRecycler.getChildCount() > 0) {
            Parcelable state = modsLayoutManager.onSaveInstanceState();
            if (compactMode) compactModsScrollState = state;
            else fullModsScrollState = state;
        } else if (activeSection == MenuSection.SETTINGS && settingsContainer instanceof ScrollView) {
            settingsScrollPosition = ((ScrollView) settingsContainer).getScrollY();
        } else if (activeSection == MenuSection.CONFIG) {
            ViewGroup content = overlayView.findViewById(R.id.mod_config_content);
            RuntimeConfigView.save(content);
            if (content != null && content.getChildCount() > 0
                    && content.getChildAt(0) instanceof ScrollView && lastConfigModKey != null) {
                legacyConfigScrollPositions.put(lastConfigModKey,
                        ((ScrollView) content.getChildAt(0)).getScrollY());
            }
        }
    }

    private void restoreModsScrollPosition() {
        if (modsLayoutManager == null || adapter == null || adapter.getItemCount() == 0) return;
        Parcelable state = compactMode ? compactModsScrollState : fullModsScrollState;
        if (state != null) modsLayoutManager.onRestoreInstanceState(state);
    }

    private void showModulesSection() {
        saveVisibleScrollPosition();
        activeSection = MenuSection.MODULES;
        updateNavigationItem(navSettings, compactNavSettings, false);
        updateNavigationItem(navHudEditor, compactNavHudEditor, false);
        updateCategorySelection();
        
        if (modulesContainer.getVisibility() != View.VISIBLE) {
            modulesContainer.setVisibility(View.VISIBLE);
            crossfade(modulesContainer);
        }
        settingsContainer.setVisibility(View.GONE);
        if (detailsPanel != null) detailsPanel.setVisibility(View.GONE);
        
        if (overlayView != null) {
            View modConfigContainer = overlayView.findViewById(R.id.mod_config_container);
            View searchContainer = overlayView.findViewById(R.id.search_container);
            View configHeader = overlayView.findViewById(R.id.config_header);
            if (modConfigContainer != null) modConfigContainer.setVisibility(View.GONE);
            if (searchContainer != null) searchContainer.setVisibility(View.VISIBLE);
            if (configHeader != null) configHeader.setVisibility(View.GONE);
            updateSectionTitle();
            updateFilterBarVisibility();
        }
    }
    
    private void showSettingsSection() {
        dismissInlineConfig();
        saveVisibleScrollPosition();
        activeSection = MenuSection.SETTINGS;
        updateNavigationItem(navSettings, compactNavSettings, true);
        updateNavigationItem(navHudEditor, compactNavHudEditor, false);
        updateCategorySelection();
        
        modulesContainer.setVisibility(View.VISIBLE);
        if (settingsContainer.getVisibility() != View.VISIBLE) {
            settingsContainer.setVisibility(View.VISIBLE);
            crossfade(settingsContainer);
        }
        if (detailsPanel != null) {
            detailsPanel.setVisibility(View.VISIBLE);
            crossfade(detailsPanel);
        }
        
        if (overlayView != null) {
            View modConfigContainer = overlayView.findViewById(R.id.mod_config_container);
            View searchContainer = overlayView.findViewById(R.id.search_container);
            View configHeader = overlayView.findViewById(R.id.config_header);
            TextView configTitle = overlayView.findViewById(R.id.config_title);
            if (modConfigContainer != null) modConfigContainer.setVisibility(View.GONE);
            if (searchContainer != null) searchContainer.setVisibility(View.GONE);
            if (configHeader != null) configHeader.setVisibility(View.VISIBLE);
            if (configTitle != null) configTitle.setText(R.string.settings);
            if (filterBar != null) filterBar.setVisibility(View.GONE);
            if (compactFilterBar != null) compactFilterBar.setVisibility(View.GONE);
        }
        if (restoringLastSection && settingsContainer instanceof ScrollView) {
            View current = settingsContainer;
            int position = settingsScrollPosition;
            current.post(() -> {
                if (overlayView != null && settingsContainer == current) {
                    ((ScrollView) current).scrollTo(0, position);
                }
            });
        }
    }
    
    private void showConfigSection(UnifiedMod mod) {
        dismissInlineConfig();
        saveVisibleScrollPosition();
        if (mod.openCustomConfig()) {
            hide();
            return;
        }
        activeSection = MenuSection.CONFIG;
        lastConfigModKey = mod.getStableKey();
        updateNavigationItem(navSettings, compactNavSettings, false);
        updateNavigationItem(navHudEditor, compactNavHudEditor, false);
        updateCategorySelection();
        
        modulesContainer.setVisibility(View.VISIBLE);
        settingsContainer.setVisibility(View.GONE);
        if (detailsPanel != null) {
            detailsPanel.setVisibility(View.VISIBLE);
            crossfade(detailsPanel);
        }
        
        if (overlayView != null) {
            View modConfigContainer = overlayView.findViewById(R.id.mod_config_container);
            View searchContainer = overlayView.findViewById(R.id.search_container);
            View configHeader = overlayView.findViewById(R.id.config_header);
            ViewGroup modConfigContent = overlayView.findViewById(R.id.mod_config_content);
            TextView configTitle = overlayView.findViewById(R.id.config_title);
            
            if (modConfigContainer != null) {
                modConfigContainer.setVisibility(View.VISIBLE);
                crossfade(modConfigContainer);
            }
            if (searchContainer != null) {
                searchContainer.setVisibility(View.GONE);
            }
            if (configHeader != null) configHeader.setVisibility(View.VISIBLE);
            if (filterBar != null) filterBar.setVisibility(View.GONE);
            if (compactFilterBar != null) compactFilterBar.setVisibility(View.GONE);
            if (configTitle != null) configTitle.setText(mod.getName());
            
            if (modConfigContent != null) {
                ModConfigView.render(activity, modConfigContent, mod, compactMode, () -> {
                    InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
                    if (overlayManager != null) {
                        overlayManager.applyConfigurationChanges(mod.getId());
                    }
                });
                if (modConfigContent.getChildCount() > 0
                        && modConfigContent.getChildAt(0) instanceof ScrollView) {
                    ScrollView scroll = (ScrollView) modConfigContent.getChildAt(0);
                    int position = legacyConfigScrollPositions.getOrDefault(mod.getStableKey(), 0);
                    scroll.post(() -> {
                        if (overlayView != null && scroll.getParent() == modConfigContent) {
                            scroll.scrollTo(0, position);
                        }
                    });
                }
            }
        }
    }

    private void toggleInlineConfig(UnifiedMod mod, float anchorX, float anchorY) {
        if (mod == null) return;
        String key = mod.getStableKey();
        if (inlineConfigCard != null && key != null && key.equals(inlineConfigKey)) {
            dismissInlineConfig();
            return;
        }
        dismissInlineConfigImmediate();
        if (mod.hasCustomConfig()) {
            showConfigSection(mod);
            return;
        }
        if (!(modulesContainer instanceof ViewGroup)) return;
        ViewGroup container = (ViewGroup) modulesContainer;
        int containerW = container.getWidth();
        int containerH = container.getHeight();
        if (containerW <= 0 || containerH <= 0) return;

        int margin = dp(12);
        int cardW = containerW - margin * 2;
        int desired = Math.min(dp(380), Math.max(dp(220), containerH - dp(24)));
        int top = Math.round(anchorY) + dp(4);
        if (top + desired > containerH - dp(8)) {
            top = Math.round(anchorY) - desired - dp(34);
        }
        top = Math.max(dp(8), Math.min(top, containerH - dp(120)));
        int targetH = Math.min(desired, containerH - top - dp(8));
        if (targetH < dp(160)) {
            top = dp(8);
            targetH = Math.min(desired, containerH - dp(16));
        }

        FrameLayout card = new FrameLayout(activity);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0D0F16);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), 0xFF272B36);
        card.setBackground(bg);
        card.setElevation(dp(12));
        card.setClickable(true);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(4), 0, dp(4), dp(4));
        card.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), 0, dp(6), 0);
        TextView title = new TextView(activity);
        title.setText(mod.getName());
        title.setTextColor(0xFFD9DBE2);
        title.setTextSize(13f);
        title.setSingleLine(true);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.BOLD));
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(activity);
        close.setText("×");
        close.setTextColor(0xFF9296A2);
        close.setTextSize(20f);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(10), 0, dp(10), dp(2));
        close.setOnClickListener(v -> dismissInlineConfig());
        header.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        shell.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        TextView hint = new TextView(activity);
        hint.setText(R.string.mod_config_inline_hint);
        hint.setTextColor(0xFF5E6370);
        hint.setTextSize(8.5f);
        hint.setLetterSpacing(0.08f);
        hint.setPadding(dp(12), 0, dp(12), dp(3));
        shell.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));

        FrameLayout configHost = new FrameLayout(activity);
        shell.addView(configHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ModConfigView.render(activity, configHost, mod, compactMode, () -> {
            InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
            if (overlayManager != null) {
                overlayManager.applyConfigurationChanges(mod.getId());
            }
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(cardW, 0);
        params.leftMargin = margin;
        params.topMargin = top;
        params.gravity = Gravity.TOP;
        container.addView(card, params);

        card.setAlpha(0f);
        card.setTranslationY(-dp(10));
        inlineConfigCard = card;
        inlineConfigHost = configHost;
        inlineConfigKey = key;
        if (solsticeView != null) solsticeView.setExpandedKey(key);

        card.animate().alpha(1f).translationY(0f)
                .setDuration(190L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        ValueAnimator open = ValueAnimator.ofInt(0, targetH);
        open.setDuration(230L);
        open.setInterpolator(new DecelerateInterpolator());
        open.addUpdateListener(a -> {
            if (inlineConfigCard != card) return;
            ViewGroup.LayoutParams lp = card.getLayoutParams();
            lp.height = (int) a.getAnimatedValue();
            card.setLayoutParams(lp);
        });
        inlineConfigAnimator = open;
        open.start();
    }

    private void dismissInlineConfig() {
        dismissInlineConfig(true);
    }

    private void dismissInlineConfigImmediate() {
        dismissInlineConfig(false);
    }

    private void dismissInlineConfig(boolean animate) {
        ValueAnimator animator = inlineConfigAnimator;
        inlineConfigAnimator = null;
        if (animator != null) animator.cancel();

        View card = inlineConfigCard;
        FrameLayout host = inlineConfigHost;
        if (card == null) {
            inlineConfigKey = null;
            if (solsticeView != null) solsticeView.setExpandedKey(null);
            return;
        }

        Runnable cleanup = () -> {
            if (card.getParent() instanceof ViewGroup) {
                ((ViewGroup) card.getParent()).removeView(card);
            }
            RuntimeConfigView.stop(host);
            if (inlineConfigCard == card) {
                inlineConfigCard = null;
                inlineConfigHost = null;
                inlineConfigKey = null;
                if (solsticeView != null) solsticeView.setExpandedKey(null);
            }
        };

        if (!animate) {
            cleanup.run();
            return;
        }

        card.animate().alpha(0f).translationY(-dp(8))
                .setDuration(150L)
                .withEndAction(cleanup)
                .start();
        ValueAnimator close = ValueAnimator.ofInt(card.getHeight(), 0);
        close.setDuration(200L);
        close.setInterpolator(new DecelerateInterpolator());
        close.addUpdateListener(a -> {
            ViewGroup.LayoutParams lp = card.getLayoutParams();
            lp.height = (int) a.getAnimatedValue();
            card.setLayoutParams(lp);
        });
        inlineConfigAnimator = close;
        close.start();
    }

    private void setupHudEditorDragHandle(View handle, View tools) {
        final int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;
            private int pointerId = -1;
            private boolean dragging;

            @Override public boolean onTouch(View view, MotionEvent event) {
                if (overlayView == null || tools == null || tools.getVisibility() != View.VISIBLE) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        pointerId = event.getPointerId(0);
                        downX = event.getRawX();
                        downY = event.getRawY();
                        dragging = false;
                        if (wmParams != null) {
                            startX = wmParams.x;
                            startY = wmParams.y;
                        } else {
                            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
                            startX = params.leftMargin;
                            startY = params.topMargin;
                        }
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (pointerId < 0 || event.getPointerId(0) != pointerId) return true;
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) dragging = true;
                        if (dragging) moveHudEditorTools(startX + Math.round(dx), startY + Math.round(dy));
                        return true;
                    case MotionEvent.ACTION_POINTER_UP:
                        if (event.getPointerId(event.getActionIndex()) != pointerId) return true;
                        pointerId = -1;
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (pointerId >= 0 && !dragging) view.performClick();
                        // Fall through to release the gesture on both up and cancellation.
                    case MotionEvent.ACTION_CANCEL:
                        pointerId = -1;
                        dragging = false;
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void moveHudEditorTools(int x, int y) {
        if (overlayView == null) return;
        OverlayBounds.Position position = OverlayBounds.clampPosition(activity, overlayView, x, y);
        if (wmParams != null && windowManager != null) {
            wmParams.x = position.x;
            wmParams.y = position.y;
            windowManager.updateViewLayout(overlayView, wmParams);
        } else if (overlayView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
            params.leftMargin = position.x;
            params.topMargin = position.y;
            overlayView.setLayoutParams(params);
        }
    }

    private void enterHudEditorMode(View modMenuContainer, View hudEditorTools) {
        saveVisibleScrollPosition();
        activeSection = MenuSection.HUD_EDITOR;
        updateNavigationItem(navModules, compactNavModules, false);
        updateNavigationItem(navSettings, compactNavSettings, false);
        updateNavigationItem(navHudEditor, compactNavHudEditor, true);

        if (modMenuContainer != null) {
            modMenuContainer.setVisibility(View.GONE);
        }
        if (hudEditorTools != null) {
            hudEditorTools.setVisibility(View.VISIBLE);
            hudEditorTools.setAlpha(1f);
            hudEditorTools.setTranslationX(0f);
        }
        if (overlayView != null) {
            overlayView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            overlayView.setClickable(false);
        }
        if (wmParams != null && windowManager != null) {
            wmParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
            wmParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            wmParams.gravity = Gravity.TOP | Gravity.LEFT;
            wmParams.x = 0;
            wmParams.y = 0;
            windowManager.updateViewLayout(overlayView, wmParams);
        } else if (overlayView != null && overlayView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.leftMargin = 0;
            params.topMargin = 0;
            overlayView.setLayoutParams(params);
        }
        // Wait for the compact tool window to be measured before centering it.
        final View editorRoot = overlayView;
        if (editorRoot != null) editorRoot.post(() -> {
            if (overlayView != editorRoot || hudEditorTools == null || hudEditorTools.getVisibility() != View.VISIBLE) return;
            OverlayBounds.Position rightEdge = OverlayBounds.clampPosition(
                    activity, editorRoot, Integer.MAX_VALUE, 0);
            moveHudEditorTools(rightEdge.x / 2, 0);
        });
        InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
        if (overlayManager != null) {
            overlayManager.setHudEditorSelectionListener(this::updateHudEditorSizeControls);
            overlayManager.setHudEditorMode(true);
        }
    }

    private void exitHudEditorMode(View modMenuContainer, View hudEditorTools) {
        if (hudEditorTools != null) {
            hudEditorTools.setVisibility(View.GONE);
        }
        if (overlayView != null) {
            overlayView.setClickable(true);
        }
        if (wmParams != null && windowManager != null) {
            wmParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            wmParams.height = WindowManager.LayoutParams.MATCH_PARENT;
            wmParams.gravity = Gravity.CENTER;
            wmParams.x = 0;
            wmParams.y = 0;
            windowManager.updateViewLayout(overlayView, wmParams);
        } else if (overlayView != null && overlayView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.CENTER;
            params.leftMargin = 0;
            params.topMargin = 0;
            overlayView.setLayoutParams(params);
        }
        if (modMenuContainer != null) {
            modMenuContainer.setVisibility(View.VISIBLE);
            animateMenuEnter(modMenuContainer);
        }
        InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
        if (overlayManager != null) {
            overlayManager.setHudEditorMode(false);
            overlayManager.setHudEditorSelectionListener(null);
        }
        showModulesSection();
    }

    private void updateHudEditorSizeControls(int sizeDp) {
        if (hudButtonSizeSeekBar == null) return;
        boolean hasSelection = sizeDp > 0;
        hudButtonSizeSeekBar.setEnabled(hasSelection);
        if (!hasSelection) {
            updateHudButtonSizeText(0);
            return;
        }
        updatingHudButtonSize = true;
        hudButtonSizeSeekBar.setProgress(sizeDp);
        updatingHudButtonSize = false;
        updateHudButtonSizeText(sizeDp);
    }

    private void updateHudButtonSizeText(int size) {
        if (hudButtonSizeText == null) return;
        if (size <= 0) {
            hudButtonSizeText.setText(activity.getString(R.string.overlay_button_size));
        } else {
            hudButtonSizeText.setText(activity.getString(R.string.overlay_button_size_value, size));
        }
    }

    private void setupCompactFilter() {
        if (compactFilterSelector == null) return;
        compactFilterSelector.setOnClickListener(this::showCompactFilterMenu);
        updateCompactFilterSelector();
    }

    private void showCompactFilterMenu(View anchor) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        popup.getMenu().add(0, 100, 0, R.string.filter_all);
        popup.getMenu().add(0, 101, 1, R.string.mod_menu_favorites);
        popup.getMenu().add(0, 102, 2, R.string.mod_menu_filter_enabled);
        popup.getMenu().add(0, 103, 3, R.string.mod_menu_filter_inbuilt);
        popup.getMenu().add(0, 104, 4, R.string.mod_menu_filter_external);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 101:
                    setModuleFilter(ModuleFilter.FAVORITES);
                    break;
                case 102:
                    setModuleFilter(ModuleFilter.ENABLED);
                    break;
                case 103:
                    setModuleFilter(ModuleFilter.INBUILT);
                    break;
                case 104:
                    setModuleFilter(ModuleFilter.EXTERNAL);
                    break;
                case 100:
                default:
                    setModuleFilter(ModuleFilter.ALL);
                    break;
            }
            return true;
        });
        popup.show();
    }

    private void setCompactMode(boolean compact) {
        saveVisibleScrollPosition();
        compactMode = compact;
        if (modsLayoutManager != null) {
            modsLayoutManager.setSpanCount(compact ? 1 : 2);
        }
        if (adapter != null) {
            adapter.setCompactMode(compact);
            adapter.updateMods(filteredMods, favoriteKeys);
        }
        restoreModsScrollPosition();
        applyCompactModeLayout(compact);
        updateFilterBarVisibility();
        updateFilterButtons();
        updateModuleCount();
    }

    private void applyCompactModeLayout(boolean compact) {
        if (menuContainer == null) return;
        ViewGroup.LayoutParams rawParams = menuContainer.getLayoutParams();
        if (rawParams instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rawParams;
            if (compact) {
                int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
                int availableWidth = Math.max(1, screenWidth - dp(36));
                int desiredWidth = Math.min(dp(390), Math.round(screenWidth * 0.42f));
                int minimumWidth = Math.min(dp(292), availableWidth);
                params.width = Math.min(availableWidth, Math.max(minimumWidth, desiredWidth));
                int availableHeight = Math.max(1, screenHeight - dp(24));
                params.height = Math.min(dp(560), availableHeight);
                params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
                params.setMargins(dp(18), dp(12), 0, dp(12));
                params.setMarginStart(dp(18));
                params.setMarginEnd(0);
            } else {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                params.gravity = Gravity.CENTER;
                params.setMargins(dp(10), dp(10), dp(10), dp(10));
                params.setMarginStart(dp(10));
                params.setMarginEnd(dp(10));
            }
            menuContainer.setLayoutParams(params);
        }

        if (modMenuSidebar != null) {
            ViewGroup.LayoutParams params = modMenuSidebar.getLayoutParams();
            params.width = dp(compact ? 62 : 150);
            modMenuSidebar.setLayoutParams(params);
            if (modMenuSidebar instanceof android.widget.LinearLayout) {
                ((android.widget.LinearLayout) modMenuSidebar).setGravity(compact ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.NO_GRAVITY);
            }
            modMenuSidebar.setPadding(0, dp(14), 0, dp(14));
        }
        if (modMenuLogo != null) modMenuLogo.setVisibility(compact ? View.GONE : View.VISIBLE);
        View searchContainer = overlayView.findViewById(R.id.search_container);
        if (searchContainer != null) {
            ViewGroup.LayoutParams searchParams = searchContainer.getLayoutParams();
            searchParams.width = dp(compact ? 148 : 220);
            searchContainer.setLayoutParams(searchParams);
        }

        setNavigationMode(navModules, compactNavModules, R.string.mod_menu_all_modules, compact);
        if (navModules != null) navModules.setVisibility(View.GONE);
        if (compactNavModules != null) compactNavModules.setVisibility(View.GONE);
        setNavigationMode(navHudEditor, compactNavHudEditor, R.string.mod_menu_hud_editor, compact);
        setNavigationMode(navSettings, compactNavSettings, R.string.settings, compact);

        updateCategorySelection();
        updateNavigationItem(navSettings, compactNavSettings, activeSection == MenuSection.SETTINGS);
        updateNavigationItem(navHudEditor, compactNavHudEditor, false);
        if (modulesContainer != null) {
            modulesContainer.setVisibility(View.VISIBLE);
        }
        if (detailsPanel != null) {
            ViewGroup.LayoutParams params = detailsPanel.getLayoutParams();
            params.width = compact ? ViewGroup.LayoutParams.MATCH_PARENT : dp(306);
            detailsPanel.setLayoutParams(params);
        }
        if (navCategories != null) {
            navCategories.setVisibility(View.GONE);
        }

        if (modsRecycler != null) {
            int padding = dp(compact ? 4 : 10);
            modsRecycler.setPadding(padding, padding, padding, padding);
            modsRecycler.setVisibility(View.GONE);
        }
        if (solsticeView != null) {
            solsticeView.setVisibility(View.VISIBLE);
        }
        menuContainer.requestLayout();
    }

    private void setNavigationMode(TextView fullView, ImageButton compactView, int textRes, boolean compact) {
        if (fullView != null) {
            fullView.setVisibility(compact ? View.GONE : View.VISIBLE);
            if (!compact) {
                fullView.setText(textRes);
                fullView.setGravity(Gravity.CENTER_VERTICAL);
                fullView.setIncludeFontPadding(true);
                fullView.setPadding(dp(12), 0, dp(10), 0);
                fullView.setCompoundDrawablePadding(dp(8));
                ViewGroup.LayoutParams rawParams = fullView.getLayoutParams();
                if (rawParams instanceof android.widget.LinearLayout.LayoutParams) {
                    android.widget.LinearLayout.LayoutParams params = (android.widget.LinearLayout.LayoutParams) rawParams;
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    params.height = dp(44);
                    params.gravity = Gravity.NO_GRAVITY;
                    params.setMargins(0, 0, 0, 0);
                    fullView.setLayoutParams(params);
                }
            }
        }
        if (compactView != null) compactView.setVisibility(compact ? View.VISIBLE : View.GONE);
    }

    private void updateFilterBarVisibility() {
        // Neverlose board keeps every module on one canvas; filter chips and
        // category pages would split it up again, so both stay hidden.
        if (filterBar != null) filterBar.setVisibility(View.GONE);
        if (compactFilterBar != null) compactFilterBar.setVisibility(View.GONE);
    }

    private void updateCompactFilterSelector() {
        if (compactFilterSelector == null) return;
        compactFilterSelector.setText(getFilterLabelRes(activeFilter));
        compactFilterSelector.setTextColor(getAccentColor());
    }

    private int getFilterLabelRes(ModuleFilter filter) {
        switch (filter) {
            case FAVORITES:
                return R.string.mod_menu_favorites;
            case ENABLED:
                return R.string.mod_menu_filter_enabled;
            case INBUILT:
                return R.string.mod_menu_filter_inbuilt;
            case EXTERNAL:
                return R.string.mod_menu_filter_external;
            case ALL:
            default:
                return R.string.filter_all;
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void setupFilterButtons() {
        if (filterAll != null) {
            filterAll.setOnClickListener(v -> setModuleFilter(ModuleFilter.ALL));
        }
        if (filterFavorites != null) {
            filterFavorites.setOnClickListener(v -> setModuleFilter(ModuleFilter.FAVORITES));
        }
        if (filterEnabled != null) {
            filterEnabled.setOnClickListener(v -> setModuleFilter(ModuleFilter.ENABLED));
        }
        if (filterInbuilt != null) {
            filterInbuilt.setOnClickListener(v -> setModuleFilter(ModuleFilter.INBUILT));
        }
        if (filterExternal != null) {
            filterExternal.setOnClickListener(v -> setModuleFilter(ModuleFilter.EXTERNAL));
        }
        updateFilterButtons();
    }

    private void setModuleFilter(ModuleFilter filter) {
        activeFilter = filter;
        updateFilterButtons();
        applyFilters();
    }

    private void applyFilters() {
        filteredMods.clear();
        String query = searchInput != null
            ? searchInput.getText().toString().trim().toLowerCase(Locale.ROOT)
            : "";

        Map<String, GroupedMods> groupedMatches = new LinkedHashMap<>();
        for (UnifiedMod mod : allMods) {
            if (matchesActiveFilter(mod) && matchesQuery(mod, query) && matchesCategory(mod)) {
                GroupedMods group = groupedMatches.computeIfAbsent(
                    mod.getGroupId(), ignored -> new GroupedMods());
                group.add(mod, isFavorite(mod));
            }
        }
        for (GroupedMods group : groupedMatches.values()) {
            group.appendTo(filteredMods);
        }

        if (adapter != null) {
            adapter.updateMods(filteredMods, favoriteKeys);
        }
        if (solsticeView != null) {
            solsticeView.setContent(filteredMods, favoriteKeys);
        }
        updateEmptyState();
        updateModuleCount();
    }

    private String categoryKeyFor(UnifiedMod mod) {
        if (mod.getSource() == UnifiedMod.Source.EXTERNAL) return "external";
        String id = safeString(mod.getId()).toLowerCase(Locale.ROOT);
        switch (id) {
            case "aim_assist":
            case "trigger_bot":
            case "target_esp":
            case "swing":
                return "combat";
            case "esp":
            case "name_tags":
            case "fullbright":
            case "toggle_hud":
            case "fps_display":
            case "cps_display":
                return "visuals";
            case "auto_sprint":
            case "speed":
            case "zoom":
            case "snaplook":
            case "gyro":
            case "virtual_cursor":
            case "pojav_controls":
                return "movement";
            default:
                return "misc";
        }
    }

    private boolean matchesCategory(UnifiedMod mod) {
        if (activeCategory == null) return true;
        if (searchInput != null && searchInput.getText().length() > 0) return true;
        return activeCategory.equals(categoryKeyFor(mod));
    }

    private void rebuildCategories() {
        if (navCategories == null) return;
        navCategories.removeAllViews();
        categoryNavViews.clear();
        for (int i = 0; i < CATEGORY_KEYS.length; i++) {
            final String key = CATEGORY_KEYS[i];
            TextView item = new TextView(activity);
            item.setText(CATEGORY_LABELS[i]);
            item.setTextSize(13f);
            item.setTextColor(0xFF9296A2);
            item.setSingleLine(true);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(11), 0, dp(8), 0);
            item.setBackgroundResource(R.drawable.bg_zenix_nav);
            item.setCompoundDrawablesWithIntrinsicBounds(CATEGORY_ICONS[i], 0, 0, 0);
            item.setCompoundDrawablePadding(dp(9));
            item.setCompoundDrawableTintList(ColorStateList.valueOf(0xFF9296A2));
            item.setDefaultFocusHighlightEnabled(false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
            params.setMargins(0, dp(1), 0, dp(1));
            item.setLayoutParams(params);
            item.setOnClickListener(v -> selectCategory(key));
            navCategories.addView(item);
            categoryNavViews.put(key, item);
        }
        navCategories.setVisibility(compactMode ? View.GONE : View.VISIBLE);
    }

    private void selectCategory(String key) {
        dismissInlineConfig();
        activeCategory = key;
        activeFilter = ModuleFilter.ALL;
        updateFilterButtons();
        updateCategorySelection();
        updateSectionTitle();
        if (activeSection != MenuSection.MODULES) {
            showModulesSection();
        } else {
            applyFilters();
        }
    }

    private void updateCategorySelection() {
        for (java.util.Map.Entry<String, TextView> entry : categoryNavViews.entrySet()) {
            boolean selected = entry.getKey().equals(activeCategory)
                && activeSection == MenuSection.MODULES;
            TextView view = entry.getValue();
            view.setTextColor(selected ? 0xFF4E83FF : 0xFF9296A2);
            view.setAlpha(selected ? 1f : 1f);
            view.setBackgroundResource(selected
                ? R.drawable.bg_zenix_nav_active : R.drawable.bg_zenix_nav);
            android.content.res.ColorStateList tint = android.content.res.ColorStateList.valueOf(
                selected ? 0xFF4E83FF : 0xFF9296A2);
            view.setCompoundDrawableTintList(tint);
        }
        if (navModules != null) navModules.setVisibility(View.GONE);
        if (compactNavModules != null) compactNavModules.setVisibility(View.GONE);
        if (navCategories != null) {
            navCategories.setVisibility(compactMode ? View.GONE : View.VISIBLE);
        }
    }

    private void updateSectionTitle() {
        if (modSectionTitle == null) return;
        String label;
        if (activeCategory == null) {
            label = activity.getString(R.string.mod_menu_all_modules);
        } else {
            int index = java.util.Arrays.asList(CATEGORY_KEYS).indexOf(activeCategory);
            label = index >= 0
                ? activity.getString(CATEGORY_LABELS[index])
                : activity.getString(R.string.mod_menu_all_modules);
        }
        modSectionTitle.setText(label);
    }

    private void resetMenuState() {
        if (searchInput != null) searchInput.setText("");
        activeFilter = ModuleFilter.ALL;
        activeCategory = null;
        updateFilterButtons();
        updateCategorySelection();
        updateSectionTitle();
        showModulesSection();
    }

    private boolean matchesActiveFilter(UnifiedMod mod) {
        switch (activeFilter) {
            case FAVORITES:
                return isFavorite(mod);
            case ENABLED:
                return mod.isEnabled();
            case INBUILT:
                return mod.getSource() == UnifiedMod.Source.INBUILT;
            case EXTERNAL:
                return mod.getSource() == UnifiedMod.Source.EXTERNAL;
            case ALL:
            default:
                return true;
        }
    }

    private boolean matchesQuery(UnifiedMod mod, String query) {
        if (query.isEmpty()) return true;
        String searchText = (
            safeString(mod.getName()) + " " +
            safeString(mod.getDescription()) + " " +
            safeString(mod.getId()) + " " +
            safeString(mod.getModId()) + " " +
            safeString(mod.getGroupName()) + " " +
            safeString(mod.getGroupId())
        ).toLowerCase(Locale.ROOT);
        return searchText.contains(query);
    }

    private boolean isFavorite(UnifiedMod mod) {
        return favoriteKeys.contains(mod.getStableKey());
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private void updateFilterButtons() {
        updateFilterButton(filterAll, activeFilter == ModuleFilter.ALL);
        updateFilterButton(filterFavorites, activeFilter == ModuleFilter.FAVORITES);
        updateFilterButton(filterEnabled, activeFilter == ModuleFilter.ENABLED);
        updateFilterButton(filterInbuilt, activeFilter == ModuleFilter.INBUILT);
        updateFilterButton(filterExternal, activeFilter == ModuleFilter.EXTERNAL);
        updateCompactFilterSelector();
    }

    private void updateFilterButton(TextView view, boolean selected) {
        if (view == null) return;
        view.setTextColor(selected ? 0xFF4E83FF : 0xFF9296A2);
        view.setAlpha(1f);
        view.setBackgroundResource(selected
            ? R.drawable.bg_zenix_chip_active : R.drawable.bg_zenix_chip);
    }

    private void updateNavigationItem(TextView fullView, ImageButton compactView, boolean selected) {
        int color = selected ? getAccentColor() : 0xFF9296A2;
        if (fullView != null) {
            fullView.setTextColor(color);
            fullView.setAlpha(selected ? 1f : 0.85f);
            fullView.setCompoundDrawableTintList(ColorStateList.valueOf(color));
            fullView.setBackgroundResource(selected
                ? R.drawable.bg_zenix_nav_active : R.drawable.bg_zenix_nav);
        }
        if (compactView != null) {
            compactView.setAlpha(selected ? 1f : 0.88f);
            compactView.setImageTintList(ColorStateList.valueOf(color));
            if (selected) {
                compactView.setBackgroundResource(R.drawable.bg_mod_menu_nav_compact_selected);
            } else {
                TypedValue typedValue = new TypedValue();
                if (activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)) {
                    compactView.setBackgroundResource(typedValue.resourceId);
                } else {
                    compactView.setBackground(null);
                }
            }
        }
    }

    private void updateModuleCount() {
        String value = activity.getString(
            R.string.mod_menu_module_count,
            filteredMods.size(),
            allMods.size());
        if (moduleCountText != null) {
            moduleCountText.setText(value);
        }
        if (compactModuleCount != null) {
            compactModuleCount.setText(value);
        }
    }

    private void loadMods() {
        if (activeCategory == null) activeCategory = "combat";
        allMods.clear();

        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        favoriteKeys.clear();
        favoriteKeys.addAll(manager.getFavoriteModKeys());
        allMods.addAll(InbuiltModuleProvider.load(activity));
        allMods.addAll(ExternalModuleProvider.load(activity));

        rebuildCategories();
        applyFilters();
    }
    
    private void filterMods(String query) {
        applyFilters();
    }
    
    private void updateEmptyState() {
        if (emptyState != null) {
            emptyState.setVisibility(filteredMods.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (emptyStateText != null) {
            String query = searchInput != null ? searchInput.getText().toString().trim() : "";
            if (!query.isEmpty()) {
                emptyStateText.setText(R.string.mod_menu_no_matches);
            } else if (activeFilter == ModuleFilter.FAVORITES) {
                emptyStateText.setText(R.string.mod_menu_no_favorites);
            } else {
                emptyStateText.setText(R.string.mod_menu_no_mods);
            }
        }
    }
    
    public void refreshMods() {
        loadMods();
    }

    private void applyMenuOpacity() {
        if (overlayView != null) {
            View menuContainer = overlayView.findViewById(R.id.mod_menu_container);
            if (menuContainer != null) {
                int opacity = InbuiltModManager.getInstance(activity).getModMenuOpacity();
                menuContainer.setAlpha(opacity / 100f);
            }
        }
    }
    
    public void hide() {
        if (!isShowing || overlayView == null) return;
        dismissInlineConfig();
        saveVisibleScrollPosition();
        searchHandler.removeCallbacks(searchRunnable);
        RuntimeConfigView.stop(overlayView.findViewById(R.id.mod_config_content));

        InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
        if (overlayManager != null) {
            overlayManager.setHudEditorMode(false);
            overlayManager.setHudEditorSelectionListener(null);
        }
        
        Runnable performHide = () -> {
            handler.post(() -> {
                try {
                    if (wmParams != null && windowManager != null) {
                        windowManager.removeView(overlayView);
                    } else {
                        ViewGroup rootView = activity.findViewById(android.R.id.content);
                        if (rootView != null) {
                            rootView.removeView(overlayView);
                        }
                    }
                } catch (Exception ignored) {}
                overlayView = null;
                isShowing = false;
            });
        };
        
        View menuContainer = overlayView.findViewById(R.id.mod_menu_container);
        if (menuContainer != null) {
            animateMenuExit(menuContainer, performHide);
        } else {
            performHide.run();
        }
        overlayView.animate().alpha(0f).setDuration(180).start();
    }
    
    public boolean isShowing() {
        return isShowing;
    }

    private static class GroupedMods {
        private final List<UnifiedMod> favorites = new ArrayList<>();
        private final List<UnifiedMod> others = new ArrayList<>();

        void add(UnifiedMod mod, boolean favorite) {
            if (favorite) {
                favorites.add(mod);
            } else {
                others.add(mod);
            }
        }

        void appendTo(List<UnifiedMod> target) {
            sort(favorites);
            sort(others);
            target.addAll(favorites);
            target.addAll(others);
        }

        private void sort(List<UnifiedMod> mods) {
            mods.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        }
    }
}
