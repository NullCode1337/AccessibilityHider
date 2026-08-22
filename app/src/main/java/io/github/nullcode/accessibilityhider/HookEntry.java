package io.github.nullcode.accessibilityhider;

import android.content.ContentResolver;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.util.Collections;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

/**
 * Runs once per process for every app selected in this module's LSPosed
 * scope (including, optionally, "Android System" / system_server if the
 * caller reads accessibility state from there)
 *
 * What gets hidden, for any hooked process:
 *   - AccessibilityManager#getEnabledAccessibilityServiceList(int)
 *   - AccessibilityManager#getInstalledAccessibilityServiceList()
 *   - AccessibilityManager#isEnabled()
 *   - AccessibilityManager#isTouchExplorationEnabled()
 *   - Settings.Secure ENABLED_ACCESSIBILITY_SERVICES (getString)
 *   - Settings.Secure ACCESSIBILITY_ENABLED (getInt/getString)
 *   - Settings.Secure TOUCH_EXPLORATION_ENABLED (getInt/getString)
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "AccessibilityHider";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        hookAccessibilityManager();
        hookSettingsSecure();
    }

    private void hookAccessibilityManager() {
        hookReturn(AccessibilityManager.class, "getEnabledAccessibilityServiceList",
                new Class<?>[]{int.class}, Collections.emptyList());

        hookReturn(AccessibilityManager.class, "getInstalledAccessibilityServiceList",
                new Class<?>[]{}, Collections.emptyList());

        hookReturn(AccessibilityManager.class, "isEnabled",
                new Class<?>[]{}, Boolean.FALSE);

        hookReturn(AccessibilityManager.class, "isTouchExplorationEnabled",
                new Class<?>[]{}, Boolean.FALSE);
    }

    private void hookReturn(Class<?> target, String method, Class<?>[] params, Object value) {
        try {
            Object[] args = new Object[params.length + 1];
            System.arraycopy(params, 0, args, 0, params.length);
            args[params.length] = new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return value;
                }
            };
            findAndHookMethod(target, method, args);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": couldn't hook " + target.getSimpleName() + "#" + method + " -> " + t);
        }
    }

    private void hookSettingsSecure() {
        XC_MethodHook secureStringHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String key = (String) param.args[1];
                if (Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES.equals(key)) {
                    param.setResult("");
                } else if (Settings.Secure.ACCESSIBILITY_ENABLED.equals(key)
                        || Settings.Secure.TOUCH_EXPLORATION_ENABLED.equals(key)) {
                    param.setResult("0");
                }
            }
        };

        XC_MethodHook secureIntHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String key = (String) param.args[1];
                if (Settings.Secure.ACCESSIBILITY_ENABLED.equals(key)
                        || Settings.Secure.TOUCH_EXPLORATION_ENABLED.equals(key)) {
                    param.setResult(0);
                }
            }
        };

        try {
            findAndHookMethod(Settings.Secure.class, "getString",
                    ContentResolver.class, String.class, secureStringHook);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": couldn't hook Settings.Secure#getString -> " + t);
        }

        try {
            findAndHookMethod(Settings.Secure.class, "getInt",
                    ContentResolver.class, String.class, secureIntHook);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": couldn't hook Settings.Secure#getInt(2-arg) -> " + t);
        }

        try {
            findAndHookMethod(Settings.Secure.class, "getInt",
                    ContentResolver.class, String.class, int.class, secureIntHook);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": couldn't hook Settings.Secure#getInt(3-arg) -> " + t);
        }
    }
}
