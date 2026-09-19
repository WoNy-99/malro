package com.example.malro;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.malro.utils.LogoutManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

public class StudyStartActivity extends AppCompatActivity {

    // XML과 동일한 타입/ID로 선언
    private Spinner splitButtonLang;   // @id/split_button_lang (Spinner)
    private Spinner splitButtonLevel;  // @id/split_button_lev  (Spinner)
    private ExtendedFloatingActionButton btnStart; // @id/extended_fab
    private ImageButton btnBack;       // @id/btnBack (상단바 뒤로가기)

    // 하단 네비 (MaterialButton)
    private MaterialButton navStudy, navArchive, navTranslate;

    // 선택 값
    private String selectedLanguage = "영어"; // 기본 언어
    private String selectedLevel    = "초급"; // 기본 수준

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_start);

        // 뷰 바인딩
        btnBack          = findViewById(R.id.btnBack);
        splitButtonLang  = findViewById(R.id.split_button_lang);
        splitButtonLevel = findViewById(R.id.split_button_lev);
        btnStart         = findViewById(R.id.extended_fab);

        navStudy         = findViewById(R.id.nav_study);
        navArchive       = findViewById(R.id.nav_archive);
        navTranslate     = findViewById(R.id.nav_translate);

        // 🔒 상단 아바타 → 로그아웃 다이얼로그 연결
        wireLogoutButton();

        // 뒤로가기
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Spinner 리스너: 선택 값 반영
        splitButtonLang.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);
                if (item != null) selectedLanguage = item.toString();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
        });

        splitButtonLevel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);
                if (item != null) selectedLevel = item.toString();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
        });

        // 시작 버튼 → 발음 학습 화면으로 이동
        btnStart.setOnClickListener(v -> {
            Intent intent = new Intent(StudyStartActivity.this, StudyPronunciationActivity.class);
            // 기존 키 유지 + langTag 함께 전달 (학습 화면에서 언어코드 필요 시)
            intent.putExtra("language", selectedLanguage);
            intent.putExtra("level", selectedLevel);
            intent.putExtra("langTag", mapLanguageToTag(selectedLanguage));
            startActivity(intent);
        });

        // 하단 네비
        navStudy.setOnClickListener(v ->
                startActivity(new Intent(this, StudyStartActivity.class)));
        navArchive.setOnClickListener(v ->
                startActivity(new Intent(this, ArchiveTranslationActivity.class)));
        navTranslate.setOnClickListener(v ->
                startActivity(new Intent(this, TranslateActivity.class)));
    }

    /** 상단 아바타 버튼을 로그아웃 다이얼로그로 연결 */
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

    /** 표시 언어 → BCP-47 언어 코드 매핑 */
    private String mapLanguageToTag(String lang) {
        if (lang == null) return "en-US";
        switch (lang) {
            case "영어":   return "en-US";
            case "일본어": return "ja-JP";
            case "중국어": return "zh-CN";
            case "한국어": return "ko-KR";
            default:       return "en-US";
        }
    }
}
