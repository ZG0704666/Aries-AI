package G1;

import android.util.Log;
import com.ai.assistance.operit.provider.UIAccessibilityService;

/* loaded from: classes.dex */
public final class h extends d {
    @Override // G1.e
    public final boolean a(int i3, int i4) {
        if (!UIAccessibilityService.h) {
            Log.w("RemoteBinderService", "performLongPress: Accessibility Service not connected.");
            return false;
        }
        l lVar = UIAccessibilityService.f3844i;
        if (lVar != null) {
            return lVar.a(i3, i4);
        }
        return false;
    }

    @Override // G1.e
    public final boolean b(int i3) {
        if (!UIAccessibilityService.h) {
            Log.w("RemoteBinderService", "performGlobalAction: Accessibility Service not connected.");
            return false;
        }
        l lVar = UIAccessibilityService.f3844i;
        if (lVar != null) {
            return lVar.f1280a.performGlobalAction(i3);
        }
        return false;
    }

    @Override // G1.e
    public final boolean c(int i3, int i4) {
        if (!UIAccessibilityService.h) {
            Log.w("RemoteBinderService", "performClick: Accessibility Service not connected.");
            return false;
        }
        l lVar = UIAccessibilityService.f3844i;
        if (lVar != null) {
            return lVar.c(i3, i4);
        }
        return false;
    }

    @Override // G1.d, G1.e
    public final String d() {
        if (UIAccessibilityService.h) {
            return UIAccessibilityService.f3845j;
        }
        Log.w("RemoteBinderService", "getCurrentActivityName: Accessibility Service not connected.");
        return "";
    }

    @Override // G1.e
    public final boolean e() {
        return UIAccessibilityService.h;
    }

    @Override // G1.e
    public final boolean f(String str, String str2) {
        p2.g.e(str, "nodeId");
        p2.g.e(str2, "text");
        if (!UIAccessibilityService.h) {
            Log.w("RemoteBinderService", "setTextOnNode: Accessibility Service not connected.");
            return false;
        }
        l lVar = UIAccessibilityService.f3844i;
        if (lVar != null) {
            return lVar.f(str, str2);
        }
        return false;
    }

    @Override // G1.e
    public final String g() {
        if (!UIAccessibilityService.h) {
            Log.w("RemoteBinderService", "getUiHierarchy: Accessibility Service not connected.");
            return "";
        }
        l lVar = UIAccessibilityService.f3844i;
        String strG = lVar != null ? lVar.g() : null;
        return strG == null ? "" : strG;
    }

    @Override // G1.e
    public final boolean h(String str, String str2) {
        p2.g.e(str, "path");
        p2.g.e(str2, "format");
        if (!UIAccessibilityService.h) {
            Log.w("RemoteBinderService", "takeScreenshot: Accessibility Service not connected.");
            return false;
        }
        l lVar = UIAccessibilityService.f3844i;
        if (lVar != null) {
            return lVar.h(str, str2);
        }
        return false;
    }

    @Override // G1.e
    public final boolean i(int i3, int i4, int i5, int i6, long j3) {
        if (!UIAccessibilityService.h) {
            Log.w("RemoteBinderService", "performSwipe: Accessibility Service not connected.");
            return false;
        }
        l lVar = UIAccessibilityService.f3844i;
        if (lVar != null) {
            return lVar.i(i3, i4, i5, i6, j3);
        }
        return false;
    }

    @Override // G1.e
    public final String j() {
        if (!UIAccessibilityService.h) {
            Log.w("RemoteBinderService", "findFocusedNodeId: Accessibility Service not connected.");
            return null;
        }
        l lVar = UIAccessibilityService.f3844i;
        if (lVar != null) {
            return lVar.j();
        }
        return null;
    }
}
