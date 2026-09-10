package androidx.appcompat.app;

// Minimal compile-time stub so the offline verify-logic.ps1 Java check can
// type-check MainActivity against android.jar without AndroidX jars.
public abstract class AppCompatDelegate {
    public static final int MODE_NIGHT_FOLLOW_SYSTEM = -1;
    public static final int MODE_NIGHT_NO = 1;
    public static final int MODE_NIGHT_YES = 2;

    public static void setDefaultNightMode(int mode) {
    }
}
