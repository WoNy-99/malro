package com.example.malro;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Google Cloud Text-to-Speech (REST)
 * - 입력 텍스트를 합성하여 mp3를 캐시에 저장하고 즉시 재생
 * - OkHttp 4.12.0 기준
 */
public class GoogleTTSRequester {

    public interface TTSCallback {
        default void onStart(@NonNull File audioFile) {}
        default void onDone(@NonNull File audioFile) {}
        default void onError(@NonNull String message) {}
    }

    private static final String TAG = "GoogleTTSRequester";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Context appCtx;
    private final String apiKey;
    private final OkHttpClient http;
    private final Handler main = new Handler(Looper.getMainLooper());

    private MediaPlayer player;

    public GoogleTTSRequester(@NonNull Context context, @NonNull String apiKey) {
        this.appCtx = context.getApplicationContext();
        this.apiKey = apiKey;

        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    // 기존 호출과 호환 (slow=false)
    public void speak(@NonNull String text, @NonNull String languageCode) {
        speak(text, languageCode, false, null);
    }

    // TodayPronunciationActivity에서 쓰던 형태와 호환
    public void speak(@NonNull String text, @NonNull String languageCode, boolean slow) {
        speak(text, languageCode, slow, null);
    }

    // 콜백까지 필요할 때
    public void speak(@NonNull String text, @NonNull String languageCode, boolean slow,
                      @Nullable TTSCallback cb) {
        if (text.trim().isEmpty()) {
            if (cb != null) cb.onError("읽을 텍스트가 비어있습니다.");
            return;
        }

        try {
            // Google Cloud TTS 요청 바디
            JSONObject input = new JSONObject().put("text", text);
            JSONObject voice = new JSONObject()
                    .put("languageCode", languageCode)
                    .put("ssmlGender", "NEUTRAL"); // voice name 없이도 대부분 언어 동작
            JSONObject audio = new JSONObject()
                    .put("audioEncoding", "MP3")
                    .put("speakingRate", slow ? 0.85 : 1.0)  // 느리게 옵션
                    .put("pitch", 0.0);

            JSONObject body = new JSONObject()
                    .put("input", input)
                    .put("voice", voice)
                    .put("audioConfig", audio);

            String url = "https://texttospeech.googleapis.com/v1/text:synthesize?key=" + apiKey;

            Request req = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toString(), JSON))
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
                        String audioBase64 = obj.optString("audioContent", null);
                        if (audioBase64 == null) {
                            postError(cb, "오디오가 비어있습니다.");
                            return;
                        }
                        byte[] mp3 = Base64.decode(audioBase64, Base64.DEFAULT);
                        File out = new File(appCtx.getCacheDir(),
                                "tts_" + System.currentTimeMillis() + ".mp3");
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            fos.write(mp3);
                            fos.flush();
                        }
                        playFile(out, cb);
                    } catch (Exception ex) {
                        Log.e(TAG, "parse error", ex);
                        postError(cb, "응답 파싱 오류");
                    }
                }
            });
        } catch (Exception e) {
            postError(cb, "요청 생성 실패: " + e.getMessage());
        }
    }

    public void stop() {
        main.post(() -> {
            if (player != null) {
                try { player.stop(); } catch (Exception ignored) {}
                try { player.release(); } catch (Exception ignored) {}
                player = null;
            }
        });
    }

    public void shutdown() {
        stop();
        // OkHttp/Handler는 그대로 두어도 무방
    }

    private void playFile(@NonNull File file, @Nullable TTSCallback cb) {
        main.post(() -> {
            stop(); // 기존 재생 중지

            player = new MediaPlayer();
            try {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build());
                player.setDataSource(file.getAbsolutePath());
                player.setOnPreparedListener(mp -> {
                    if (cb != null) cb.onStart(file);
                    mp.start();
                });
                player.setOnCompletionListener(mp -> {
                    if (cb != null) cb.onDone(file);
                    try { mp.release(); } catch (Exception ignored) {}
                    player = null;
                    // 캐시 파일 정리(선택)
                    // noinspection ResultOfMethodCallIgnored
                    file.delete();
                });
                player.prepareAsync();
            } catch (Exception e) {
                Log.e(TAG, "playFile error", e);
                if (cb != null) cb.onError("재생 실패: " + e.getMessage());
            }
        });
    }

    private void postError(@Nullable TTSCallback cb, @NonNull String msg) {
        main.post(() -> {
            if (cb != null) cb.onError(msg);
            Log.e(TAG, msg);
        });
    }
}
