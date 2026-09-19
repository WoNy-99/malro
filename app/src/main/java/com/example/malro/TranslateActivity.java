package com.example.malro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView; // ✅ 추가
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder; // ✅ 추가
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import com.example.malro.utils.LogoutManager; // ✅ 추가

import java.io.File;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;

public class TranslateActivity extends AppCompatActivity {

    // App bar
    private ImageButton btnBack;

    // 입력/출력 영역
    private Spinner spinnerSourceLanguage, spinnerTargetLanguage;
    private EditText inputEditText;
    private TextView textTranslatedOutput;

    private final Handler autoHandler = new Handler(Looper.getMainLooper());
    private Runnable autoTask;
    private static final long AUTO_DELAY_MS = 500L; // 0.5초 디바운스

    // 버튼
    private ImageButton btnVoiceInput;       // 음성 입력 (녹음 토글)
    private ImageButton btnPlayInputAudio;   // 입력 텍스트 TTS
    private ImageButton btnPlayOutputAudio;  // 출력 텍스트 TTS
    private ImageButton btnSwitchLanguages;  // 언어/텍스트 스왑
    private ExtendedFloatingActionButton extendedFab; // 학습하기

    // 도움 객체
    private AudioRecorderHelper recorder;
    private boolean isRecording = false;
    private String lastAudioPath = null;

    private GoogleSTTRequester stt;                 // Google Cloud STT (REST)
    private GoogleTTSRequester tts;                 // Google Cloud TTS (REST)
    private TranslateRequester requester;           // Azure Translator 래퍼

    // 마이크 권한 런처
    private final ActivityResultLauncher<String> reqMic =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) startRecording();
                        else toast("마이크 권한이 필요합니다.");
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translation);

        // ✅ 상단 아바타 → 로그아웃 연결
        wireLogoutButton();

        // ====== View 바인딩 (XML id와 동일) ======
        btnBack               = findViewById(R.id.btnBack);

        spinnerSourceLanguage = findViewById(R.id.spinnerSourceLanguage);
        spinnerTargetLanguage = findViewById(R.id.spinnerTargetLanguage);

        inputEditText         = findViewById(R.id.inputEditText);
        textTranslatedOutput  = findViewById(R.id.textTranslatedOutput);

        btnVoiceInput         = findViewById(R.id.btnVoiceInput);
        btnPlayInputAudio     = findViewById(R.id.btnPlayInputAudio);
        btnPlayOutputAudio    = findViewById(R.id.btnPlayOutputAudio);
        btnSwitchLanguages    = findViewById(R.id.btnSwitchLanguages);

        extendedFab           = findViewById(R.id.extended_fab);

        // ====== 객체 초기화 ======
        recorder = new AudioRecorderHelper(this);
        stt      = new GoogleSTTRequester(BuildConfig.Google_STT_KEY);
        tts      = new GoogleTTSRequester(this, BuildConfig.Google_STT_KEY);
        requester= new TranslateRequester(
                BuildConfig.AZURE_ENDPOINT,
                BuildConfig.AZURE_KEY,
                BuildConfig.AZURE_REGION
        );

        // ====== 리스너 ======
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        if (btnVoiceInput != null) {
            btnVoiceInput.setOnClickListener(v -> onMicToggled());
        }

        if (btnPlayInputAudio != null) {
            btnPlayInputAudio.setOnClickListener(v -> {
                String txt = safe(inputEditText);
                if (txt.isEmpty()) { toast("입력 텍스트가 없습니다."); return; }
                String tag = getSelectedLangTag(spinnerSourceLanguage, "ko-KR");
                tts.speak(txt, tag, false);
            });
        }

        if (btnPlayOutputAudio != null) {
            btnPlayOutputAudio.setOnClickListener(v -> {
                String txt = safe(textTranslatedOutput);
                if (txt.isEmpty()) { toast("출력 텍스트가 없습니다."); return; }
                String tag = getSelectedLangTag(spinnerTargetLanguage, "en-US");
                tts.speak(txt, tag, false);
            });
        }

        if (btnSwitchLanguages != null) {
            btnSwitchLanguages.setOnClickListener(v -> switchLanguages());
        }

        if (extendedFab != null) {
            extendedFab.setOnClickListener(v -> goToStudy());
        }

        // 입력 즉시 자동 번역 (디바운스)
        inputEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override public void afterTextChanged(Editable s) {
                // 녹음 중엔 자동 번역 off (STT 입력 간섭 방지)
                if (isRecording) return;

                autoHandler.removeCallbacks(autoTask);
                autoTask = () -> {
                    String text = safe(inputEditText);
                    if (!text.isEmpty()) doTranslate();
                    else textTranslatedOutput.setText(""); // 입력 비면 결과도 비움
                };
                autoHandler.postDelayed(autoTask, AUTO_DELAY_MS);
            }
        });

