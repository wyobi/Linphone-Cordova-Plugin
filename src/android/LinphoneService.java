package cordova.plugin.linphone;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;
import org.linphone.core.BuildConfig;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.LogCollectionState;
import org.linphone.core.PayloadType;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;




public class LinphoneService extends Service {
    // Keep a static reference to the Service so we can access it from anywhere in the app
    private static LinphoneService sInstance;
    static CallbackContext callbackContext;

    private Handler mHandler;
    private Timer mTimer;
    PluginResult pluginResult;

    private static Call anyCall;
    public static CordovaInterface cordova;

    private Core mCore;
    private CoreListenerStub mCoreListener;

    public static boolean isReady() {
        return sInstance != null;
    }

    public static LinphoneService getInstance() {
        return sInstance;
    }

    public static Core getCore() {
        return sInstance.mCore;
    }
    public static Call getCall() {
        return anyCall;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (isAnotherInstanceRunning()) {
            Log.w("LinphoneService", "Another instance detected, stopping it.");
            stopSelf();
            return;
        }

        // Ensure Cordova is initialized before using it
        if (cordova == null) {
            stopSelf();
            return;
        }

        // The first call to liblinphone SDK MUST BE to a Factory method
        // So let's enable the library debug logs & log collection
        String basePath = getFilesDir().getAbsolutePath();
        Factory.instance().setLogCollectionPath(basePath);
        Factory.instance().enableLogCollection(LogCollectionState.Enabled);

        Factory.instance().setDebugMode(true, getString(cordova.getActivity().getResources().getIdentifier("app_name", "string", cordova.getActivity().getPackageName())));

        mHandler = new Handler();
        // This will be our main Core listener, it will change activities depending on events
        mCoreListener = new CoreListenerStub() {
            @Override
            public void onCallStateChanged(Core core, Call call, Call.State state, String message) {
                if (state == Call.State.IncomingReceived) {
                    // For this sample we will automatically answer incoming calls
                    anyCall = call;
//                    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
//                    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
//                    r.play();
//                    CallParams params = getCore().createCallParams(call);
//                    params.enableVideo(false);
//                    call.acceptWithParams(params);
                }
                else if(state == Call.State.End) {
                    anyCall = null;
                }
                else {
                    anyCall = call;
                }
            }
        };

        try {
            // Let's copy some RAW resources to the device
            // The default config file must only be installed once (the first time)
            copyIfNotExist(cordova.getActivity().getResources().getIdentifier("linphonerc_default", "raw", cordova.getActivity().getPackageName()), basePath + "/.linphonerc");
            // The factory config is used to override any other setting, let's copy it each time
            copyFromPackage(cordova.getActivity().getResources().getIdentifier("linphonerc_factory", "raw", cordova.getActivity().getPackageName()), "linphonerc");
        } catch (IOException ioe) {
            Log.e(ioe);
        }

        // Create the Core and add our listener
        mCore = Factory.instance()
                .createCore(basePath + "/.linphonerc", basePath + "/linphonerc", this);
        mCore.addListener(mCoreListener);
        // Core is ready to be configured
        configureCore();
    }

    private boolean isAnotherInstanceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (LinphoneService.class.getName().equals(service.service.getClassName()) && service.pid != android.os.Process.myPid()) {
                    return true; // Another instance is running
                }
            }
        }
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        // If our Service is already running, no need to continue
        if (sInstance != null) {
            return START_STICKY;
        }

        // Our Service has been started, we can keep our reference on it
        // From now one the Launcher will be able to call onServiceReady()
        sInstance = this;

        // Core must be started after being created and configured
        mCore.start();
        // We also MUST call the iterate() method of the Core on a regular basis
        TimerTask lTask =
                new TimerTask() {
                    @Override
                    public void run() {
                        mHandler.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mCore != null) {
                                            mCore.iterate();
                                        }
                                    }
                                });
                    }
                };
        mTimer = new Timer("Linphone scheduler");
        mTimer.schedule(lTask, 0, 20);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if(LinphoneService.isReady()) {
            mCore.removeListener(mCoreListener);
            mTimer.cancel();
            mCore.stop();
            mCore = null;
            sInstance = null;
        }

        super.onDestroy();
    }


    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // For this sample we will kill the Service at the same time we kill the app
        stopSelf();

        super.onTaskRemoved(rootIntent);
    }

    public void stop() {
        if(LinphoneService.isReady()) {
            mCore.removeListener(mCoreListener);
            mTimer.cancel();
            mCore.stop();
            mCore = null;
            sInstance = null;
        }

        stopSelf();
    }

    private void configureCore() {
        // We will create a directory for user signed certificates if needed
        String basePath = getFilesDir().getAbsolutePath();
        String userCerts = basePath + "/user-certs";
        File f = new File(userCerts);
        if (!f.exists()) {
            if (!f.mkdir()) {
                Log.e(userCerts + " can't be created.");
            }
        }
        mCore.setUserCertificatesPath(userCerts);
        String deviceName = Build.DEVICE;
        String appName = "Linphone";
        String androidVersion = BuildConfig.VERSION_NAME;
        String userAgent = appName + "/" + androidVersion + " (" + deviceName + ") LinphoneSDK";

        mCore.setUserAgent(
                userAgent,
                getString(cordova.getActivity().getResources().getIdentifier("linphone_sdk_version", "string", cordova.getActivity().getPackageName()))
                        + " ("
                        + getString(cordova.getActivity().getResources().getIdentifier("linphone_sdk_branch", "string", cordova.getActivity().getPackageName()))
                        + ")");

        for (final PayloadType pt : mCore.getAudioPayloadTypes()) {
            if ("G729".equalsIgnoreCase(pt.getMimeType())) {
                pt.enable(true);
            }
        }
    }

    private void copyIfNotExist(int ressourceId, String target) throws IOException {
        File lFileToCopy = new File(target);
        if (!lFileToCopy.exists()) {
            copyFromPackage(ressourceId, lFileToCopy.getName());
        }
    }

    private void copyFromPackage(int ressourceId, String target) throws IOException {
        FileOutputStream lOutputStream;
        lOutputStream = openFileOutput(target, 0);
        InputStream lInputStream = getResources().openRawResource(ressourceId);
        int readByte;
        byte[] buff = new byte[8048];
        while ((readByte = lInputStream.read(buff)) != -1) {
            lOutputStream.write(buff, 0, readByte);
        }
        lOutputStream.flush();
        lOutputStream.close();
        lInputStream.close();
    }
}
