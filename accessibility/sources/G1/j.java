package G1;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.util.Log;

/* loaded from: classes.dex */
public final class j extends AccessibilityService.GestureResultCallback {

    /* renamed from: a, reason: collision with root package name */
    public final /* synthetic */ int f1275a;

    @Override // android.accessibilityservice.AccessibilityService.GestureResultCallback
    public final void onCancelled(GestureDescription gestureDescription) {
        switch (this.f1275a) {
            case 0:
                super.onCancelled(gestureDescription);
                Log.w("UIAccessibilityService", "手势被取消。");
                break;
            default:
                super.onCancelled(gestureDescription);
                Log.w("UIAccessibilityService", "长按手势被取消。");
                break;
        }
    }

    @Override // android.accessibilityservice.AccessibilityService.GestureResultCallback
    public final void onCompleted(GestureDescription gestureDescription) {
        switch (this.f1275a) {
            case 0:
                super.onCompleted(gestureDescription);
                Log.i("UIAccessibilityService", "手势已成功完成。");
                break;
            default:
                super.onCompleted(gestureDescription);
                Log.i("UIAccessibilityService", "长按手势已成功完成。");
                break;
        }
    }
}