// 키보드의 완료(완료/Enter)로도 번역 트리거
        inputEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doTranslate();
                return true;
            }
            return false;
        });
    }

    // ====== 녹음/인식 ======
    private void onMicToggled() {
        if (!isRecording) {
            ensureMicPermissionThenStart();
        } else {
            stopRecordingAndRunSTT();
        }
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
        try {
            String fileName = "trans_" + System.currentTimeMillis();
            recorder.startRecording(fileName);
            isRecording = true;
            btnVoiceInput.setImageResource(android.R.drawable.ic_media_pause);
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
        btnVoiceInput.setImageResource(0);
        btnVoiceInput.setBackgroundResource(R.drawable.ic_mic);

        lastAudioPath = recorder.getOutputFilePath();
        if (lastAudioPath == null) {
            toast("녹음 파일 경로를 찾지 못했습니다.");
            return;
        }
        File wav = new File(lastAudioPath);
        if (!wav.exists()) {
            toast("녹음 파일이 없습니다.");
            return;
        }

        String srcTag = getSelectedLangTag(spinnerSourceLanguage, "ko-KR");

        stt.recognizeWavFile(wav, srcTag, new GoogleSTTRequester.STTCallback() {
            @Override public void onSuccess(@NonNull String transcript) {
                runOnUiThread(() -> inputEditText.setText(transcript));
            }
            @Override public void onAlternatives(@NonNull String[] alts) { }
            @Override public void onError(@NonNull String message) {
                runOnUiThread(() -> toast("STT 오류: " + message));
            }
        });
    }

    // ====== 번역 ======
    private void doTranslate() {
        String srcText = safe(inputEditText);
        if (srcText.isEmpty()) { toast("번역할 문장을 입력하세요."); return; }

        String fromTag = getSelectedLangTag(spinnerSourceLanguage, "ko-KR");
        String toTag   = getSelectedLangTag(spinnerTargetLanguage, "en-US");
        String fromIso = toIso2(fromTag);
        String toIso   = toIso2(toTag);

        requester.translate(srcText, fromIso, toIso, new TranslateRequester.ResultCallback() {
            @Override public void onSuccess(@NonNull String translated) {
                runOnUiThread(() -> textTranslatedOutput.setText(translated));
            }
            @Override public void onError(@NonNull String message) {
                runOnUiThread(() -> toast("번역 오류: " + message));
            }
        });
    }

    // 언어/텍스트 스왑
    private void switchLanguages() {
        int srcPos = spinnerSourceLanguage.getSelectedItemPosition();
        int tgtPos = spinnerTargetLanguage.getSelectedItemPosition();
        spinnerSourceLanguage.setSelected(false);
        spinnerTargetLanguage.setSelected(false);
        spinnerSourceLanguage.setSelection(tgtPos);
        spinnerTargetLanguage.setSelection(srcPos);

        String in = safe(inputEditText);
        String out = safe(textTranslatedOutput);
        inputEditText.setText(out);
        textTranslatedOutput.setText(in);
    }

    // 학습 화면 이동
    private void goToStudy() {
        if (TextUtils.isEmpty(safe(textTranslatedOutput))) {
            doTranslate(); // 출력이 비어 있으면 일단 번역 한 번 수행
        }
        Intent i = new Intent(this, TranslateStudyActivity.class); // ✅ 여기만 변경
        i.putExtra("sourceText", safe(inputEditText));
        i.putExtra("targetText", safe(textTranslatedOutput));
        startActivity(i);
    }

    // ====== 유틸 ======
    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private String safe(EditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }

    private String safe(TextView tv) {
        return tv != null && tv.getText() != null ? tv.getText().toString().trim() : "";
    }

    /** 스피너 선택값에서 BCP-47 언어 태그 추출 */
    private String getSelectedLangTag(Spinner spinner, String fallback) {
        if (spinner == null || spinner.getSelectedItem() == null) return fallback;
        String s = spinner.getSelectedItem().toString();

        Pattern p = Pattern.compile("([a-zA-Z]{2,3})(-[a-zA-Z]{2,3})?");
        Matcher m = Pattern.compile("\\(([^)]+)\\)").matcher(s);
        if (m.find()) {
            String inside = m.group(1);
            if (p.matcher(inside).matches()) return inside;
        }

        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.contains("korean") || lower.contains("한국")) return "ko-KR";
        if (lower.contains("english")|| lower.contains("영어")) return "en-US";
        if (lower.contains("japanese")|| lower.contains("일본")) return "ja-JP";
        if (lower.contains("chinese") || lower.contains("중국")) return "zh-CN";
        if (lower.contains("spanish") || lower.contains("스페인")) return "es-ES";
        if (lower.contains("french")  || lower.contains("프랑스")) return "fr-FR";
        if (lower.contains("german")  || lower.contains("독일")) return "de-DE";

        return fallback;
    }

    /** BCP-47에서 ISO-639-1 코드 추출 (Azure Translator용) */
    private String toIso2(String tag) {
        try {
            Locale l = Locale.forLanguageTag(tag);
            String code = l.getLanguage();
            return (code != null && !code.isEmpty()) ? code : "en";
        } catch (Exception e) {
            return "en";
        }
    }

    /** ✅ 상단 아바타(avatar_image) → 로그아웃 다이얼로그 */
    private void wireLogoutButton() {
        ImageView logoutBtn = findViewById(R.id.avatar_image);
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
                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isRecording) {
            try { recorder.stopRecording(); } catch (Exception ignore) {}
            isRecording = false;
        }
        autoHandler.removeCallbacksAndMessages(null); // 🔐 메모리 릭 방지
    }
}
