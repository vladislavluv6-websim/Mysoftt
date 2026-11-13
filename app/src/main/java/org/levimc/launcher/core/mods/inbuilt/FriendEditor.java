package org.levimc.launcher.core.mods.inbuilt;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Friend list editor: input + Add button + list with remove buttons. Replaces
 * the last text-only field so the friend module has real buttons again.
 */
public final class FriendEditor {
    private static final int TEXT_PRIMARY = 0xFFE2E5EE;
    private static final int TEXT_FAINT = 0xFF6B7080;
    private static final int CONTROL = 0xFF181B24;
    private static final int ACCENT = 0xFF4E83FF;
    private static final int FRIEND_COLOR = 0xFF55FF66;

    private FriendEditor() {
    }

    public static void show(Activity activity, InbuiltModManager manager,
                            UnifiedMod mod, String key) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        final float density = activity.getResources().getDisplayMetrics().density;
        final List<String> names = parse(mod.getConfigValue(key, ""));

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * density);
        root.setPadding(pad, pad / 2, pad, pad / 2);

        LinearLayout inputRow = new LinearLayout(activity);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText input = new EditText(activity);
        input.setHint(R.string.mod_friend_add_hint);
        input.setTextColor(TEXT_PRIMARY);
        input.setHintTextColor(TEXT_FAINT);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        inputRow.addView(input, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button add = new Button(activity);
        add.setText(R.string.mod_friend_add);
        add.setAllCaps(false);
        add.setTextColor(ACCENT);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addParams.setMarginStart((int) (6 * density));
        inputRow.addView(add, addParams);
        root.addView(inputRow);

        ScrollView scroll = new ScrollView(activity);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (240 * density)));
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll);

        Runnable persist = () -> mod.updateConfig(key, join(names));
        final Runnable[] rebuildRef = new Runnable[1];
        Runnable rebuild = () -> {
            list.removeAllViews();
            if (names.isEmpty()) {
                TextView empty = new TextView(activity);
                empty.setText(R.string.mod_friend_empty);
                empty.setTextColor(TEXT_FAINT);
                list.addView(empty);
                return;
            }
            for (String name : new ArrayList<>(names)) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView label = new TextView(activity);
                label.setText(name);
                label.setTextColor(FRIEND_COLOR);
                label.setSingleLine(true);
                label.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(label, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                Button remove = new Button(activity);
                remove.setText("×");
                remove.setAllCaps(false);
                remove.setTextColor(0xFFFF7A86);
                remove.setOnClickListener(v -> {
                    names.remove(name);
                    persist.run();
                    rebuildRef[0].run();
                });
                row.addView(remove, new LinearLayout.LayoutParams(
                        (int) (42 * density), ViewGroup.LayoutParams.WRAP_CONTENT));
                list.addView(row);
            }
        };
        Runnable addAction = () -> {
            boolean changed = false;
            for (String part : input.getText().toString().split("[,;\\n\\r\\t]+")) {
                String name = part.trim();
                if (name.isEmpty() || contains(names, name)) continue;
                names.add(name);
                changed = true;
            }
            input.setText("");
            if (changed) {
                persist.run();
                rebuildRef[0].run();
            }
        };
        add.setOnClickListener(v -> addAction.run());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addAction.run();
                return true;
            }
            return false;
        });
        rebuildRef[0] = rebuild;
        rebuild.run();

        new AlertDialog.Builder(activity)
                .setTitle(R.string.inbuilt_mod_friend)
                .setView(root)
                .setPositiveButton(android.R.string.ok, (d, w) -> persist.run())
                .show();
    }

    private static List<String> parse(String raw) {
        List<String> names = new ArrayList<>();
        if (raw == null) return names;
        for (String part : raw.split("[,;\\n\\r\\t]+")) {
            String name = part.trim();
            if (!name.isEmpty() && !contains(names, name)) names.add(name);
        }
        return names;
    }

    private static boolean contains(List<String> names, String name) {
        for (String existing : names) {
            if (existing.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static String join(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(names.get(i));
        }
        return sb.toString();
    }
}
