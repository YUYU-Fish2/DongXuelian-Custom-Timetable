package androidx.appcompat.widget;

// Minimal compile-time stub so the offline verify-logic.ps1 Java check can
// type-check MainActivity against android.jar without AndroidX jars.
public class SwitchCompat extends android.widget.CompoundButton {
    public SwitchCompat(android.content.Context context) {
        super(context);
    }

    // androidx 提供的 thumb / track 染色入口（真 SDK 有，存根补上以便离线编译）。
    public void setThumbTintList(android.content.res.ColorStateList tint) {
    }

    public void setTrackTintList(android.content.res.ColorStateList tint) {
    }
}
