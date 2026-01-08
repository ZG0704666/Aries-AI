package com.ai.assistance.operit.provider;

import G1.h;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import p2.g;

/* loaded from: classes.dex */
public final class RemoteBinderService extends Service {

    /* renamed from: d, reason: collision with root package name */
    public h f3843d;

    @Override // android.app.Service
    public final IBinder onBind(Intent intent) {
        g.e(intent, "intent");
        h hVar = this.f3843d;
        if (hVar != null) {
            return hVar;
        }
        g.h("proxyBinder");
        throw null;
    }

    @Override // android.app.Service
    public final void onCreate() {
        super.onCreate();
        this.f3843d = new h();
    }
}
