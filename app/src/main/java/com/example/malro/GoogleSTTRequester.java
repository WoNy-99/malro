package com.example.malro;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GoogleSTTRequester {

    public interface STTCallback {
        void onSuccess(@NonNull String transcript);
        void onAlternatives(@NonNull String[] alts);
        void onError(@NonNull String message);
    }

    private static final String TAG = "GoogleCloudSTTClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final String apiKey;
    private final Handler main = new Handler(Looper.getMainLooper());

    public GoogleSTTRequester(@NonNull String apiKey) {
        this.apiKey = apiKey;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void recognizeWavFile(@NonNull File wavFile,
                                 @NonNull String language,
                                 @NonNull STTCallback cb) {
        if (!wavFile.exists() || wavFile.length() < 44) {
            postError(cb, "오디오 파일이 없거나 너무 짧습니다.");
            return;
        }

        try {
            byte[] pcm = readWavPcm(wavFile);                   // WAV 헤더(44B) 스킵
            String b64 = Base64.encodeToString(pcm, Base64.NO_WRAP);

            JSONObject config = new JSONObject()
                    .put("encoding", "LINEAR16")
                    .put("sampleRateHertz", 16000)
                    .put("languageCode", language)
                    .put("enableAutomaticPunctuation", true)
                    .put("maxAlternatives", 3);

            JSONObject audio = new JSONObject().put("content", b64);
            JSONObject body = new JSONObject()
                    .put("config", config)
                    .put("audio", audio);

            String url = "https://speech.googleapis.com/v1/speech:recognize?key=" + apiKey;

            RequestBody reqBody = RequestBody.create(body.toString(), JSON); // ★ 여기!
            Request req = new Request.Builder()
                    .url(url)
                    .post(reqBody)
                    .build();

            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    postError(cb, "네트워크 오류: " + e.getMessage());
                }

                @Override public void onResponse(@NonNull Call call, @NonNull Response resp) throws IOException {
                    String txt = resp.body() != null ? resp.body().string() : "";
                    if (!resp.isSuccessful()) {
                        postError(cb, "HTTP " + resp.code() + ": " + txt);
                        return;
                    }
                    try {
                        JSONObject obj = new JSONObject(txt);
                        JSONArray results = obj.optJSONArray("results");
                        if (results == null || results.length() == 0) {
                            postError(cb, "인식 결과 없음");
                            return;
                        }
                        JSONObject first = results.getJSONObject(0);
                        JSONArray alts = first.getJSONArray("alternatives");
                        String top = alts.getJSONObject(0).optString("transcript", "");
                        String[] altArr = new String[alts.length()];
                        for (int i = 0; i < alts.length(); i++) {
                            altArr[i] = alts.getJSONObject(i).optString("transcript", "");
                        }
                        postSuccess(cb, top, altArr);
                    } catch (Exception ex) {
                        Log.e(TAG, "parse error", ex);
                        postError(cb, "응답 파싱 오류");
                    }
                }
            });

        } catch (Exception e) {
            postError(cb, "처리 실패: " + e.getMessage());
        }
    }

    private byte[] readWavPcm(File wav) throws IOException {
        try (FileInputStream fis = new FileInputStream(wav)) {
            byte[] header = new byte[12];
            int r = fis.read(header);
            boolean isWav = (r == 12 &&
                    header[0]=='R' && header[1]=='I' && header[2]=='F' && header[3]=='F' &&
                    header[8]=='W' && header[9]=='A' && header[10]=='V' && header[11]=='E');

            long skip = isWav ? 44 : 0;
            long fileLen = wav.length();
            int pcmLen = (int)Math.max(0, fileLen - skip);

            byte[] pcm = new byte[pcmLen];
            if (skip > 12) fis.skip(skip - 12);
            int off = 0;
            while (off < pcmLen) {
                int n = fis.read(pcm, off, pcmLen - off);
                if (n <= 0) break;
                off += n;
            }
            return pcm;
        }
    }

    private void postSuccess(STTCallback cb, String top, String[] alts) {
        main.post(() -> { cb.onSuccess(top); cb.onAlternatives(alts); });
    }
    private void postError(STTCallback cb, String msg) {
        main.post(() -> cb.onError(msg));
    }
}
