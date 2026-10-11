package in.scrollcount.probe;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

/** Small, touch-through accessibility badge. Display failures must never interrupt counting. */
final class PlayerIndicator {
    private final Context context;
    private final WindowManager windows;
    private TextView badge;
    private int windowId=-1;
    private boolean unavailable;
    private String label="";

    PlayerIndicator(Context context) {
        this.context=context;
        windows=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
    }

    void show(long items) {
        if(unavailable)return;
        String next="●  Scrolls · "+(items>=0?Ui.number(items):"—");
        try {
            if(badge==null) {
                windowId=-1;
                badge=new TextView(context);
                badge.setTextSize(11);
                badge.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
                badge.setTextColor(0xffd7efdf);
                badge.setPadding(dp(11),dp(6),dp(11),dp(6));
                badge.setMaxWidth(context.getResources().getDisplayMetrics().widthPixels-dp(48));
                badge.setGravity(Gravity.CENTER);
                // This status is optional and also available in the app. Never announce every skip.
                badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                GradientDrawable background=new GradientDrawable();
                background.setColor(0xb322352b);
                background.setCornerRadius(dp(24));
                background.setStroke(dp(1),0x3365aa83);
                badge.setBackground(background);
                badge.setText(next);
                WindowManager.LayoutParams params=new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT);
                params.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
                params.y=dp(56);
                params.setTitle("ScrollCount live badge");
                windows.addView(badge,params);
                captureWindowId();
                label=next;
            } else if(!next.equals(label)) {
                badge.setText(next);
                label=next;
            }
        } catch(RuntimeException ignored) {
            hide();
            unavailable=true; // Do not repeatedly attempt a rejected window on every event.
        }
    }

    boolean ownsWindow(int id) {
        try {
            if(badge!=null && windowId<0)captureWindowId();
        } catch(RuntimeException ignored) {
            return false;
        }
        return id>=0 && windowId>=0 && id==windowId;
    }

    private void captureWindowId() {
        if(badge==null || !badge.isAttachedToWindow())return;
        AccessibilityNodeInfo ownNode=badge.createAccessibilityNodeInfo();
        if(ownNode==null)return;
        try {
            if(ownNode.getWindowId()>=0)windowId=ownNode.getWindowId();
        } finally {
            ownNode.recycle();
        }
    }

    void hide() {
        if(badge==null)return;
        try {
            captureWindowId();
            windows.removeViewImmediate(badge);
        } catch(RuntimeException ignored) {
            // A revoked/disconnected accessibility window is already gone.
        } finally {
            badge=null;
            label="";
        }
    }

    private int dp(int value) {
        return Math.round(value*context.getResources().getDisplayMetrics().density);
    }
}
