package androidx.appcompat.app;

import android.content.DialogInterface;

// Minimal compile-time stub so the offline verify-logic.ps1 Java check can
// type-check MainActivity against android.jar without AndroidX jars.
public class AlertDialog extends android.app.Dialog {
    public static final int BUTTON_POSITIVE = -1;
    public static final int BUTTON_NEGATIVE = -2;
    public static final int BUTTON_NEUTRAL = -3;

    public AlertDialog(android.content.Context context) {
        super(context);
    }

    public android.widget.Button getButton(int whichButton) {
        return null;
    }

    public void setButton(int whichButton, CharSequence text, DialogInterface.OnClickListener listener) {
    }
}
