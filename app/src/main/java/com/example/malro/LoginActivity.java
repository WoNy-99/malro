package com.example.malro;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class LoginActivity extends AppCompatActivity {

    private MaterialButton kakaoLoginButton;  // ⬅ LinearLayout → MaterialButton
    private MaterialButton guestLoginButton;  // ⬅ LinearLayout → MaterialButton

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login); // XML이 맞는지 확인

        kakaoLoginButton = findViewById(R.id.btnKakaoLogin);
        guestLoginButton = findViewById(R.id.btnGuestLogin);

        // 카카오 로그인
        kakaoLoginButton.setOnClickListener(v -> {
            saveLoginStatus(false, true); // isGuest=false, saveRecord=true
            MyApplication.getInstance().setGuestMode(false);
            MyApplication.getInstance().setSaveRecord(true);
            goToMain();
        });

        // 게스트 로그인
        guestLoginButton.setOnClickListener(v -> {
            saveLoginStatus(true, false); // isGuest=true, saveRecord=false
            MyApplication.getInstance().setGuestMode(true);
            MyApplication.getInstance().setSaveRecord(false);
            goToMain();
        });
    }

    private void saveLoginStatus(boolean isGuest, boolean saveRecord) {
        SharedPreferences prefs = getSharedPreferences("user_status", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("isGuest", isGuest)
                .putBoolean("saveRecord", saveRecord)
                .apply();
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
