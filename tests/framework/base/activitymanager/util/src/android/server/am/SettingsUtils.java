package android.server.am;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.Settings.SettingNotFoundException;
import android.support.test.InstrumentationRegistry;

import java.util.function.Function;

/**
 * Helper methods to get, put, and delete system-level device preferences.
 *
 * To use this helper, a test APK must be self-instrumented and have
 * {@link android.Manifest.permission#WRITE_SECURE_SETTINGS} permission.
 */
final class SettingsUtils {

    @FunctionalInterface
    interface IntGetter {
        int get(ContentResolver cr, String key) throws SettingNotFoundException;
    }

    @FunctionalInterface
    interface FloatGetter {
        float get(ContentResolver cr, String key) throws SettingNotFoundException;
    }

    @FunctionalInterface
    interface IntSetter {
        void put(ContentResolver cr, String key, int value);
    }

    @FunctionalInterface
    interface FloatSetter {
        void put(ContentResolver cr, String key, float value);
    }

    @FunctionalInterface
    interface SettingsSetter<T> {
        void put(ContentResolver cr, String key, T value);
    }

    static int getIntSettings(IntGetter getter, String key) throws SettingNotFoundException {
        return getter.get(getContentResolver(), key);
    }

    static float getFloatSettings(FloatGetter getter, String key) throws SettingNotFoundException {
        return getter.get(getContentResolver(), key);
    }

    static void putSettings(IntSetter setter, String key, int value) {
        setter.put(getContentResolver(), key, value);
    }

    static void putSettings(FloatSetter setter, String key, float value) {
        setter.put(getContentResolver(), key, value);
    }

    static <T> void putSettings(SettingsSetter<T> setter, String key, T value) {
        setter.put(getContentResolver(), key, value);
    }

    static void deleteSettings(Function<String,Uri> getUriFor, String key) throws RemoteException {
        getContentResolver()
                .delete(getUriFor.apply(key), null, null);
    }

    private static ContentResolver getContentResolver() {
        return InstrumentationRegistry.getTargetContext().getContentResolver();
    }
}
