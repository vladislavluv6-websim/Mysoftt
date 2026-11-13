package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.UnifiedMod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ModMenuAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_GROUP = 0;
    private static final int VIEW_TYPE_MOD = 1;
    private static final int VIEW_TYPE_MOD_COMPACT = 2;

    private final List<MenuItem> items = new ArrayList<>();
    private final Map<String, Boolean> toggleStates = new HashMap<>();
    private final Map<String, Boolean> favoriteStates = new HashMap<>();
    private OnModActionListener listener;
    private boolean compactMode;

    public ModMenuAdapter() {
        setHasStableIds(true);
    }

    public interface OnModActionListener {
        void onToggle(UnifiedMod mod, boolean enabled);
        void onConfig(UnifiedMod mod);
        void onFavoriteChanged(UnifiedMod mod, boolean favorite);
    }

    public void setOnModActionListener(OnModActionListener listener) {
        this.listener = listener;
    }

    public void setCompactMode(boolean compactMode) {
        if (this.compactMode == compactMode) return;
        this.compactMode = compactMode;
        items.clear();
        toggleStates.clear();
        favoriteStates.clear();
        notifyDataSetChanged();
    }

    public void updateMods(List<UnifiedMod> mods, Set<String> favoriteKeys) {
        List<MenuItem> nextItems = new ArrayList<>();
        Map<String, Boolean> nextToggleStates = new HashMap<>();
        Map<String, Boolean> nextFavoriteStates = new HashMap<>();
        String lastGroupId = null;
        for (UnifiedMod mod : mods) {
            boolean firstInGroup = !Objects.equals(mod.getGroupId(), lastGroupId);
            if (!compactMode && firstInGroup) {
                nextItems.add(MenuItem.group(mod.getGroupId(), mod.getGroupName()));
            }
            boolean showGroupLabel = compactMode
                && firstInGroup
                && mod.getSource() == UnifiedMod.Source.EXTERNAL;
            nextItems.add(MenuItem.mod(mod, showGroupLabel));
            lastGroupId = mod.getGroupId();
            nextToggleStates.put(mod.getStableKey(), mod.isEnabled());
            nextFavoriteStates.put(mod.getStableKey(),
                favoriteKeys != null && favoriteKeys.contains(mod.getStableKey()));
        }

        List<MenuItem> oldItems = new ArrayList<>(items);
        Map<String, Boolean> oldToggleStates = new HashMap<>(toggleStates);
        Map<String, Boolean> oldFavoriteStates = new HashMap<>(favoriteStates);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return nextItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).stableKey()
                    .equals(nextItems.get(newItemPosition).stableKey());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                MenuItem oldItem = oldItems.get(oldItemPosition);
                MenuItem newItem = nextItems.get(newItemPosition);
                if (oldItem.isGroup() || newItem.isGroup()) {
                    return oldItem.isGroup() == newItem.isGroup()
                        && Objects.equals(oldItem.groupName, newItem.groupName);
                }
                String oldKey = oldItem.mod.getStableKey();
                String newKey = newItem.mod.getStableKey();
                return Objects.equals(oldItem.mod.getName(), newItem.mod.getName())
                    && Objects.equals(oldItem.mod.getDescription(), newItem.mod.getDescription())
                    && oldItem.mod.hasConfig() == newItem.mod.hasConfig()
                    && oldItem.showGroupLabel == newItem.showGroupLabel
                    && oldToggleStates.getOrDefault(oldKey, false)
                        .equals(nextToggleStates.getOrDefault(newKey, false))
                    && oldFavoriteStates.getOrDefault(oldKey, false)
                        .equals(nextFavoriteStates.getOrDefault(newKey, false));
            }
        }, false);

        items.clear();
        items.addAll(nextItems);
        toggleStates.clear();
        toggleStates.putAll(nextToggleStates);
        favoriteStates.clear();
        favoriteStates.putAll(nextFavoriteStates);
        diff.dispatchUpdatesTo(this);
    }

    public boolean isGroupHeader(int position) {
        return position >= 0 && position < items.size() && items.get(position).isGroup();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_GROUP) {
            TextView title = new TextView(parent.getContext());
            float density = parent.getResources().getDisplayMetrics().density;
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (34 * density));
            params.setMargins((int) (8 * density), (int) (6 * density),
                (int) (8 * density), 0);
            title.setLayoutParams(params);
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setPadding((int) (6 * density), 0, (int) (6 * density), 0);
            title.setTextColor(0xFF4E83FF);
            title.setTextSize(10);
            title.setTypeface(null, Typeface.BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            return new GroupViewHolder(title);
        }

        int layout = viewType == VIEW_TYPE_MOD_COMPACT
            ? R.layout.item_mod_menu_compact
            : R.layout.item_mod_menu_card;
        // MinecraftActivity runs on an AppCompat theme. Any Material widget in an
        // item layout would throw "requires Theme.MaterialComponents" during the
        // RecyclerView layout pass (outside the menu's own try/catch). Inflate the
        // card with a Material theme so the menu cannot crash on the game activity.
        Context themed = new androidx.appcompat.view.ContextThemeWrapper(
            parent.getContext(),
            com.google.android.material.R.style.Theme_MaterialComponents_NoActionBar);
        View v = LayoutInflater.from(themed).inflate(layout, parent, false);
        return new ModViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MenuItem item = items.get(position);
        if (item.isGroup()) {
            ((GroupViewHolder) holder).title.setText(item.groupName);
            return;
        }

        ModViewHolder modHolder = (ModViewHolder) holder;
        UnifiedMod mod = item.mod;

        if (modHolder.name == null || modHolder.statusText == null ||
                modHolder.favoriteBtn == null || modHolder.configBtn == null) {
            return;
        }
        modHolder.name.setText(mod.getName());
        if (modHolder.descText != null) {
            String desc = mod.getDescription();
            modHolder.descText.setText(desc == null ? "" : desc);
            modHolder.descText.setVisibility(desc == null || desc.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (modHolder.groupText != null) {
            modHolder.groupText.setVisibility(item.showGroupLabel ? View.VISIBLE : View.GONE);
            if (item.showGroupLabel) {
                modHolder.groupText.setText(mod.getGroupName());
            }
        }

        boolean isEnabled = toggleStates.getOrDefault(mod.getStableKey(), false);
        if (modHolder.statusText instanceof Switch) {
            ((Switch) modHolder.statusText).setOnCheckedChangeListener(null);
        }
        updateStatusView(modHolder, isEnabled);
        updateFavoriteView(modHolder, favoriteStates.getOrDefault(mod.getStableKey(), false));

        View.OnClickListener toggleClick = v ->
            toggleMod(mod, !toggleStates.getOrDefault(mod.getStableKey(), false), modHolder);

        modHolder.itemView.setOnClickListener(toggleClick);
        if (modHolder.statusText instanceof Switch) {
            ((Switch) modHolder.statusText).setOnCheckedChangeListener((btn, checked) -> {
                if (checked != toggleStates.getOrDefault(mod.getStableKey(), false)) {
                    toggleMod(mod, checked, modHolder);
                }
            });
        } else {
            modHolder.statusText.setOnClickListener(toggleClick);
        }

        if (mod.hasConfig()) {
            modHolder.itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onConfig(mod);
                return true;
            });
        } else {
            modHolder.itemView.setOnLongClickListener(null);
        }

        modHolder.favoriteBtn.setOnClickListener(v -> {
            boolean favorite = !favoriteStates.getOrDefault(mod.getStableKey(), false);
            favoriteStates.put(mod.getStableKey(), favorite);
            updateFavoriteView(modHolder, favorite);
            if (listener != null) {
                listener.onFavoriteChanged(mod, favorite);
            }
        });

        if (mod.hasConfig()) {
            modHolder.configBtn.setVisibility(View.VISIBLE);
            modHolder.configBtn.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConfig(mod);
                }
            });
        } else {
            modHolder.configBtn.setVisibility(View.GONE);
        }

        updateCardState(modHolder, isEnabled);
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position).isGroup()) return VIEW_TYPE_GROUP;
        return compactMode ? VIEW_TYPE_MOD_COMPACT : VIEW_TYPE_MOD;
    }

    private void toggleMod(UnifiedMod mod, boolean newState, ModViewHolder holder) {
        toggleStates.put(mod.getStableKey(), newState);
        updateStatusView(holder, newState);
        if (listener != null) {
            listener.onToggle(mod, newState);
        }
    }

    private void updateFavoriteView(ModViewHolder holder, boolean favorite) {
        int color = favorite ? 0xFFFFC94D : 0xFF727279;
        holder.favoriteBtn.setImageTintList(ColorStateList.valueOf(color));
        holder.favoriteBtn.setAlpha(favorite ? 1f : 0.9f);
        holder.favoriteBtn.setContentDescription(holder.favoriteBtn.getContext().getString(
            favorite ? R.string.mod_menu_unfavorite : R.string.mod_menu_favorite));
    }

    private void updateStatusView(ModViewHolder holder, boolean enabled) {
        if (holder.statusText instanceof Switch) {
            ((Switch) holder.statusText).setChecked(enabled);
        } else if (enabled) {
            holder.statusText.setText(R.string.mod_status_enabled);
            holder.statusText.setTextColor(0xFF4E83FF);
            holder.statusText.setBackgroundResource(R.drawable.bg_zenix_status_on);
        } else {
            holder.statusText.setText(R.string.mod_status_disabled);
            holder.statusText.setTextColor(0xFF85888E);
            holder.statusText.setBackgroundResource(R.drawable.bg_zenix_status_off);
        }
        updateCardState(holder, enabled);
    }

    private void updateCardState(ModViewHolder holder, boolean enabled) {
        holder.itemView.setAlpha(1f);
        holder.name.setTextColor(enabled ? 0xFFFFFFFF : 0xFFC9C9C9);
        if (holder.indicator != null) {
            holder.indicator.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
        }

        if (holder.itemView instanceof androidx.cardview.widget.CardView) {
            androidx.cardview.widget.CardView cv = (androidx.cardview.widget.CardView) holder.itemView;
            cv.setCardBackgroundColor(enabled ? 0xFF241C2E : 0xFF1B1B1B);
            cv.setCardElevation(0f);
        }
    }


    @Override
    public long getItemId(int position) {
        String key = items.get(position).stableKey();
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static class MenuItem {
        final String groupId;
        final String groupName;
        final UnifiedMod mod;
        final boolean showGroupLabel;

        private MenuItem(String groupId, String groupName, UnifiedMod mod, boolean showGroupLabel) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.mod = mod;
            this.showGroupLabel = showGroupLabel;
        }

        static MenuItem group(String groupId, String groupName) {
            return new MenuItem(groupId, groupName, null, false);
        }

        static MenuItem mod(UnifiedMod mod, boolean showGroupLabel) {
            return new MenuItem(null, null, mod, showGroupLabel);
        }

        boolean isGroup() {
            return mod == null;
        }

        String stableKey() {
            return isGroup() ? "group:" + groupId : "mod:" + mod.getStableKey();
        }
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        TextView title;

        GroupViewHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView;
        }
    }

    static class ModViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView statusText;
        TextView groupText;
        TextView descText;
        ImageButton favoriteBtn;
        ImageButton configBtn;
        View indicator;

        ModViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.mod_card_name);
            statusText = itemView.findViewById(R.id.mod_card_status);
            groupText = itemView.findViewById(R.id.mod_card_group);
            descText = itemView.findViewById(R.id.mod_card_desc);
            favoriteBtn = itemView.findViewById(R.id.mod_card_favorite);
            configBtn = itemView.findViewById(R.id.mod_card_config);
            indicator = itemView.findViewById(R.id.mod_card_indicator);
        }
    }
}
