package com.example.malro; // 실제 패키지명으로 교체

import android.content.Intent; // ← 인텐트 import가 필요합니다.
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

public class ExplainArActivity extends AppCompatActivity {

    private LinearLayout dotIndicator;
    private View dimOverlay;
    private ConstraintLayout explainBubble;
    private TextView explainText;
    private Button btnNext;
    private ImageView whiteCircle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_explain_ar);

        // 뷰 바인딩
        dotIndicator = findViewById(R.id.dotIndicator);
        dimOverlay = findViewById(R.id.dimOverlay);
        explainBubble = findViewById(R.id.explainBubble);
        explainText = findViewById(R.id.explainText);
        btnNext = findViewById(R.id.btnNext);
        whiteCircle = findViewById(R.id.white_circle);

        // "NEXT" 버튼 클릭 이벤트: ExplainRecordActivity로 화면 전환
        btnNext.setOnClickListener(v -> {
            Intent intent = new Intent(ExplainArActivity.this, ExplainRecordActivity.class);
            startActivity(intent);
            // 만약 현재 화면을 뒤로가기로 남기고 싶지 않으면 finish()도 호출 가능
            // finish();
        });

        // dimOverlay 클릭 시: 말풍선과 오버레이 숨기기 예시
        dimOverlay.setOnClickListener(v -> {
            dimOverlay.setVisibility(View.GONE);
            explainBubble.setVisibility(View.GONE);
        });

        // 점 인디케이터나 원 이미지 등 별도 동작이 필요하다면 여기에 구현
    }
}
