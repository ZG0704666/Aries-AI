package G1;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import com.ai.assistance.operit.provider.UIAccessibilityService;
import java.io.StringWriter;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import org.xmlpull.v1.XmlSerializer;
import p2.m;

/* loaded from: classes.dex */
public final class l extends d {

    /* renamed from: a, reason: collision with root package name */
    public final /* synthetic */ UIAccessibilityService f1280a;

    public l(UIAccessibilityService uIAccessibilityService) {
        this.f1280a = uIAccessibilityService;
    }

    @Override // G1.e
    public final boolean a(int i3, int i4) {
        Log.d("UIAccessibilityService", "准备在 (" + i3 + ", " + i4 + ") 执行长按...");
        Path path = new Path();
        float f3 = (float) i3;
        float f4 = (float) i4;
        path.moveTo(f3, f4);
        path.lineTo(f3, f4);
        return this.f1280a.dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0L, 600L)).build(), new j(1), null);
    }

    @Override // G1.e
    public final boolean b(int i3) {
        return this.f1280a.performGlobalAction(i3);
    }

    @Override // G1.e
    public final boolean c(int i3, int i4) {
        Log.d("UIAccessibilityService", "准备在 (" + i3 + ", " + i4 + ") 执行点击...");
        Path path = new Path();
        float f3 = (float) i3;
        float f4 = (float) i4;
        path.moveTo(f3, f4);
        path.lineTo(f3, f4);
        return this.f1280a.dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0L, 50L)).build(), new j(0), null);
    }

    @Override // G1.e
    public final boolean e() {
        return UIAccessibilityService.h;
    }

    @Override // G1.e
    public final boolean f(String str, String str2) throws Throwable {
        AccessibilityNodeInfo accessibilityNodeInfoA;
        p2.g.e(str, "nodeId");
        p2.g.e(str2, "text");
        Log.d("UIAccessibilityService", "准备为节点 " + str + " 设置文本: '" + str2 + '\'');
        AccessibilityNodeInfo rootInActiveWindow = this.f1280a.getRootInActiveWindow();
        if (rootInActiveWindow == null) {
            Log.w("UIAccessibilityService", "setTextOnNode 失败: rootInActiveWindow is null");
            return false;
        }
        AccessibilityNodeInfo accessibilityNodeInfoB = UIAccessibilityService.b(rootInActiveWindow, str);
        rootInActiveWindow.recycle();
        if (accessibilityNodeInfoB == null) {
            Log.w("UIAccessibilityService", "setTextOnNode 失败: 无法通过ID '" + str + "' 找到目标容器节点");
            return false;
        }
        try {
            accessibilityNodeInfoA = UIAccessibilityService.a(accessibilityNodeInfoB);
        } catch (Throwable th) {
            th = th;
            accessibilityNodeInfoA = null;
        }
        try {
            if (accessibilityNodeInfoA == null) {
                Log.w("UIAccessibilityService", "setTextOnNode 失败: 在节点 " + str + " 及其子节点中未找到可编辑的节点。");
                accessibilityNodeInfoB.recycle();
                return false;
            }
            Bundle bundle = new Bundle();
            bundle.putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", str2);
            boolean zPerformAction = accessibilityNodeInfoA.performAction(2097152, bundle);
            if (!zPerformAction) {
                Rect rect = new Rect();
                accessibilityNodeInfoA.getBoundsInScreen(rect);
                Log.w("UIAccessibilityService", "setTextOnNode: performAction(ACTION_SET_TEXT) 在目标节点上返回 false. 节点信息: class=" + ((Object) accessibilityNodeInfoA.getClassName()) + ", text='" + ((Object) accessibilityNodeInfoA.getText()) + "', bounds=" + rect.toShortString());
            }
            accessibilityNodeInfoB.recycle();
            accessibilityNodeInfoA.recycle();
            return zPerformAction;
        } catch (Throwable th2) {
            th = th2;
            accessibilityNodeInfoB.recycle();
            if (accessibilityNodeInfoA != null) {
                accessibilityNodeInfoA.recycle();
            }
            throw th;
        }
    }

    @Override // G1.e
    public final String g() {
        boolean z3 = UIAccessibilityService.h;
        AccessibilityNodeInfo rootInActiveWindow = this.f1280a.getRootInActiveWindow();
        if (rootInActiveWindow == null) {
            return "";
        }
        XmlSerializer xmlSerializerNewSerializer = Xml.newSerializer();
        StringWriter stringWriter = new StringWriter();
        try {
            xmlSerializerNewSerializer.setOutput(stringWriter);
            xmlSerializerNewSerializer.startDocument("UTF-8", Boolean.TRUE);
            UIAccessibilityService.c(rootInActiveWindow, xmlSerializerNewSerializer);
            xmlSerializerNewSerializer.endDocument();
            String string = stringWriter.toString();
            p2.g.d(string, "toString(...)");
            return string;
        } catch (Exception e3) {
            Log.e("UIAccessibilityService", "生成UI XML时出错", e3);
            return "";
        }
    }

    @Override // G1.e
    public final boolean h(String str, String str2) {
        p2.g.e(str, "path");
        p2.g.e(str2, "format");
        if (Build.VERSION.SDK_INT < 30) {
            return false;
        }
        m mVar = new m();
        String lowerCase = str2.toLowerCase(Locale.ROOT);
        p2.g.d(lowerCase, "toLowerCase(...)");
        UIAccessibilityService uIAccessibilityService = this.f1280a;
        synchronized (uIAccessibilityService.f3846d) {
            long jCurrentTimeMillis = System.currentTimeMillis() - uIAccessibilityService.f3847e;
            if (0 <= jCurrentTimeMillis) {
                long j3 = uIAccessibilityService.f3848f;
                if (jCurrentTimeMillis < j3) {
                    try {
                        Thread.sleep(j3 - jCurrentTimeMillis);
                    } catch (InterruptedException unused) {
                    }
                }
            }
            uIAccessibilityService.f3847e = System.currentTimeMillis();
            CountDownLatch countDownLatch = new CountDownLatch(1);
            uIAccessibilityService.takeScreenshot(0, uIAccessibilityService.getMainExecutor(), new k(str, lowerCase, mVar, countDownLatch));
            try {
                countDownLatch.await();
            } catch (InterruptedException unused2) {
                mVar.f5958d = false;
            }
        }
        return mVar.f5958d;
    }

    @Override // G1.e
    public final boolean i(int i3, int i4, int i5, int i6, long j3) {
        Path path = new Path();
        path.moveTo(i3, i4);
        path.lineTo(i5, i6);
        return this.f1280a.dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0L, j3)).build(), null, null);
    }

    @Override // G1.e
    public final String j() {
        UIAccessibilityService uIAccessibilityService = this.f1280a;
        AccessibilityNodeInfo accessibilityNodeInfoFindFocus = uIAccessibilityService.findFocus(1);
        if (accessibilityNodeInfoFindFocus == null) {
            accessibilityNodeInfoFindFocus = uIAccessibilityService.findFocus(2);
        }
        if (accessibilityNodeInfoFindFocus == null) {
            return null;
        }
        Rect rect = new Rect();
        accessibilityNodeInfoFindFocus.getBoundsInScreen(rect);
        accessibilityNodeInfoFindFocus.recycle();
        return rect.toShortString();
    }
}
