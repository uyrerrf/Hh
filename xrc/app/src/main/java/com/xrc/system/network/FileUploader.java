package com.xrc.system.network;

import android.content.Context;
import android.util.Log;

import com.xrc.system.core.Constants;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class FileUploader {
    private static final String TAG = Constants.TAG + ":Upload";
    private static final int TIMEOUT = 30;
    private OkHttpClient client;

    public FileUploader(Context context) {
        client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                .build();
    }

    public void uploadFile(Context context, String filePath, String uploadUrl) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                Log.e(TAG, "File not found: " + filePath);
                return;
            }

            RequestBody fileBody = RequestBody.create(
                    file, MediaType.parse("application/octet-stream"));

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.getName(), fileBody)
                    .build();

            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .build();

            client.newCall(request).execute();
        } catch (IOException e) {
            Log.e(TAG, "Upload failed", e);
        }
    }
}
