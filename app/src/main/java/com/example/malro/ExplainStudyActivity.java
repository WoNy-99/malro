package com.example.malro; // 실제 패키지명에 맞게 수정하세요

import android.content.Intent; // ★ Intent import 추가
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

public class ExplainStudyActivity extends AppCompatActivity {

    private LinearLayout dotIndicator;
    private View dimOverlay;
    private ConstraintLayout explainBubble;
    private TextView explainText;
    private Button btnNext;
    private ImageView whiteCircle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_explain_study);

        // View 바인딩
        dotIndicator = findViewById(R.id.dotIndicator);
        dimOverlay = findViewById(R.id.dimOverlay);
        explainBubble = findViewById(R.id.explainBubble);
        explainText = findViewById(R.id.explainText);
        btnNext = findViewById(R.id.btnNext);
        whiteCircle = findViewById(R.id.white_circle);

        // "NEXT" 버튼 클릭 시 ExplainTransActivity로 전환
        btnNext.setOnClickListener(v -> {
            Intent intent = new Intent(ExplainStudyActivity.this, ExplainTransActivity.class);
            startActivity(intent);
            // 현재 액티비티를 종료하려면 아래를 활성화
            // finish();
        });

        // dimOverlay 클릭 시 (오버레이와 말풍선 숨기기)
        dimOverlay.setOnClickListener(v -> {
            dimOverlay.setVisibility(View.GONE);
            explainBubble.setVisibility(View.GONE);
        });

        // 추가 동작이 필요하다면 이곳에 더 구현하세요
    }
}
