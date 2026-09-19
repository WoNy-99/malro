// StudyPronunciationActivity.java
package com.example.malro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.malro.utils.LogoutManager;
import com.example.malro.utils.PronunciationExtras;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;

public class StudyPronunciationActivity extends AppCompatActivity {

    // Top bar / switches / mic
    private ImageButton backButton;
    private SwitchMaterial aiVoiceSwitch;
    private SwitchMaterial recordedPlaySwitch;
    private FloatingActionButton micButton;

    // Center texts
    private TextView sentenceText;
    private TextView sentenceText2;

    // Bottom side buttons (XML: btn_next, btnFeed)
    private MaterialButton btnNext;
    private MaterialButton btnFeed;

    // Bottom navigation (XML: nav_study, nav_archive, nav_translate)
    private MaterialButton navStudy, navArchive, navTranslate;

    // Audio / STT / TTS / Upload / Scoring
    private AudioRecorderHelper recorder;
    private boolean isRecording = false;
    private MediaPlayer player;

    private GoogleSTTRequester stt;
    private GoogleTTSRequester tts;

    private String languageCode = "en-US";
    private AudioUrlProvider uploader;
    private TheFluentClient fluent;

    private final ActivityResultLauncher<String> reqMic =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> { if (granted) startRecording(); else toast("마이크 권한이 필요합니다."); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_pronunciation);

        // (전역에서 1회만 초기화한다면 MyApplication 등으로 이동 가능)
        FirebaseApp.initializeApp(this);

        // ===== XML id 바인딩 (activity_study_pronunciation.xml과 1:1 매칭) =====
        backButton         = findViewById(R.id.btnBack);
        aiVoiceSwitch      = findViewById(R.id.ai_voice_switch);
        recordedPlaySwitch = findViewById(R.id.ai_voice_switch_2);
        micButton          = findViewById(R.id.mic_button);

        sentenceText       = findViewById(R.id.sentence_text);
        sentenceText2      = findViewById(R.id.sentence_text_2);

        btnNext            = findViewById(R.id.btn_next);
        btnFeed            = findViewById(R.id.btnFeed);

