package com.ai.assistance.shower.wrappers;

import com.ai.assistance.shower.shell.FakeContext;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class ServiceManager {

    private static final Method GET_SERVICE_METHOD;

    static {
        try {
            GET_SERVICE_METHOD = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static ActivityManager activityManager;
    private static WindowManager windowManager;

    private ServiceManager() {
    }

    static IInterface getService(String service, String type) {
        try {
            IBinder binder = (IBinder) GET_SERVICE_METHOD.invoke(null, service);
            Method asInterfaceMethod = Class.forName(type + "$Stub").getMethod("asInterface", IBinder.class);
            return (IInterface) asInterfaceMethod.invoke(null, binder);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static ActivityManager getActivityManager() {
        if (activityManager == null) {
            activityManager = ActivityManager.create();
        }
        return activityManager;
    }

    public static WindowManager getWindowManager() {
        if (windowManager == null) {
            windowManager = WindowManager.create();
        }
        return windowManager;
    }
}
