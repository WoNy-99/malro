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

public class TodayPronunciationActivity extends AppCompatActivity {

    // UI
    private ImageButton backButton;
    private SwitchMaterial aiVoiceSwitch;
    private SwitchMaterial recordedPlaySwitch;
    private FloatingActionButton micButton;
    private MaterialButton btnFeed;                 // XML: @+id/btnFeed
    private MaterialButton navStudy, navArchive, navTranslate; // XML: nav_study/nav_archive/nav_translate
    private TextView sentenceText, sentenceText2;

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
        setContentView(R.layout.activity_today_pronunciation);

        FirebaseApp.initializeApp(this);
        wireLogoutButton(); // ✅ 상단 아바타 로그아웃 연결

        // XML id 바인딩 (activity_today_pronunciation.xml과 1:1 매칭)
        backButton         = findViewById(R.id.btnBack);
        aiVoiceSwitch      = findViewById(R.id.ai_voice_switch);
        recordedPlaySwitch = findViewById(R.id.ai_voice_switch_2);
        micButton          = findViewById(R.id.mic_button);
        btnFeed            = findViewById(R.id.btnFeed);

        navStudy           = findViewById(R.id.nav_study);
        navArchive         = findViewById(R.id.nav_archive);
        navTranslate       = findViewById(R.id.nav_translate);

        sentenceText       = findViewById(R.id.sentence_text);
        sentenceText2      = findViewById(R.id.sentence_text_2);

        // 뒤로가기
        backButton.setOnClickListener(v -> finish());

        if (btnFeed != null) {
            btnFeed.setOnClickListener(v -> {
                String recognized = sentenceText2.getText() != null ? sentenceText2.getText().toString() : "";
                Intent i = new Intent(this, TodayStudyFeedbackActivity.class);
                i.putExtra(PronunciationExtras.RECOGNIZED_TEXT, recognized);
                i.putExtra(PronunciationExtras.PROMPT_TEXT, sentenceText.getText() != null ? sentenceText.getText().toString() : "");
                startActivity(i);
            });
        }
        // 객체 초기화
        recorder = new AudioRecorderHelper(this);

        final String sttKey = BuildConfig.Google_STT_KEY;
        stt = new GoogleSTTRequester(sttKey);
        tts = new GoogleTTSRequester(this, sttKey);

        StorageReference rootRef = FirebaseStorage.getInstance().getReference();
        uploader = new FirebaseAudioUrlProvider(rootRef);

        final String fluentBase   = BuildConfig.THEFLUENT_BASE;
        final String fluentApiKey = BuildConfig.Fluent_STT_KEY;        // (이미 존재)
        final String authHeader   = BuildConfig.THEFLUENT_AUTH_HEADER;
        final String authPrefix   = BuildConfig.THEFLUENT_AUTH_PREFIX;
        fluent = new TheFluentClient(fluentBase, fluentApiKey, authHeader, authPrefix);

        // 외부 전달값 반영
        String prompt = getIntent().getStringExtra(PronunciationExtras.PROMPT_TEXT);
        if (prompt != null && !prompt.isEmpty()) sentenceText.setText(prompt);
        String lang = getIntent().getStringExtra("langTag");
        if (lang != null && !lang.isEmpty()) languageCode = lang;

        // 예문 TTS 토글: 켜면 1회 재생 후 끄기
        aiVoiceSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                String text = sentenceText.getText() != null ? sentenceText.getText().toString() : "";
                tts.speak(text, languageCode, false);
                aiVoiceSwitch.setChecked(false);
            }
        });

        // 녹음본 재생 토글
        recordedPlaySwitch.setOnCheckedChangeListener((b, checked) -> {
            if (checked) playRecorded(); else stopPlayer();
        });

        // 마이크
        micButton.setOnClickListener(v -> onMicClicked());

        // 피드백 화면 이동
        btnFeed.setOnClickListener(v -> {
            String recognized = sentenceText2.getText() != null ? sentenceText2.getText().toString() : "";
            Intent i = new Intent(this, TodayStudyFeedbackActivity.class);
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

    /** 상단 아바타 버튼 → 로그아웃 다이얼로그 연결 */
    private void wireLogoutButton() {
        ImageView logoutBtn = findViewById(R.id.avatar_image); // 상단 오른쪽 버튼
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            toast("마이크 권한이 필요합니다.");
            return;
        }
        try {
            String fileName = "today_pron_" + System.currentTimeMillis();
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
                                Intent i = new Intent(TodayPronunciationActivity.this, TodayStudyFeedbackActivity.class);
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
