// com/example/malro/TheFluentClient.java
package com.example.malro;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

public class TheFluentClient {

    public interface EvalCallback {
        void onSuccess(float overall, float[] scores, @NonNull String rawJson);
        void onError(@NonNull String message);
    }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final String baseUrl;  // 예: https://thefluent.me/api
    private final String apiKey;   // 필요시: RapidAPI 키 등 (없으면 "")

    // 인증 헤더 이름이 다를 수 있어 일반화 (필요 없으면 null)
    private final String authHeaderName;
    private final String authHeaderValuePrefix; // 예: "Bearer " 또는 "" 등

    public TheFluentClient(@NonNull String baseUrl,
                           @NonNull String apiKey,
                           String authHeaderName,
                           String authHeaderValuePrefix) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl;
        this.apiKey = apiKey;
        this.authHeaderName = authHeaderName;
        this.authHeaderValuePrefix = authHeaderValuePrefix == null ? "" : authHeaderValuePrefix;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /** 업로더로 파일을 올리고 → /post → /score 순서대로 실행 */
    public void evaluatePronunciation(@NonNull File wavFile,
                                      @NonNull String referenceText,
                                      @NonNull String postLanguageId, // thefluent 언어 ID (문서값 사용)
                                      int scale,                        // 예: 100
                                      @NonNull AudioUrlProvider uploader,
                                      @NonNull EvalCallback cb) {
        uploader.upload(wavFile, new AudioUrlProvider.Callback() {
            @Override public void onSuccess(@NonNull String audioUrl) {
                createPost(postLanguageId, "Pronunciation", referenceText, new PostCallback() {
                    @Override public void onSuccess(int postId) {
                        scorePronunciation(postId, audioUrl, scale, cb);
                    }
                    @Override public void onError(@NonNull String message) {
                        cb.onError("createPost failed: " + message);
                    }
                });
            }
            @Override public void onError(@NonNull Exception e) {
                cb.onError("Upload failed: " + e.getMessage());
            }
        });
    }

    // --- 1) /post ---
    interface PostCallback { void onSuccess(int postId); void onError(@NonNull String message); }

    public void createPost(@NonNull String languageId,
                           @NonNull String title,
                           @NonNull String content,
                           @NonNull PostCallback cb) {
        try {
            JSONObject body = new JSONObject()
                    .put("post_language_id", languageId)
                    .put("post_title", title)
                    .put("post_content", content);

            Request.Builder rb = new Request.Builder()
                    .url(baseUrl + "/post")
                    .post(RequestBody.create(body.toString(), JSON))
                    .addHeader("Content-Type", "application/json; charset=UTF-8");

            if (authHeaderName != null && !authHeaderName.isEmpty() && !apiKey.isEmpty()) {
                rb.addHeader(authHeaderName, authHeaderValuePrefix + apiKey);
            }

            http.newCall(rb.build()).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    main.post(() -> cb.onError("Network: " + e.getMessage()));
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String txt = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        main.post(() -> cb.onError("HTTP " + response.code() + ": " + txt));
                        return;
                    }
                    try {
                        // 응답에서 post_id 추출 (문서 예시 기준 키 이름에 맞춰 조정)
                        JSONObject obj = new JSONObject(txt);
                        int postId = obj.optInt("post_id", -1);
                        if (postId <= 0) {
                            // 혹시 배열/다른 키로 올 수도 있으니 보조 파싱 시도
                            if (obj.has("data")) {
                                JSONObject data = obj.getJSONObject("data");
                                postId = data.optInt("post_id", -1);
                            }
                        }
                        final int id = postId;
                        if (id <= 0) throw new IOException("post_id not found");
                        main.post(() -> cb.onSuccess(id));
                    } catch (Exception ex) {
                        main.post(() -> cb.onError("Parse: " + ex.getMessage()));
                    }
                }
            });
        } catch (Exception e) {
            cb.onError("Build request failed: " + e.getMessage());
        }
    }

    // --- 2) /score/{post_id}?scale=... ---
    public void scorePronunciation(int postId,
                                   @NonNull String audioUrl,
                                   int scale,
                                   @NonNull EvalCallback cb) {
        try {
            JSONObject body = new JSONObject()
                    .put("audio_provided", audioUrl);

            HttpUrl url = HttpUrl.parse(baseUrl + "/score/" + postId)
                    .newBuilder()
                    .addQueryParameter("scale", String.valueOf(scale))
                    .build();

            Request.Builder rb = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toString(), JSON))
                    .addHeader("Content-Type", "application/json; charset=UTF-8");

            if (authHeaderName != null && !authHeaderName.isEmpty() && !apiKey.isEmpty()) {
                rb.addHeader(authHeaderName, authHeaderValuePrefix + apiKey);
            }

            http.newCall(rb.build()).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    main.post(() -> cb.onError("Network: " + e.getMessage()));
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String txt = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        main.post(() -> cb.onError("HTTP " + response.code() + ": " + txt));
                        return;
                    }
                    try {
                        // ★ 응답 포맷에 맞춰 파싱(예시): { "overall": 83.5, "scores": [ ... ] }
                        JSONObject o = new JSONObject(txt);
                        float overall = (float) o.optDouble("overall", -1.0);
                        float[] scores = null;
                        if (o.has("scores")) {
                            JSONArray arr = o.getJSONArray("scores");
                            scores = new float[arr.length()];
                            for (int i = 0; i < arr.length(); i++) {
                                scores[i] = (float) arr.optDouble(i, 0.0);
                            }
                        }
                        float[] finalScores = scores;
                        main.post(() -> cb.onSuccess(overall, finalScores, txt));
                    } catch (Exception ex) {
                        main.post(() -> cb.onError("Parse: " + ex.getMessage()));
                    }
                }
            });
        } catch (Exception e) {
            cb.onError("Build request failed: " + e.getMessage());
        }
    }
}
