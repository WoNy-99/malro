
package com.example.malro;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_splash); // 로고 + TextView(id=login_info)

        String last = getSharedPreferences("auth_prefs", MODE_PRIVATE).getString("last_provider", "none");
        TextView info = findViewById(R.id.login_info);
        info.setText("최근 로그인: " + ("kakao".equals(last) ? "카카오" : "게스트"));

        new Handler().postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, 1500);
    }

}