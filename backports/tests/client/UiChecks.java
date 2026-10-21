package com.nomiss.smoke;

import java.lang.reflect.*;
import java.util.*;

/** Test-only render checks; never included in the production NoMiss JAR. */
public final class UiChecks {
    private static int ticks, stage, frames;
    private static boolean done;
    private static Object expected, original;
    private static Field screenField;
    private static Method setScreen;
    private static final String[] LABELS = {"general", "weapons", "profiles", "hud", "picker-icons", "picker-search"};

    public static void frame(Object screen) { if (screen == expected) frames++; }
    public static void tick(Object client) {
        if (done) return;
        try {
            if (screenField == null) {
                screenField = client.getClass().getDeclaredField(System.getProperty("nomiss.smoke.screenField"));
                screenField.setAccessible(true);
                Class<?> screenClass = Class.forName(System.getProperty("nomiss.smoke.screenClass"));
                setScreen = client.getClass().getDeclaredMethod(System.getProperty("nomiss.smoke.setScreen"), screenClass);
                setScreen.setAccessible(true);
            }
            if (expected == null) {
                if (screenField.get(client) == null || ++ticks < 100) return;
                original = screenField.get(client);
                show(client);
            } else if (frames >= 3) {
                if (screenField.get(client) != expected) throw new AssertionError("Test screen was replaced unexpectedly");
                System.out.println("NOMISS_UI_CASE_PASS " + LABELS[stage]);
                stage++;
                if (stage == LABELS.length) {
                    setScreen.invoke(client, original);
                    done = true;
                    System.out.println("NOMISS_UI_SMOKE_PASS 6");
                } else show(client);
            }
        } catch (Throwable error) {
            done = true;
            System.out.println("NOMISS_UI_SMOKE_FAILED stage=" + stage);
            error.printStackTrace();
        }
    }
    private static void show(Object client) throws Exception {
        Class<?> screenClass = screenField.getType();
        boolean legacy = Boolean.getBoolean("nomiss.smoke.legacy");
        Object screen;
        if (stage < 4) {
            Class<?> type = Class.forName(legacy ? "com.nomiss.legacy.LegacyScreen" : "com.nomiss.client.gui.NoMissScreen");
            if (legacy) {
                screen = type.getConstructor(screenClass).newInstance(original);
                Field tab = type.getDeclaredField("tab"); tab.setAccessible(true); tab.setInt(screen, stage);
            } else {
                Class<?> page = Class.forName("com.nomiss.client.gui.NoMissScreen$Page");
                screen = type.getConstructor(screenClass, page).newInstance(original, page.getEnumConstants()[stage]);
            }
        } else {
            Class<?> type = Class.forName(legacy ? "com.nomiss.legacy.LegacyScreen$Picker" : "com.nomiss.client.gui.ItemPickerScreen");
            Constructor<?> constructor = type.getDeclaredConstructor(screenClass, int.class);
            constructor.setAccessible(true);
            screen = constructor.newInstance(original, 0);
            if (stage == 4 && legacy) checkExactFingerprint(screen);
            if (stage == 5) {
                Field query = type.getDeclaredField("query"); query.setAccessible(true); query.set(screen, "diamond");
            }
        }
        expected = screen; frames = 0;
        setScreen.invoke(client, screen);
        if (stage == 5) {
            Field catalog = screen.getClass().getDeclaredField("catalog"); catalog.setAccessible(true);
            int total = ((List<?>) catalog.get(screen)).size(), count = -1;
            if (legacy) {
                Field visible = screen.getClass().getDeclaredField("visible"); visible.setAccessible(true);
                count = ((List<?>) visible.get(screen)).size();
            } else {
                Field field = screen.getClass().getDeclaredField("itemList"); field.setAccessible(true);
                Object list = field.get(screen);
                for (Class<?> type = list.getClass(); type != Object.class; type = type.getSuperclass()) {
                    for (Field candidate : type.getDeclaredFields()) if (List.class.isAssignableFrom(candidate.getType())) {
                        candidate.setAccessible(true);
                        List<?> values = (List<?>) candidate.get(list);
                        if (values != null && !values.isEmpty() && values.get(0).getClass().getName().endsWith("ItemPickerScreen$ItemEntry")) count = values.size();
                    }
                }
            }
            if (count <= 0 || count >= total) throw new AssertionError("Search did not narrow the item catalog: " + count + "/" + total);
        }
    }
    private static void checkExactFingerprint(Object picker) throws Exception {
        Field catalog = picker.getClass().getDeclaredField("catalog"); catalog.setAccessible(true);
        Object choice = ((List<?>) catalog.get(picker)).get(0);
        Method icon = choice.getClass().getDeclaredMethod("icon"); icon.setAccessible(true);
        Object stack = icon.invoke(choice);
        Method fingerprint = Class.forName("com.nomiss.legacy.LegacyMatcher").getMethod("fingerprint", stack.getClass());
        Object original = fingerprint.invoke(null, stack);
        if (((Optional<?>) original).isEmpty()) throw new AssertionError("No exact-item signature");
        stack.getClass().getMethod(System.getProperty("nomiss.smoke.setDamage"), int.class).invoke(stack, 3);
        if (!original.equals(fingerprint.invoke(null, stack))) throw new AssertionError("Durability changed exact match");
        stack.getClass().getMethod(System.getProperty("nomiss.smoke.setRepairCost"), int.class).invoke(stack, 7);
        if (!original.equals(fingerprint.invoke(null, stack))) throw new AssertionError("Repair cost changed exact match");
        stack.getClass().getMethod(System.getProperty("nomiss.smoke.setCustomName"), String.class).invoke(stack, "NoMiss smoke rename");
        if (original.equals(fingerprint.invoke(null, stack))) throw new AssertionError("Rename did not change exact match");
        System.out.println("NOMISS_EXACT_SMOKE_PASS durability repair_cost rename");
    }

}
