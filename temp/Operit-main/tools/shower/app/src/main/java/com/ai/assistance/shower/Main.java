package com.ai.assistance.shower;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.view.Surface;

import com.ai.assistance.shower.shell.FakeContext;
import com.ai.assistance.shower.shell.Workarounds;
import com.ai.assistance.shower.wrappers.ServiceManager;
import com.ai.assistance.shower.wrappers.WindowManager;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local WebSocket server which can create a virtual display via reflection and
 * stream frames back to the client.
 */
public class Main {

    private static final String TAG = "ShowerMain";
    private static final int DEFAULT_PORT = 8986;
    private static final int DEFAULT_BIT_RATE = 4_000_000;

    private static final String ACTION_SHOWER_BINDER_READY = "com.ai.assistance.operit.action.SHOWER_BINDER_READY";
    private static final String EXTRA_BINDER_CONTAINER = "binder_container";

    private static final int VIRTUAL_DISPLAY_FLAG_PUBLIC = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private static final int VIRTUAL_DISPLAY_FLAG_PRESENTATION = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
    private static final int VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 << 6;
    private static final int VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT = 1 << 7;
    private static final int VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private static final int VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 << 9;
    private static final int VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 << 10;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED = 1 << 12;
    private static final int VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED = 1 << 13;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_FOCUS = 1 << 14;
    private static final int VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP = 1 << 15;

    private static Main sInstance;

    private final Context appContext;

    // Map of displayId -> DisplaySession
    private final Map<Integer, DisplaySession> displays = new ConcurrentHashMap<>();

    private static final long CLIENT_IDLE_TIMEOUT_MS = 15_000L;
    private volatile long lastClientActiveTime = System.currentTimeMillis();
    private Thread idleWatcherThread;

    private static PrintWriter fileLog;
    private static final SimpleDateFormat LOG_TIME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    // 当通过 main(String...) 以 CLI 方式启动时为 true，用于在服务停止后退出整个进程
    private static volatile boolean sExitOnStop = false;

    static synchronized void logToFile(String msg, Throwable t) {
        try {
            if (fileLog == null) {
                File logFile = new File("/data/local/tmp/shower.log");
                fileLog = new PrintWriter(new FileWriter(logFile, true), true);
            }
            long now = System.currentTimeMillis();
            String timestamp = LOG_TIME_FORMAT.format(new Date(now));
            String line = timestamp + " " + msg;
            fileLog.println(line);
            if (t != null) {
                t.printStackTrace(fileLog);
            }
        } catch (IOException e) {
            // For debugging: also print to stderr so we can see why the log file is not created.
            e.printStackTrace();
        }
    }

    private class DisplaySession {
        final int displayId;
        final VirtualDisplay virtualDisplay;
        final MediaCodec videoEncoder;
        final Surface encoderSurface;
        final Thread encoderThread;
        volatile boolean encoderRunning;
        final InputController inputController;
        IShowerVideoSink videoSink;
        IBinder videoSinkBinder;
        IBinder.DeathRecipient videoSinkDeathRecipient;
        final Object lock = new Object();

        DisplaySession(int displayId, VirtualDisplay virtualDisplay, MediaCodec videoEncoder, Surface encoderSurface, InputController inputController) {
            this.displayId = displayId;
            this.virtualDisplay = virtualDisplay;
            this.videoEncoder = videoEncoder;
            this.encoderSurface = encoderSurface;
            this.inputController = inputController;
            this.encoderRunning = true;
            this.encoderThread = new Thread(this::encodeLoop, "ShowerEncoder-" + displayId);
            this.encoderThread.start();
        }

        void release() {
            logToFile("Releasing display session " + displayId, null);
            stopEncoder();
            
            if (virtualDisplay != null) {
                virtualDisplay.release();
            }
            
            if (inputController != null) {
                // Reset input controller display ID, though it matters less as we discard it
                inputController.setDisplayId(0);
            }
            
            setVideoSink(null);
        }

