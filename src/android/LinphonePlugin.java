package cordova.plugin.linphone;

import android.content.Intent;
import android.os.Handler;

import com.rscja.utility.StringUtility;

import org.apache.cordova.CordovaPlugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.core.AccountCreator;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Call;
import org.linphone.core.CallParams;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.TransportType;



/**
 * This class echoes a string called from JavaScript.
 */
public class LinphonePlugin extends CordovaPlugin {
    private Handler mHandler;
    PluginResult pluginResult;

    private CallbackContext callbackContext;
    private CallbackContext dtmfCallbackContext;
    private CoreListenerStub mCoreListener;
    private AccountCreator mAccountCreator;


    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        LinphoneService.cordova = cordova;
        if (action.equals("initLinphoneCore")) {
            mCoreListener = new CoreListenerStub() {
                @Override
                public void onRegistrationStateChanged(Core core, ProxyConfig cfg, RegistrationState state, String message) {
                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("message", message);
                        obj.put("code", state.toInt());
                        obj.put("state", state.name());

                        pluginResult = new PluginResult(PluginResult.Status.OK, obj);
                        pluginResult.setKeepCallback(true);
                        callbackContext.sendPluginResult(pluginResult);
                    }
                    catch (Exception e) {
                        pluginResult = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                        pluginResult.setKeepCallback(false);
                        callbackContext.sendPluginResult(pluginResult);
                    }
                }

                @Override
                public void onCallStateChanged(Core core, Call call, Call.State state, String message) {
                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("message", message);
                        obj.put("code", state.toInt());
                        obj.put("state", state.name());

                        pluginResult = new PluginResult(PluginResult.Status.OK, obj);
                        pluginResult.setKeepCallback(true);
                        callbackContext.sendPluginResult(pluginResult);
                    }
                    catch (Exception e) {
                        pluginResult = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                        pluginResult.setKeepCallback(false);
                        callbackContext.sendPluginResult(pluginResult);
                    }
                }

