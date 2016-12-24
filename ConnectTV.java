package com.latinperu.tvincaperu2.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;

import com.connectsdk.core.MediaInfo;
import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.device.ConnectableDeviceListener;
import com.connectsdk.device.DevicePicker;
import com.connectsdk.discovery.DiscoveryManager;
import com.connectsdk.discovery.DiscoveryManagerListener;
import com.connectsdk.service.DeviceService;
import com.connectsdk.service.capability.MediaControl;
import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.command.ServiceCommandError;
import com.connectsdk.service.sessions.LaunchSession;
import com.latinperu.tvincaperu2.R;
import com.latinperu.tvincaperu2.httpd.utils.HttpUtils;

import java.net.URLEncoder;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Created by GroverTB on 22/12/2016.
 */

public class ConnectTV {
    private Activity mActivity;
    private AlertDialog dialog;
    private AlertDialog pairingAlertDialog;
    private AlertDialog pairingCodeDialog;
    private DevicePicker dp;
    public MenuItem connectItem;
    private static MediaPlayer mediaPlayer;
    private ConnectableDevice mTV;
    public static DiscoveryManager mDiscoveryManager;
    public static String mDeviceId;
    public static Boolean isConnect = false;
    private LaunchSession mLaunchSession = null;
    private Timer refreshTimer;
    public final int REFRESH_INTERVAL_MS = (int) TimeUnit.SECONDS.toMillis(1);

    public ConnectTV(Activity mactivity) {
        this.mActivity = mactivity;
        setupPicker();
        mDiscoveryManager = DiscoveryManager.getInstance();
    }

    public void setupListener() {
        mDiscoveryManager.addListener(new DiscoveryManagerListener() {
            @Override
            public void onDeviceAdded(DiscoveryManager manager, ConnectableDevice device) {
                if (mDeviceId != null && mTV == null) {
                    if (device.getId().equalsIgnoreCase(mDeviceId)) {
                        mTV = device;
                        device.addListener(deviceListener);
                        device.connect();
                        isConnect = true;
                        mDeviceId = mTV.getId();
                        mediaPlayer = mTV.getCapability(MediaPlayer.class);
                        connectItem.setTitle(mTV.getFriendlyName());
                        connectItem.setIcon(R.drawable.ic_monitor_shared);
                    }
                }
            }

            @Override
            public void onDeviceUpdated(DiscoveryManager manager, ConnectableDevice device) {
                System.out.println("onDeviceUpdated");
            }

            @Override
            public void onDeviceRemoved(DiscoveryManager manager, ConnectableDevice device) {
                System.out.println("onDeviceRemoved");
            }

            @Override
            public void onDiscoveryFailed(DiscoveryManager manager, ServiceCommandError error) {
                System.out.println("onDiscoveryFailed");
            }
        });
    }

