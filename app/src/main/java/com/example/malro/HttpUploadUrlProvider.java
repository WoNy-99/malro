// com/example/malro/HttpUploadUrlProvider.java
package com.example.malro;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

public class HttpUploadUrlProvider implements AudioUrlProvider {

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final String uploadEndpoint; // 예: https://yourserver.com/upload
    private final String apiKey;         // 필요 없으면 "" 전달

    public HttpUploadUrlProvider(@NonNull String uploadEndpoint, @NonNull String apiKey) {
        this.uploadEndpoint = uploadEndpoint;
        this.apiKey = apiKey;
    }

    @Override
    public void upload(@NonNull File file, @NonNull Callback cb) {
        RequestBody fileBody =
                RequestBody.create(file, MediaType.parse("audio/wav"));

        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody) // 서버 필드명에 맞춰 변경
                .build();

        Request.Builder rb = new Request.Builder()
                .url(uploadEndpoint)
                .post(body);

        if (!apiKey.isEmpty()) {
            rb.addHeader("Authorization", "Bearer " + apiKey); // 필요에 맞게
        }

        http.newCall(rb.build()).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                main.post(() -> cb.onError(e));
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String txt = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    main.post(() -> cb.onError(new IOException("HTTP " + response.code() + ": " + txt)));
                    return;
                }
                try {
                    // 서버 응답 예: { "url": "https://public.cdn/xxx.wav" }
                    String url = new JSONObject(txt).optString("url", "");
                    if (url.isEmpty()) throw new IOException("No url in response");
                    String finalUrl = url;
                    main.post(() -> cb.onSuccess(finalUrl));
                } catch (Exception ex) {
                    main.post(() -> cb.onError(new IOException("Parse error: " + ex.getMessage())));
                }
            }
        });
    }
}
