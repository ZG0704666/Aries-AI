package G1;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService$TakeScreenshotCallback;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import p2.m;

/* loaded from: classes.dex */
public final class k implements AccessibilityService$TakeScreenshotCallback {

    /* renamed from: a, reason: collision with root package name */
    public final /* synthetic */ String f1276a;

    /* renamed from: b, reason: collision with root package name */
    public final /* synthetic */ String f1277b;

    /* renamed from: c, reason: collision with root package name */
    public final /* synthetic */ m f1278c;

    /* renamed from: d, reason: collision with root package name */
    public final /* synthetic */ CountDownLatch f1279d;

    public k(String str, String str2, m mVar, CountDownLatch countDownLatch) {
        this.f1276a = str;
        this.f1277b = str2;
        this.f1278c = mVar;
        this.f1279d = countDownLatch;
    }

    public final void onFailure(int i3) {
        this.f1278c.f5958d = false;
        this.f1279d.countDown();
    }

    public final void onSuccess(AccessibilityService.ScreenshotResult screenshotResult) {
        Bitmap.CompressFormat compressFormat;
        p2.g.e(screenshotResult, "screenshotResult");
        HardwareBuffer hardwareBuffer = screenshotResult.getHardwareBuffer();
        p2.g.d(hardwareBuffer, "getHardwareBuffer(...)");
        ColorSpace colorSpace = screenshotResult.getColorSpace();
        p2.g.d(colorSpace, "getColorSpace(...)");
        Bitmap bitmapWrapHardwareBuffer = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace);
        hardwareBuffer.close();
        try {
            if (bitmapWrapHardwareBuffer != null) {
                try {
                    File file = new File(this.f1276a);
                    File parentFile = file.getParentFile();
                    if (parentFile != null && !parentFile.exists()) {
                        parentFile.mkdirs();
                    }
                    String str = this.f1277b;
                    int iHashCode = str.hashCode();
                    if (iHashCode != 105441) {
                        if (iHashCode != 111145) {
                            if (iHashCode != 3268712 || !str.equals("jpeg")) {
                            }
                        } else if (str.equals("png")) {
                            compressFormat = Bitmap.CompressFormat.PNG;
                        }
                    } else {
                        compressFormat = !str.equals("jpg") ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
                    }
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    try {
                        this.f1278c.f5958d = bitmapWrapHardwareBuffer.compress(compressFormat, compressFormat == Bitmap.CompressFormat.JPEG ? 90 : 100, fileOutputStream);
                        fileOutputStream.close();
                    } finally {
                    }
                } catch (Exception unused) {
                    this.f1278c.f5958d = false;
                }
                bitmapWrapHardwareBuffer.recycle();
            } else {
                this.f1278c.f5958d = false;
            }
            this.f1279d.countDown();
        } catch (Throwable th) {
            bitmapWrapHardwareBuffer.recycle();
            throw th;
        }
    }
}
