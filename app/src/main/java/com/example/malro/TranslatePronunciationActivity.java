package com.example.malro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

// Firebase
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;

// ✅ 로그아웃 다이얼로그/유틸 import
import android.widget.ImageView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.malro.utils.LogoutManager;

public class TranslatePronunciationActivity extends AppCompatActivity {

    // UI (XML과 1:1 매칭)
    private ImageButton backButton;                 // @id/btnBack
    private SwitchMaterial aiVoiceSwitch;           // @id/ai_voice_switch
    private SwitchMaterial recordedPlaySwitch;      // @id/ai_voice_switch_2
    private FloatingActionButton micButton;         // @id/mic_button
    private MaterialButton btnNext;                 // @id/btn_next
    private MaterialButton btnFeed;                 // @id/btnFeed
    private MaterialButton navStudy, navArchive, navTranslate; // @id/nav_study, @id/nav_archive, @id/nav_translate
    private TextView sentenceText;                  // @id/sentence_text
    private TextView sentenceText2;                 // @id/sentence_text_2

    // 오디오
    private AudioRecorderHelper recorder;
    private boolean isRecording = false;
    private MediaPlayer player;

    // STT/TTS
    private GoogleSTTRequester stt;
    private GoogleTTSRequester tts;

    // 업로드/평가
    private String languageCode = "en-US";
    private AudioUrlProvider uploader;
    private TheFluentClient fluent;

    // 권한 런처
    private final ActivityResultLauncher<String> reqMic =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> { if (granted) startRecording(); else toast("마이크 권한이 필요합니다."); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translation_pronunciation);

        // ✅ 상단 아바타 → 로그아웃 연결
        wireLogoutButton();

        // Firebase 초기화(전역에서 한 번만 한다면 MyApplication에서 처리 가능)
        FirebaseApp.initializeApp(this);

        // ===== View 바인딩 =====
        backButton         = findViewById(R.id.btnBack);
        aiVoiceSwitch      = findViewById(R.id.ai_voice_switch);
        recordedPlaySwitch = findViewById(R.id.ai_voice_switch_2);
        micButton          = findViewById(R.id.mic_button);

        btnNext            = findViewById(R.id.btn_next);
        btnFeed            = findViewById(R.id.btnFeed);

        navStudy           = findViewById(R.id.nav_study);
        navArchive         = findViewById(R.id.nav_archive);
        navTranslate       = findViewById(R.id.nav_translate);

        sentenceText       = findViewById(R.id.sentence_text);
        sentenceText2      = findViewById(R.id.sentence_text_2);

        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                toast("다음 문장을 준비 중입니다.");
                // TODO: 실제 '다음 문장' 로딩 로직 연결 지점
            });
        }
        if (btnFeed != null) {
            btnFeed.setOnClickListener(v -> {
                String recognized = sentenceText2.getText() != null ? sentenceText2.getText().toString() : "";
                Intent intent = new Intent(this, TranslateFeedbackActivity.class);
                intent.putExtra("translatedText", recognized);
                intent.putExtra("promptText", sentenceText.getText() != null ? sentenceText.getText().toString() : "");
                startActivity(intent);
            });
        }
        // ===== 객체 초기화 =====
        recorder = new AudioRecorderHelper(this);

        // 키/설정은 strings.xml을 사용하는 현재 방식 유지
        final String sttKey       = BuildConfig.Google_STT_KEY;
        stt = new GoogleSTTRequester(sttKey);
        tts = new GoogleTTSRequester(this, sttKey);

        StorageReference rootRef = FirebaseStorage.getInstance().getReference();
        uploader = new FirebaseAudioUrlProvider(rootRef);

        final String fluentBase   = BuildConfig.THEFLUENT_BASE;
        final String fluentApiKey = BuildConfig.Fluent_STT_KEY;
        final String authHeader   = BuildConfig.THEFLUENT_AUTH_HEADER;
        final String authPrefix   = BuildConfig.THEFLUENT_AUTH_PREFIX;

        fluent = new TheFluentClient(fluentBase, fluentApiKey, authHeader, authPrefix);

        // 외부 인텐트 파라미터
        String prompt = getIntent().getStringExtra("promptText");
        if (prompt != null && !prompt.isEmpty()) sentenceText.setText(prompt);
        String lang = getIntent().getStringExtra("langTag");
        if (lang != null && !lang.isEmpty()) languageCode = lang;

        // 뒤로가기
        backButton.setOnClickListener(v -> finish());

        // 예문 TTS
        aiVoiceSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) {
                String text = sentenceText.getText() != null ? sentenceText.getText().toString() : "";
                tts.speak(text, languageCode, false);
                aiVoiceSwitch.setChecked(false);
            }
        });

        // 녹음 재생
        recordedPlaySwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) playRecorded(); else stopPlayer();
        });

        // 마이크
        micButton.setOnClickListener(v -> onMicClicked());

        // 다음 문장
        btnNext.setOnClickListener(v -> toast("다음 문장을 준비 중입니다."));

        // 피드백 화면 이동
        btnFeed.setOnClickListener(v -> {
            String recognized = sentenceText2.getText() != null ? sentenceText2.getText().toString() : "";
            Intent intent = new Intent(this, TranslateFeedbackActivity.class);
            intent.putExtra("translatedText", recognized);
            intent.putExtra("promptText", sentenceText.getText() != null ? sentenceText.getText().toString() : "");
            startActivity(intent);
        });

        // 하단 네비게이션
        navStudy.setOnClickListener(v ->
                startActivity(new Intent(this, StudyStartActivity.class)));
        navArchive.setOnClickListener(v ->
                startActivity(new Intent(this, ArchiveTranslationActivity.class)));
        navTranslate.setOnClickListener(v ->
                startActivity(new Intent(this, TranslateActivity.class)));
    }

    // ------------------------
    // 로그아웃 버튼 연결 메서드
    // ------------------------
    private void wireLogoutButton() {
        ImageView logoutBtn = findViewById(R.id.avatar_image); // 상단 오른쪽 버튼(id가 레이아웃에 있어야 함)
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
                == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            reqMic.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startRecording() {
        // 권한 재확인(보호적)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            toast("마이크 권한이 필요합니다.");
            return;
        }
        try {
            String fileName = "trans_pron_" + System.currentTimeMillis();
            recorder.startRecording(fileName);
            isRecording = true;
            micButton.setImageResource(android.R.drawable.ic_media_pause);
            toast("녹음을 시작했어요.");
        } catch (SecurityException se) {
            toast("권한이 없어 녹음을 시작할 수 없습니다.");
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
                                Intent i = new Intent(TranslatePronunciationActivity.this, TranslateFeedbackActivity.class);
                                i.putExtra("overall", overall);
                                i.putExtra("scores", scores);
                                i.putExtra("promptText", reference);
                                i.putExtra("recognizedText", transcript);
                                i.putExtra("pron_result_json", rawJson);
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
        if (path == null) {
            toast("재생할 녹음이 없습니다.");
            recordedPlaySwitch.setChecked(false);
            return;
        }
        try {
            stopPlayer();
            player = new MediaPlayer();
            player.setDataSource(path);
            player.setOnCompletionListener(mp -> {
                stopPlayer();
                recordedPlaySwitch.setChecked(false);
            });
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

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isRecording) {
            try { recorder.stopRecording(); } catch (Exception ignore) {}
            isRecording = false;
        }
        stopPlayer();
    }
}