        navStudy           = findViewById(R.id.nav_study);
        navArchive         = findViewById(R.id.nav_archive);
        navTranslate       = findViewById(R.id.nav_translate);
        // ===============================================================
// 클릭 리스너 보강 (null-safe)
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                // TODO: 다음 문장 로드 로직 연결 (현재는 안내 토스트)
                toast("다음 문장을 준비 중입니다.");
            });
        }
        if (btnFeed != null) {
            btnFeed.setOnClickListener(v -> {
                String recognized = sentenceText2.getText() != null ? sentenceText2.getText().toString() : "";
                Intent i = new Intent(this, StudyFeedbackActivity.class);
                i.putExtra(PronunciationExtras.RECOGNIZED_TEXT, recognized);
                i.putExtra(PronunciationExtras.PROMPT_TEXT, sentenceText.getText() != null ? sentenceText.getText().toString() : "");
                startActivity(i);
            });
        }
        // 🔒 상단 아바타 → 로그아웃 다이얼로그 연결
        wireLogoutButton();

        recorder = new AudioRecorderHelper(this);

        // ✅ BuildConfig로 키 접근 (build.gradle의 buildConfigField 사용)
        final String sttKey = BuildConfig.Google_STT_KEY;
        stt = new GoogleSTTRequester(sttKey);
        tts = new GoogleTTSRequester(this, sttKey);

        // Firebase Storage 업로더
        StorageReference rootRef = FirebaseStorage.getInstance().getReference();
        uploader = new FirebaseAudioUrlProvider(rootRef);

        // TheFluent API 클라이언트 (BuildConfig로 통일 권장)
        final String fluentBase   = BuildConfig.THEFLUENT_BASE;        // 예: "https://api.thefluent.me"
        final String fluentApiKey = BuildConfig.Fluent_STT_KEY;        // build.gradle에 정의
        final String authHeader   = BuildConfig.THEFLUENT_AUTH_HEADER; // 예: "Authorization"
        final String authPrefix   = BuildConfig.THEFLUENT_AUTH_PREFIX; // 예: "Bearer "
        fluent = new TheFluentClient(fluentBase, fluentApiKey, authHeader, authPrefix);

        // 인텐트로부터 문장/언어 적용
        String prompt = getIntent().getStringExtra(PronunciationExtras.PROMPT_TEXT);
        if (prompt != null && !prompt.isEmpty()) sentenceText.setText(prompt);

        String lang = getIntent().getStringExtra("langTag");
        if (lang != null && !lang.isEmpty()) languageCode = lang;

        // 뒤로가기
        backButton.setOnClickListener(v -> finish());

        // AI 음성 (TTS) 토글: 켜면 한번 재생 후 바로 off
        aiVoiceSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                String text = sentenceText.getText() != null ? sentenceText.getText().toString() : "";
                tts.speak(text, languageCode, false);
                aiVoiceSwitch.setChecked(false);
            }
        });

        // 내 녹음 재생 토글
        recordedPlaySwitch.setOnCheckedChangeListener((b, checked) -> {
            if (checked) playRecorded(); else stopPlayer();
        });

        // 마이크 버튼
        micButton.setOnClickListener(v -> onMicClicked());

        // "다음 문장" (btn_next)
        btnNext.setOnClickListener(v -> toast("다음 문장을 준비 중입니다."));

        // "피드백" (btnFeed) → StudyFeedbackActivity로 이동
        btnFeed.setOnClickListener(v -> {
            String recognized = sentenceText2.getText() != null ? sentenceText2.getText().toString() : "";
            Intent i = new Intent(this, StudyFeedbackActivity.class);
            i.putExtra(PronunciationExtras.RECOGNIZED_TEXT, recognized);
            i.putExtra(PronunciationExtras.PROMPT_TEXT, sentenceText.getText() != null ? sentenceText.getText().toString() : "");
            startActivity(i);
        });

        // 하단 네비게이션
        navStudy.setOnClickListener(v ->
                startActivity(new Intent(this, StudyStartActivity.class)));
        navArchive.setOnClickListener(v ->
                startActivity(new Intent(this, ArchiveTranslationActivity.class)));
        navTranslate.setOnClickListener(v ->
                startActivity(new Intent(this, TranslateActivity.class)));
    }

    /** 상단 아바타 버튼을 로그아웃 다이얼로그로 연결 */
    private void wireLogoutButton() {
        ImageView logoutBtn = findViewById(R.id.avatar_image); // 상단 오른쪽 이미지 버튼
        if (logoutBtn == null) return;

        logoutBtn.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle("로그아웃")
                        .setMessage("정말 로그아웃하시겠어요?")
                        .setPositiveButton("로그아웃", (d, w) -> {
                            LogoutManager.logout(this, new LogoutManager.Callback() {
                                @Override public void onSuccess() {
                                    Toast.makeText(getApplicationContext(), "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show();
                                    Intent i = new Intent(getApplicationContext(), LoginActivity.class);
                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // 뒤로가기 막기
                                    startActivity(i);
                                }
                                @Override public void onError(String msg) {
                                    Toast.makeText(getApplicationContext(), "로그아웃 실패: " + msg, Toast.LENGTH_SHORT).show();
                                }
                            });
                        })
                        .setNegativeButton("취소", null)
                        .show()
        );
    }

    private void onMicClicked() {
        if (!isRecording) ensureMicPermissionThenStart();
        else stopRecordingAndRunSTT();
    }

    private void ensureMicPermissionThenStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) startRecording();
        else reqMic.launch(Manifest.permission.RECORD_AUDIO);
    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            toast("마이크 권한이 필요합니다.");
            return;
        }
        try {
            String fileName = "study_pron_" + System.currentTimeMillis();
            recorder.startRecording(fileName);
            isRecording = true;
            micButton.setImageResource(android.R.drawable.ic_media_pause);
            toast("녹음을 시작했어요.");
        } catch (Exception e) {
            toast("녹음 시작 실패: " + e.getMessage());
        }
    }

    private void stopRecordingAndRunSTT() {
        try { recorder.stopRecording(); } catch (Exception ignore) {}
        isRecording = false;
        micButton.setImageResource(android.R.drawable.ic_btn_speak_now);

        String path = recorder.getOutputFilePath();
        if (path == null) { toast("녹음 파일 경로를 찾지 못했습니다."); return; }
        File wav = new File(path);
        if (!wav.exists()) { toast("녹음 파일이 없습니다."); return; }

        stt.recognizeWavFile(wav, languageCode, new GoogleSTTRequester.STTCallback() {
            @Override public void onSuccess(@NonNull String transcript) {
                runOnUiThread(() -> sentenceText2.setText(transcript));

                String reference = sentenceText.getText() != null ? sentenceText.getText().toString() : "";
                String langId = resolveTheFluentLanguageId(languageCode);
                int scale = 100;

                fluent.evaluatePronunciation(
                        new File(recorder.getOutputFilePath()),
                        reference,
                        langId,
                        scale,
                        uploader,
                        new TheFluentClient.EvalCallback() {
                            @Override public void onSuccess(float overall, float[] scores, @NonNull String rawJson) {
                                Intent i = new Intent(StudyPronunciationActivity.this, StudyFeedbackActivity.class);
                                i.putExtra(PronunciationExtras.OVERALL, overall);
                                i.putExtra(PronunciationExtras.SCORES, scores);
                                i.putExtra(PronunciationExtras.PROMPT_TEXT, reference);
                                i.putExtra(PronunciationExtras.RECOGNIZED_TEXT, transcript);
                                i.putExtra(PronunciationExtras.PRON_RESULT_JSON, rawJson);
                                startActivity(i);
                            }
                            @Override public void onError(@NonNull String message) {
                                runOnUiThread(() -> toast("발음평가 오류: " + message));
                            }
                        }
                );
            }
            @Override public void onAlternatives(@NonNull String[] alts) {}
            @Override public void onError(@NonNull String message) {
                runOnUiThread(() -> toast("STT 오류: " + message));
            }
        });
    }

    private void playRecorded() {
        String path = recorder.getOutputFilePath();
        if (path == null) { toast("재생할 녹음이 없습니다."); recordedPlaySwitch.setChecked(false); return; }
        try {
            stopPlayer();
            player = new MediaPlayer();
            player.setDataSource(path);
            player.setOnCompletionListener(mp -> { stopPlayer(); recordedPlaySwitch.setChecked(false); });
            player.prepare();
            player.start();
        } catch (Exception e) {
            toast("재생 실패: " + e.getMessage());
            recordedPlaySwitch.setChecked(false);
            stopPlayer();
        }
    }

    private void stopPlayer() {
        try {
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }
        } catch (Exception ignore) {}
    }

    private String resolveTheFluentLanguageId(String bcp47) {
        if (bcp47 == null) return "22";
        String s = bcp47.toLowerCase();
        if (s.startsWith("en")) return "22";
        if (s.startsWith("ko")) return "??";
        if (s.startsWith("ja")) return "??";
        if (s.startsWith("zh")) return "??";
        return "22";
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (isRecording) { try { recorder.stopRecording(); } catch (Exception ignore) {} isRecording = false; }
        stopPlayer();
    }
}