                @Override
                public void onDtmfReceived(Core lc, Call call, int dtmf) {
                    pluginResult = new PluginResult(PluginResult.Status.OK, dtmf);
                    pluginResult.setKeepCallback(true);
                    if(dtmfCallbackContext != null) {
                        dtmfCallbackContext.sendPluginResult(pluginResult);
                    }
                }
            };
            this.configureAccount();

            return true;
        } else if (action.equals("registerSIP")) {
            this.registerSIP(args, callbackContext);
            return true;
        } else if (action.equals("unregisterSIP")) {
            this.unregisterSIP(args, callbackContext);
            return true;
        } else if (action.equals("acceptCall")) {
            this.acceptCall();
            return true;
        } else if (action.equals("makeCall")) {
            try {
                String username = args.get(0).toString();
                String domain = args.get(1).toString();
                String displayName = args.get(2).toString();

                makeCall(username, domain, displayName);

                pluginResult = new PluginResult(PluginResult.Status.OK, "Dialing");
                pluginResult.setKeepCallback(false);
                callbackContext.sendPluginResult(pluginResult);

            } catch (JSONException e) {
                pluginResult = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                pluginResult.setKeepCallback(false);
                callbackContext.sendPluginResult(pluginResult);
            }
        }
        else if (action.equals("listenForDTMF")) {
            dtmfCallbackContext = callbackContext;
            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            dtmfCallbackContext.sendPluginResult(result);
            return true;
        }
        else if (action.equals("stop")) {
            try {
                stop();

                PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                result.setKeepCallback(false);
                dtmfCallbackContext.sendPluginResult(result);
                callbackContext.sendPluginResult(result);
            }
            catch (Exception e) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                result.setKeepCallback(false);
                callbackContext.sendPluginResult(result);
            }

            return true;
        }

        return false;
    }

    private void acceptCall() {
        Core core = LinphoneService.getCore();

        CallParams params = core.createCallParams(LinphoneService.getCall());
        params.enableVideo(true);
        LinphoneService.getCall().acceptWithParams(params);
    }

    public static void makeCall(String username, String domain, String displayName) {
        String url = username + "@" + domain;
        Core core = LinphoneService.getCore();
        Address addressToCall = core.interpretUrl(url);
        CallParams params = core.createCallParams(null);
        if (addressToCall != null) {
            core.inviteAddressWithParams(addressToCall, params);
        }
    }

    public void unregisterSIP(JSONArray args, CallbackContext callbackContext){
        if(!LinphoneService.isReady()) {
            if(callbackContext != null) {
                pluginResult = new PluginResult(PluginResult.Status.ERROR, "Linphone Service not started");
                pluginResult.setKeepCallback(false);
                callbackContext.sendPluginResult(pluginResult);
            }
            return;
        }

        try {
            String server = null;
            if (args.length() > 0) {
                server = args.get(0).toString();
            }

            try {
                ProxyConfig[] proxyConfigs = LinphoneService.getCore().getProxyConfigList();
                for (ProxyConfig proxyConfig : proxyConfigs) {
                    if(server == null || server.equals("") || server.equals(proxyConfig.getDomain())) {
                        AuthInfo mAuthInfo = proxyConfig.findAuthInfo();
                        LinphoneService.getCore().removeProxyConfig(proxyConfig);
                        if (mAuthInfo != null) {
                            LinphoneService.getCore().removeAuthInfo(mAuthInfo);
                        }
                    }
                }

                if(callbackContext != null) {
                    pluginResult = new PluginResult(PluginResult.Status.OK);
                    pluginResult.setKeepCallback(false);
                    callbackContext.sendPluginResult(pluginResult);
                }
            } catch (Exception e) {
                if(callbackContext != null) {
                    pluginResult = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                    pluginResult.setKeepCallback(false);
                    callbackContext.sendPluginResult(pluginResult);
                }
                else {
                    e.printStackTrace();
                }
            }
        }
        catch (JSONException e) {
            if(callbackContext != null) {
                pluginResult = new PluginResult(PluginResult.Status.ERROR, "Deregistration Failed");
                pluginResult.setKeepCallback(false);
                callbackContext.sendPluginResult(pluginResult);
            }
            else {
                e.printStackTrace();
            }
        }
    }


    public void registerSIP(JSONArray args, CallbackContext callbackContext){
        //String transport = args.get(4).toString();
        String transport = null;
        // At least the 3 below values are required
        //mAccountCreator.setUsername(args.get(0).toString());
        try {
            mAccountCreator.setUsername(args.get(0).toString());
            mAccountCreator.setDisplayName(args.get(1).toString());
            mAccountCreator.setDomain(args.get(2).toString());
            mAccountCreator.setPassword(args.get(3).toString());

            transport = args.get(4).toString();
            // By default it will be UDP if not set, but TLS is strongly recommended
            switch (transport.toUpperCase()) {
                case "UDP":
                    mAccountCreator.setTransport(TransportType.Udp);
                    break;
                case "TCP":
                    mAccountCreator.setTransport(TransportType.Tcp);
                    break;
                case "TLS":
                    mAccountCreator.setTransport(TransportType.Tls);
                    break;
            }

            // This will automatically create the proxy config and auth info and add them to the Core
            ProxyConfig cfg = mAccountCreator.createProxyConfig();
            // Make sure the newly created one is the default
            LinphoneService.getCore().setDefaultProxyConfig(cfg);
            //LinphoneService.getCore().setUserAgent();

            try {
                mAccountCreator = LinphoneService.getCore().createAccountCreator(null);
                LinphoneService.getCore().addListener(mCoreListener);

                JSONObject obj = new JSONObject();
                obj.put("message", "Registration Start");
                obj.put("code", 15);
                obj.put("state", "Start");

                pluginResult = new PluginResult(PluginResult.Status.OK, obj);
                pluginResult.setKeepCallback(false);
                callbackContext.sendPluginResult(pluginResult);
            }
            catch (Exception e) {
                pluginResult = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                pluginResult.setKeepCallback(false);
                callbackContext.sendPluginResult(pluginResult);
            }
        } catch (JSONException e) {
            e.printStackTrace();

            pluginResult = new PluginResult(PluginResult.Status.ERROR, "Registration Failed");
            pluginResult.setKeepCallback(false);
            callbackContext.sendPluginResult(pluginResult);
        }
    }

    public void configureAccount(){
        mHandler = new Handler();
        LinphoneService.callbackContext=callbackContext;
        if (LinphoneService.isReady()) {
            try {
                mAccountCreator = LinphoneService.getCore().createAccountCreator(null);
                LinphoneService.getCore().addListener(mCoreListener);

                JSONObject obj = new JSONObject();
                obj.put("message", "Service Running");
                obj.put("code", 10);
                obj.put("state", "Ready");

                pluginResult = new PluginResult(PluginResult.Status.OK, obj);
                pluginResult.setKeepCallback(true);
                callbackContext.sendPluginResult(pluginResult);
            }
            catch (Exception e) {
                pluginResult = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                pluginResult.setKeepCallback(false);
                callbackContext.sendPluginResult(pluginResult);
            }
        } else {
            try {
                Intent intent = new Intent(cordova.getContext(), LinphoneService.class);
                cordova.getContext().stopService(intent); // Stop old instance if running
            }
            catch (Exception e) {

            }

            // If it's not, let's start it
            cordova.getContext().startService(
                    new Intent().setClass(cordova.getContext(), LinphoneService.class));
            // And wait for it to be ready, so we can safely use it afterwards
            new ServiceWaitThread().start();
        }

    }

    public void stop() {
        this.unregisterSIP(new JSONArray(), null);
        LinphoneService.getInstance().stop();
    }

    // This thread will periodically check if the Service is ready, and then call onServiceReady
    private class ServiceWaitThread extends Thread {
        public void run() {
            while (!LinphoneService.isReady()) {
                try {
                    sleep(30);
                } catch (InterruptedException e) {
                    throw new RuntimeException("waiting thread sleep() has been interrupted");
                }
            }
            // As we're in a thread, we can't do UI stuff in it, must post a runnable in UI thread
            mHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mAccountCreator = LinphoneService.getCore().createAccountCreator(null);
                                LinphoneService.getCore().addListener(mCoreListener);

                                JSONObject obj = new JSONObject();
                                obj.put("message", "Service Ready");
                                obj.put("code", 10);
                                obj.put("state", "Ready");

                                pluginResult = new PluginResult(PluginResult.Status.OK, obj);
                                pluginResult.setKeepCallback(true);
                                callbackContext.sendPluginResult(pluginResult);
                            }
                            catch (Exception e) {
                                pluginResult = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                                pluginResult.setKeepCallback(false);
                                callbackContext.sendPluginResult(pluginResult);
                            }
                        }
                    });
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        if(LinphoneService.isReady()) {
            LinphoneService.getCore().addListener(mCoreListener);
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        if(LinphoneService.isReady()) {
            LinphoneService.getCore().removeListener(mCoreListener);
        }
        super.onPause(multitasking);
    }
}
