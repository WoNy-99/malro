package com.example.malro;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;          // ← okhttp3.Callback 임포트
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class TranslateRequester {

    public interface ResultCallback {
        void onSuccess(@NonNull String translated);
        void onError(@NonNull String message);
    }

    private static final MediaType JSON =
            MediaType.get("application/json; charset=UTF-8");

    private final OkHttpClient http;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final String endpoint;
    private final String key;
    private final String region;

    // ✅ 불필요하게 들어간 TranslationCallback 파라미터 제거
    public TranslateRequester(@NonNull String endpoint,
                              @NonNull String key,
                              @NonNull String region) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.key = key;
        this.region = region;

        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void translate(@NonNull String text,
                          @NonNull String from,
                          @NonNull String to,
                          @NonNull ResultCallback cb) {
        try {
            String url = endpoint + "/translate?api-version=3.0&from=" + from + "&to=" + to;

            JSONArray bodyArr = new JSONArray();
            JSONObject obj = new JSONObject();
            obj.put("Text", text);
            bodyArr.put(obj);

            Request req = new Request.Builder()
                    .url(url)
                    .addHeader("Ocp-Apim-Subscription-Key", key)
                    .addHeader("Ocp-Apim-Subscription-Region", region)
                    .addHeader("Content-Type", "application/json; charset=UTF-8")
                    .post(RequestBody.create(bodyArr.toString().getBytes(StandardCharsets.UTF_8), JSON))
                    .build();

            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    postError(cb, "네트워크 오류: " + e.getMessage());
                }

                @Override public void onResponse(@NonNull Call call, @NonNull Response resp) throws IOException {
                    String txt;
                    try (ResponseBody rb = resp.body()) {
                        txt = rb != null ? rb.string() : "";
                    }
                    if (!resp.isSuccessful()) {
                        postError(cb, "HTTP " + resp.code() + ": " + txt);
                        return;
                    }
                    try {
                        JSONArray root = new JSONArray(txt);
                        JSONArray translations = root.getJSONObject(0).optJSONArray("translations");
                        String translated = (translations != null && translations.length() > 0)
                                ? translations.getJSONObject(0).optString("text", "")
                                : "";
                        if (translated.isEmpty()) {
                            postError(cb, "번역 결과가 없습니다.");
                        } else {
                            postSuccess(cb, translated);
                        }
                    } catch (Exception ex) {
                        postError(cb, "파싱 오류: " + ex.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            postError(cb, "요청 생성 실패: " + e.getMessage());
        }
    }

    private void postSuccess(@NonNull ResultCallback cb, @NonNull String t) {
        main.post(() -> cb.onSuccess(t));
    }

    private void postError(@NonNull ResultCallback cb, @NonNull String m) {
        main.post(() -> cb.onError(m));
    }
}