        private void stopEncoder() {
            encoderRunning = false;
            MediaCodec codec = videoEncoder;
            if (codec != null) {
                try {
                    codec.signalEndOfInputStream();
                } catch (Exception e) {
                    logToFile("signalEndOfInputStream failed: " + e.getMessage(), e);
                }
            }
            if (encoderThread != null) {
                try {
                    encoderThread.join(1000);
                } catch (InterruptedException e) {
                    logToFile("Encoder thread join interrupted: " + e.getMessage(), e);
                    Thread.currentThread().interrupt();
                }
            }
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception e) {
                    logToFile("Error stopping codec: " + e.getMessage(), e);
                }
                codec.release();
            }
            if (encoderSurface != null) {
                encoderSurface.release();
            }
        }

        void setVideoSink(IBinder sink) {
            synchronized (lock) {
                if (videoSinkBinder != null && videoSinkBinder != sink && videoSinkDeathRecipient != null) {
                    try {
                        videoSinkBinder.unlinkToDeath(videoSinkDeathRecipient, 0);
                    } catch (Throwable t) {
                        logToFile("unlinkToDeath previous video sink failed: " + t.getMessage(), t);
                    }
                    videoSinkBinder = null;
                    videoSinkDeathRecipient = null;
                }
                if (sink == null) {
                    videoSink = null;
                    videoSinkBinder = null;
                    videoSinkDeathRecipient = null;
                    return;
                }
                videoSinkBinder = sink;
                videoSinkDeathRecipient = () -> {
                    synchronized (lock) {
                        logToFile("Video sink binder died, clearing sink for display " + displayId, null);
                        videoSink = null;
                        videoSinkBinder = null;
                        videoSinkDeathRecipient = null;
                    }
                };
                try {
                    sink.linkToDeath(videoSinkDeathRecipient, 0);
                } catch (Throwable t) {
                    logToFile("linkToDeath for video sink failed: " + t.getMessage(), t);
                }
                videoSink = IShowerVideoSink.Stub.asInterface(sink);
            }
        }

        private void encodeLoop() {
            MediaCodec codec = videoEncoder;
            if (codec == null) {
                return;
            }

            BufferInfo bufferInfo = new BufferInfo();

            while (encoderRunning) {
                int index;
                try {
                    index = codec.dequeueOutputBuffer(bufferInfo, 10_000);
                } catch (IllegalStateException e) {
                    logToFile("dequeueOutputBuffer failed: " + e.getMessage(), e);
                    break;
                }

                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue;
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = codec.getOutputFormat();
                    trySendConfig(format);
                } else if (index >= 0) {
                    if (bufferInfo.size > 0) {
                        ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                        if (outputBuffer != null) {
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                            byte[] data = new byte[bufferInfo.size];
                            outputBuffer.get(data);
                            sendVideoFrame(data);
                        }
                    }
                    codec.releaseOutputBuffer(index, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }
        }

        private void trySendConfig(MediaFormat format) {
            ByteBuffer csd0 = format.getByteBuffer("csd-0");
            ByteBuffer csd1 = format.getByteBuffer("csd-1");
            sendVideoFrame(csd0);
            sendVideoFrame(csd1);
        }

        private void sendVideoFrame(ByteBuffer buffer) {
            if (buffer == null || !buffer.hasRemaining()) {
                return;
            }
            ByteBuffer dup = buffer.duplicate();
            dup.position(0);
            byte[] data = new byte[dup.remaining()];
            dup.get(data);
            sendVideoFrame(data);
        }

        private void sendVideoFrame(byte[] data) {
            IShowerVideoSink sink;
             synchronized (lock) {
                 sink = videoSink;
             }
            if (sink != null) {
                try {
                    sink.onVideoFrame(data);
                } catch (Exception e) {
                    // Client may have died, invalidate the sink.
                    synchronized (lock) {
                        videoSink = null;
                    }
                }
            }
        }
    }

    private byte[] captureScreenshotBytes(int displayId) {
        String path = "/data/local/tmp/shower_screenshot_" + displayId + ".png";
        String cmd = "screencap -d " + displayId + " -p " + path;
        Process proc = null;
        try {
            logToFile("captureScreenshotBytes executing: " + cmd, null);
            proc = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            int exit = proc.waitFor();
            if (exit != 0) {
                logToFile("captureScreenshotBytes screencap exited with code=" + exit, null);
                return null;
            }

            File f = new File(path);
            if (!f.exists() || f.length() == 0) {
                logToFile("captureScreenshotBytes file missing or empty: " + path, null);
                return null;
            }

            byte[] data;
            try (FileInputStream fis = new FileInputStream(f)) {
                data = new byte[(int) f.length()];
                int read = fis.read(data);
                if (read != data.length) {
                    logToFile("captureScreenshotBytes short read: " + read + " / " + data.length, null);
                }
            }
            return data;
        } catch (Exception e) {
            logToFile("captureScreenshotBytes failed: " + e.getMessage(), e);
            return null;
        } finally {
            if (proc != null) {
                try {
                    proc.destroy();
                } catch (Exception ignored) {
                }
            }
            // Cleanup file
            new File(path).delete();
        }
    }


    private void markClientActive() {
        lastClientActiveTime = System.currentTimeMillis();
    }

    private void startIdleWatcher() {
        idleWatcherThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    // Check if *any* display has a sink
                    boolean hasActiveSink = false;
                    for (DisplaySession session : displays.values()) {
                        synchronized(session.lock) {
                            if (session.videoSink != null) {
                                hasActiveSink = true;
                                break;
                            }
                        }
                    }
                    
                    if (!hasActiveSink && now - lastClientActiveTime > CLIENT_IDLE_TIMEOUT_MS) {
                        logToFile("No active Binder clients for " + CLIENT_IDLE_TIMEOUT_MS + "ms, exiting", null);
                        System.exit(0);
                    }
                }
            }
        }, "ShowerIdleWatcher");
        idleWatcherThread.setDaemon(true);
        idleWatcherThread.start();
    }


    private static void prepareMainLooper() {
        Looper.prepare();
        synchronized (Looper.class) {
            try {
                Field field = Looper.class.getDeclaredField("sMainLooper");
                field.setAccessible(true);
                field.set(null, Looper.myLooper());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }

    public static void main(String... args) {
        sExitOnStop = true;
        try {
            prepareMainLooper();
            logToFile("prepareMainLooper ok", null);
        } catch (Throwable t) {
            logToFile("prepareMainLooper failed: " + t.getMessage(), t);
        }

        try {
            Workarounds.apply();
            logToFile("Workarounds.apply ok", null);
        } catch (Throwable t) {
            logToFile("Workarounds.apply failed: " + t.getMessage(), t);
        }

        Context context = FakeContext.get();
        sInstance = new Main(context);
        logToFile("server started (Binder only mode)", null);

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            logToFile("Main thread interrupted: " + e.getMessage(), e);
        }
    }

    public Main(Context context) {
        this.appContext = context.getApplicationContext();
        sInstance = this;

        try {
            IShowerService service = new IShowerService.Stub() {
                @Override
                public int ensureDisplay(int width, int height, int dpi, int bitrateKbps) {
                    markClientActive();
                    int bitRate = bitrateKbps > 0 ? bitrateKbps * 1000 : DEFAULT_BIT_RATE;
                    return createVirtualDisplay(width, height, dpi, bitRate);
                }

                @Override
                public void destroyDisplay(int displayId) {
                    markClientActive();
                    releaseDisplay(displayId);
                }

                @Override
                public void launchApp(String packageName, int displayId) {
                    markClientActive();
                    if (packageName != null && !packageName.isEmpty()) {
                        launchPackageOnVirtualDisplay(packageName, displayId);
                    }
                }

                @Override
                public void tap(int displayId, float x, float y) {
                    markClientActive();
                    DisplaySession session = displays.get(displayId);
                    if (session != null && session.inputController != null) {
                        session.inputController.injectTap(x, y);
                        logToFile("Binder TAP injected: " + x + "," + y + " on " + displayId, null);
                    }
                }

                @Override
                public void swipe(int displayId, float x1, float y1, float x2, float y2, long durationMs) {
                    markClientActive();
                    DisplaySession session = displays.get(displayId);
                    if (session != null && session.inputController != null) {
                        session.inputController.injectSwipe(x1, y1, x2, y2, durationMs);
                        logToFile("Binder SWIPE injected on " + displayId, null);
                    }
                }

                @Override
                public void touchDown(int displayId, float x, float y) {
                    markClientActive();
                    DisplaySession session = displays.get(displayId);
                    if (session != null && session.inputController != null) {
                        session.inputController.touchDown(x, y);
                    }
                }

                @Override
                public void touchMove(int displayId, float x, float y) {
                    markClientActive();
                    DisplaySession session = displays.get(displayId);
                    if (session != null && session.inputController != null) {
                        session.inputController.touchMove(x, y);
                    }
                }

                @Override
                public void touchUp(int displayId, float x, float y) {
                    markClientActive();
                    DisplaySession session = displays.get(displayId);
                    if (session != null && session.inputController != null) {
                        session.inputController.touchUp(x, y);
                    }
                }

                @Override
                public void injectKey(int displayId, int keyCode) {
                    markClientActive();
                    DisplaySession session = displays.get(displayId);
                    if (session != null && session.inputController != null) {
                        session.inputController.injectKey(keyCode);
                        logToFile("Binder KEY injected: " + keyCode + " on " + displayId, null);
                    }
                }

                @Override
                public byte[] requestScreenshot(int displayId) {
                    markClientActive();
                    return captureScreenshotBytes(displayId);
                }

                @Override
                public void setVideoSink(int displayId, IBinder sink) {
                    markClientActive();
                    DisplaySession session = displays.get(displayId);
                    if (session != null) {
                        session.setVideoSink(sink);
                    } else {
                        logToFile("setVideoSink for unknown displayId: " + displayId, null);
                    }
                }
            };
            try {
                Class<?> smClass = Class.forName("android.os.ServiceManager");
                java.lang.reflect.Method addService;
                try {
                    // Older Android: addService(String, IBinder, boolean)
                    addService = smClass.getDeclaredMethod("addService", String.class, IBinder.class, boolean.class);
                    addService.setAccessible(true);
                    addService.invoke(null, "ai.assistance.shower", (IBinder) service, Boolean.TRUE);
                    logToFile("Registered Binder service ai.assistance.shower via 3-arg addService", null);
                } catch (NoSuchMethodException e) {
                    // Newer Android: addService(String, IBinder, boolean, int)
                    addService = smClass.getDeclaredMethod("addService", String.class, IBinder.class, boolean.class, int.class);
                    addService.setAccessible(true);
                    // Use 0 as dump priority, same as scrcpy/shizuku style implementations.
                    addService.invoke(null, "ai.assistance.shower", (IBinder) service, Boolean.TRUE, 0);
                    logToFile("Registered Binder service ai.assistance.shower via 4-arg addService", null);
                }
            } catch (SecurityException se) {
                logToFile("ServiceManager.addService denied by SELinux, continuing with broadcast-only registration", se);
            } catch (Throwable t) {
                logToFile("ServiceManager.addService failed (non-fatal): " + t.getMessage(), t);
            }

            sendBinderToApp(service);
        } catch (Throwable t) {
            logToFile("Failed to initialize Shower Binder service: " + t.getMessage(), t);
        }

        startIdleWatcher();
    }

    public static Main start(Context context) {
        Main main = new Main(context);
        logToFile("server started (Binder only mode via Activity)", null);
        return main;
    }


    private void sendBinderToApp(IShowerService service) {
        try {
            Context context = FakeContext.get();
            Intent baseIntent = new Intent(ACTION_SHOWER_BINDER_READY);
            baseIntent.putExtra(EXTRA_BINDER_CONTAINER, new ShowerBinderContainer(service.asBinder()));

            PackageManager pm = context.getPackageManager();
            java.util.List<android.content.pm.ResolveInfo> receivers = null;
            if (pm != null) {
                try {
                    receivers = pm.queryBroadcastReceivers(baseIntent, 0);
                } catch (Throwable t) {
                    logToFile("queryBroadcastReceivers for SHOWER_BINDER_READY failed: " + t.getMessage(), t);
                }
            }

            if (receivers == null || receivers.isEmpty()) {
                // Fallback: best-effort implicit broadcast (may be ignored on some systems).
                context.sendBroadcast(baseIntent);
                logToFile("Sent SHOWER_BINDER_READY broadcast via Context.sendBroadcast (no explicit receivers found)", null);
            } else {
                for (android.content.pm.ResolveInfo ri : receivers) {
                    if (ri.activityInfo == null) {
                        continue;
                    }
                    android.content.ComponentName cn = new android.content.ComponentName(
                            ri.activityInfo.packageName,
                            ri.activityInfo.name
                    );
                    Intent intent = new Intent(baseIntent);
                    intent.setComponent(cn);
                    context.sendBroadcast(intent);
                    logToFile("Sent SHOWER_BINDER_READY broadcast to " + cn.flattenToShortString(), null);
                }
            }
        } catch (Throwable t) {
            logToFile("Failed to send SHOWER_BINDER_READY broadcast: " + t.getMessage(), t);
        }
    }


    private synchronized int createVirtualDisplay(int width, int height, int dpi, int bitRate) {
        logToFile("ensureVirtualDisplay requested: " + width + "x" + height + " dpi=" + dpi + " bitRate=" + bitRate, null);
        
        // Removed check for existing display, we now support multiple.
        
        MediaCodec videoEncoder = null;
        Surface encoderSurface = null;
        VirtualDisplay virtualDisplay = null;
        InputController inputController = null;
        int virtualDisplayId = -1;

        // Use RGBA_8888 so that we can easily convert to Bitmap
        try {
            int actualBitRate = bitRate > 0 ? bitRate : DEFAULT_BIT_RATE;

            // Alignment logic...
            int alignedWidth = width & ~7;
            int alignedHeight = height & ~7;
            if (alignedWidth <= 0 || alignedHeight <= 0) {
                alignedWidth = Math.max(2, width);
                alignedHeight = Math.max(2, height);
            }

            logToFile("createVirtualDisplay using aligned size: " + alignedWidth + "x" + alignedHeight, null);

            MediaFormat format = MediaFormat.createVideoFormat("video/avc", alignedWidth, alignedHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, actualBitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            videoEncoder = MediaCodec.createEncoderByType("video/avc");
            videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoderSurface = videoEncoder.createInputSurface();
            videoEncoder.start();

            int flags = VIRTUAL_DISPLAY_FLAG_PUBLIC
                    | VIRTUAL_DISPLAY_FLAG_PRESENTATION
                    | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                    | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH
                    | VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT
                    | VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL;

            if (Build.VERSION.SDK_INT >= 33) {
                flags |= VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP
                        | VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED
                        | VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED;
            }

            if (Build.VERSION.SDK_INT >= 34) {
                flags |= VIRTUAL_DISPLAY_FLAG_OWN_FOCUS
                        | VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP;
            }

            java.lang.reflect.Constructor<DisplayManager> ctor = DisplayManager.class.getDeclaredConstructor(Context.class);
            ctor.setAccessible(true);
            DisplayManager dm = ctor.newInstance(FakeContext.get());

            virtualDisplay = dm.createVirtualDisplay(
                    "ShowerVirtualDisplay-" + System.currentTimeMillis() % 1000,
                    alignedWidth,
                    alignedHeight,
                    dpi,
                    encoderSurface,
                    flags
            );

            if (virtualDisplay != null && virtualDisplay.getDisplay() != null) {
                virtualDisplayId = virtualDisplay.getDisplay().getDisplayId();
                logToFile("Created virtual display id=" + virtualDisplayId, null);
                try {
                    WindowManager wm = ServiceManager.getWindowManager();
                    wm.setDisplayImePolicy(virtualDisplayId, WindowManager.DISPLAY_IME_POLICY_LOCAL);
                    logToFile("WindowManager.setDisplayImePolicy LOCAL for display=" + virtualDisplayId, null);
                } catch (Throwable t) {
                    logToFile("setDisplayImePolicy failed: " + t.getMessage(), t);
                }
            } else {
                logToFile("Failed to get virtual display id", null);
                virtualDisplayId = -1;
            }

            if (virtualDisplayId != -1) {
                try {
                     inputController = new InputController();
                     inputController.setDisplayId(virtualDisplayId);
                } catch (Throwable t) {
                    logToFile("Failed to init InputController: " + t.getMessage(), t);
                    inputController = null;
                }
                
                DisplaySession session = new DisplaySession(virtualDisplayId, virtualDisplay, videoEncoder, encoderSurface, inputController);
                displays.put(virtualDisplayId, session);
                logToFile("Registered DisplaySession for id=" + virtualDisplayId, null);
                return virtualDisplayId;
            }
            
            // Clean up if failure
            videoEncoder.stop();
            videoEncoder.release();
            encoderSurface.release();
            return -1;

        } catch (Exception e) {
            logToFile("Failed to create virtual display or encoder: " + e.getMessage(), e);
            if (videoEncoder != null) videoEncoder.release();
            if (encoderSurface != null) encoderSurface.release();
            return -1;
        }
    }


    private synchronized void releaseDisplay(int displayId) {
        DisplaySession session = displays.remove(displayId);
        if (session != null) {
             session.release();
        } else {
            logToFile("releaseDisplay ignored for unknown id=" + displayId, null);
        }
    }

    private void launchPackageOnVirtualDisplay(String packageName, int displayId) {
        logToFile("launchPackageOnVirtualDisplay: " + packageName + " on " + displayId, null);
        try {
            if (displayId == -1) {
                logToFile("launchPackageOnVirtualDisplay: invalid displayId", null);
                return;
            }

            PackageManager pm = appContext.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                logToFile("launchPackageOnVirtualDisplay: no launch intent for " + packageName, null);
                return;
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            android.os.Bundle options = null;
            if (Build.VERSION.SDK_INT >= 26) {
                android.app.ActivityOptions launchOptions = android.app.ActivityOptions.makeBasic();
                launchOptions.setLaunchDisplayId(displayId);
                options = launchOptions.toBundle();
            }

            com.ai.assistance.shower.wrappers.ActivityManager am = ServiceManager.getActivityManager();
            am.startActivity(intent, options);

            logToFile("launchPackageOnVirtualDisplay: started " + packageName + " on display " + displayId, null);
        } catch (Exception e) {
            logToFile("launchPackageOnVirtualDisplay failed: " + e.getMessage(), e);
        }
    }

}
