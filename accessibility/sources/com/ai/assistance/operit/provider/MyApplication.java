package com.ai.assistance.operit.provider;

import Q1.d;
import Q1.e;
import android.app.Application;

/* loaded from: classes.dex */
public final class MyApplication extends Application {
    @Override // android.app.Application
    public final void onCreate() {
        super.onCreate();
        int[] iArr = e.f2193a;
        registerActivityLifecycleCallbacks(new d());
    }
}
