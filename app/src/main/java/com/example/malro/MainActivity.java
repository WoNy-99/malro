package com.example.malro;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import com.example.malro.utils.LogoutManager;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private TextView sentenceText;
    private ExtendedFloatingActionButton btnStudy;

    // XML과 1:1 매칭
    private MaterialButton navStudy, navArchive, navTranslate;

    private final String[] sentences = {
            "Where are you going?","Can you help me?","What is your name?","Nice to meet you.",
            "I’m hungry.","Let’s go!","See you tomorrow.","What time is it?","I love learning.",
            "This is my friend.","It's sunny today.","Do you speak English?","I’m from Korea.",
            "I need some water.","Please repeat that.","Have a nice day!","It's very hot.",
            "How old are you?","I like music.","Good job!"
    };

    private String todaySentence = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 상단 아바타 로그아웃 연결
        wireLogoutButton();

        // View 바인딩
        sentenceText = findViewById(R.id.sentence_text);
        btnStudy     = findViewById(R.id.extended_fab);
        navStudy     = findViewById(R.id.nav_study);
        navArchive   = findViewById(R.id.nav_archive);
        navTranslate = findViewById(R.id.nav_translate);

        // 오늘의 문장
        todaySentence = sentences[new Random().nextInt(sentences.length)];
        sentenceText.setText(todaySentence);

        // 학습하기(FAB) → 발음 연습
        if (btnStudy != null) {
            btnStudy.setOnClickListener(v -> {
                Intent intent = new Intent(this, TodayPronunciationActivity.class);
                intent.putExtra("sentence", todaySentence);
                startActivity(intent);
            });
        }

        // 하단 네비 - 학습
        if (navStudy != null) {
            navStudy.setOnClickListener(v ->
                    startActivity(new Intent(this, StudyStartActivity.class)));
        }

        // 하단 네비 - 번역
        if (navTranslate != null) {
            navTranslate.setOnClickListener(v ->
                    startActivity(new Intent(this, TranslateActivity.class)));
        }

        // 하단 네비 - 아카이브 (게스트 금지 / 카카오 로그인만 허용)
        if (navArchive != null) {
            navArchive.setOnClickListener(v -> {
                if (isKakaoLoggedInPref()) {
                    startActivity(new Intent(this, ArchiveTranslationActivity.class));
                } else {
                    Intent i = new Intent(this, LoginActivity.class);
                    i.putExtra("redirect", "archive");
                    startActivity(i);
                }
            });
        }
    }

    /** 아바타(우상단) → 로그아웃 다이얼로그 */
    private void wireLogoutButton() {
        ImageView logoutBtn = findViewById(R.id.avatar_image); // 상단 오른쪽 버튼 id
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

    /** LoginActivity에서 저장한 값 기반의 간단한 카카오 로그인 판별 */
    private boolean isKakaoLoggedInPref() {
        SharedPreferences prefs = getSharedPreferences("user_status", MODE_PRIVATE);
        boolean isGuest = prefs.getBoolean("isGuest", true); // 기본값 true: 저장 전 = 게스트
        return !isGuest; // 비게스트 = 카카오 로그인
    }
}