    private void setupPicker() {
        dp = new DevicePicker(mActivity);
        dialog = dp.getPickerDialog("Dispositivos", new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                mTV = (ConnectableDevice) arg0.getItemAtPosition(arg2);
                mTV.addListener(deviceListener);
                mTV.setPairingType(null);
                mTV.connect();
                mDeviceId = mTV.getId();
                mediaPlayer = mTV.getCapability(MediaPlayer.class);
                connectItem.setTitle(mTV.getFriendlyName());
                connectItem.setIcon(R.drawable.ic_monitor_shared);
                dp.pickDevice(mTV);
            }
        });

        pairingAlertDialog = new AlertDialog.Builder(mActivity)
                .setTitle("Pairing with TV")
                .setMessage("Please confirm the connection on your TV")
                .setPositiveButton("Okay", null)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dp.cancelPicker();
                        hConnectToggle();
                    }
                })
                .create();

        final EditText input = new EditText(mActivity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        pairingCodeDialog = new AlertDialog.Builder(mActivity)
                .setTitle("Enter Pairing Code on TV")
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        if (mTV != null) {
                            String value = input.getText().toString().trim();
                            mTV.sendPairingKey(value);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dp.cancelPicker();
                        hConnectToggle();
                    }
                })
                .create();
    }


    private ConnectableDeviceListener deviceListener = new ConnectableDeviceListener() {
        @Override
        public void onPairingRequired(ConnectableDevice device, DeviceService service, DeviceService.PairingType pairingType) {
            Utils.Log("Connected to " + mTV.getIpAddress());
            switch (pairingType) {
                case FIRST_SCREEN:
                    Utils.Log("First Screen");
                    pairingAlertDialog.show();
                    break;
                case PIN_CODE:
                case MIXED:
                    Utils.Log("Pin Code");
                    pairingCodeDialog.show();
                    break;
                case NONE:
                default:
                    break;
            }
        }

        @Override
        public void onConnectionFailed(ConnectableDevice device, ServiceCommandError error) {
            Utils.Log("onConnectFailed");
            connectFailed(mTV);
        }

        @Override
        public void onDeviceReady(ConnectableDevice device) {
            Utils.Log("onPairingSuccess");
            if (pairingAlertDialog.isShowing()) {
                pairingAlertDialog.dismiss();
            }
            if (pairingCodeDialog.isShowing()) {
                pairingCodeDialog.dismiss();
            }
            registerSuccess(mTV);
        }

        @Override
        public void onDeviceDisconnected(ConnectableDevice device) {
            Utils.Log("onDeviceDisconnected");
            connectEnded(mTV);
            connectItem.setTitle("Connect onDeviceDisconnected");
        }

        @Override
        public void onCapabilityUpdated(ConnectableDevice device, List<String> added, List<String> removed) {
            Utils.Log("onCapabilityUpdated");
        }
    };

    public void hConnectToggle() {
        System.out.println("hConnectToggle: " + mActivity.isFinishing());
        if (!mActivity.isFinishing()) {
            if (mTV != null) {
                playVideo("GROVER", "TEST", "http://www.dailymotion.com/cdn/live/video/x18w5up.m3u8?source=true&auth=1482710072-2562-7odt1zai-ff2f7ca294f77e9b399f0d7e6457d375");

                new mDialogs(mActivity)
                        .mAlertDialog()
                        .setTitle("Desconectar dispositivo?")
                        .setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                disconnectTV();
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            } else {
                dialog.show();
            }
        }
    }

    private void registerSuccess(ConnectableDevice device) {
        Utils.Log("registerSuccess");
        mDeviceId = device.getId();
        isConnect = true;
//        showImage();
    }

    private void connectFailed(ConnectableDevice device) {
        Utils.Log("connectFailed");
        if (device != null)
            Log.d("2ndScreenAPP", "Failed to connect to " + device.getIpAddress());

        if (mTV != null) {
            mTV.removeListener(deviceListener);
            mTV.disconnect();
            mTV = null;
            mDeviceId = null;
        }
    }

    private void disconnectTV() {
        if (mTV.isConnected())
            mTV.disconnect();
        connectItem.setTitle("Connect hConnectToggle");
        connectItem.setIcon(R.drawable.ic_monitor);
        mTV.removeListener(deviceListener);
        mTV = null;
        mDeviceId = null;
    }

    private void connectEnded(ConnectableDevice device) {
        Utils.Log("connectEnded");
        if (pairingAlertDialog.isShowing()) {
            pairingAlertDialog.dismiss();
        }
        if (pairingCodeDialog.isShowing()) {
            pairingCodeDialog.dismiss();
        }
        mTV.removeListener(deviceListener);
        mTV = null;
        mDeviceId = null;
    }

    private void showImage() {
        String imagePath = "https://s3-us-west-2.amazonaws.com/latinperu/tvincaperu.png";
        String mimeType = "image/jpeg";
        String title = "Sintel Character Design";
        String description = "Blender Open Movie Project";
        String icon = "https://s3-us-west-2.amazonaws.com/latinperu/-1217210516.jpg";

        MediaInfo mediaInfo = new MediaInfo.Builder(imagePath, mimeType)
                .setTitle(title)
                .setDescription(description)
                .setIcon(icon)
                .build();

        mediaPlayer.displayImage(mediaInfo, new MediaPlayer.LaunchListener() {
            @Override
            public void onError(ServiceCommandError error) {
                Log.e("ALLCAST", "Error displaying Image", error);
//                stopMediaSession();
            }

            @Override
            public void onSuccess(MediaPlayer.MediaLaunchObject object) {
                Log.e("ALLCAST", "onSuccess");
//                closeButton.setEnabled(true);
//                testResponse = new TestResponseObject(true, TestResponseObject.SuccessCode, TestResponseObject.Display_image);
//                closeButton.setOnClickListener(closeListener);
//                stopUpdating();
//                isPlayingImage = true;
            }
        });
    }

    public void playVideo(String CANAL_NAME, String CANAL_CATE, String UrlStream) {
//        String UrlList = mActivity.getString(R.string.webapi) + "/list?UrlStream=" + URLEncoder.encode(UrlStream) + "&name=" + CANAL_NAME;
        System.out.println(UrlStream);
        MediaInfo mediaInfo = new MediaInfo.Builder("http://cdnh5.iblups.com/hls/OVJNKV4pSr.m3u8", "application/x-mpegurl")
                .setTitle(CANAL_NAME)
                .setDescription(CANAL_CATE)
                .setIcon("https://s3-us-west-2.amazonaws.com/latinperu/-1217210516.jpg")
                .build();

//        mediaPlayer.playMedia(mediaInfo, false, new MediaPlayer.LaunchListener() {
//            @Override
//            public void onError(ServiceCommandError error) {
//                Log.e("Error", "Error playing video", error);
////                stopMediaSession();
//            }
//
//            public void onSuccess(MediaPlayer.MediaLaunchObject object) {
//                Log.e("onSuccess", "onSuccess");
//            }
//        });
        mediaPlayer.getMediaPlayer().playMedia(mediaInfo, false, new MediaPlayer.LaunchListener() {
            @Override
            public void onSuccess(MediaPlayer.MediaLaunchObject object) {
                Log.e("onSuccess", "onSuccess");
                mLaunchSession = object.launchSession;
                stopUpdating();
            }

            @Override
            public void onError(ServiceCommandError error) {
                Log.e("onError", "Play playlist failure: " + error);
            }
        });
    }

    private void stopUpdating() {
        if (refreshTimer == null)
            return;

        refreshTimer.cancel();
        refreshTimer = null;
    }

    private void startUpdating() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
        }
        refreshTimer = new Timer();
        refreshTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                Log.d("LG", "Updating information");
            }
        }, 0, REFRESH_INTERVAL_MS);
    }
}
