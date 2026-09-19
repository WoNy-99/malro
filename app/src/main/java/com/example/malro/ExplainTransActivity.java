package com.example.malro;

import android.content.Intent; // **Intent import 추가**
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

public class ExplainTransActivity extends AppCompatActivity {

    private LinearLayout dotIndicator;
    private View dimOverlay;
    private ConstraintLayout explainBubble;
    private TextView explainText;
    private Button btnNext;
    private ImageView whiteCircle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_explain_trans);

        dotIndicator = findViewById(R.id.dotIndicator);
        dimOverlay = findViewById(R.id.dimOverlay);
        explainBubble = findViewById(R.id.explainBubble);
        explainText = findViewById(R.id.explainText);
        btnNext = findViewById(R.id.btnNext);
        whiteCircle = findViewById(R.id.white_circle);

        // "NEXT" 버튼 클릭 시 TodayStcActivity로 이동
        btnNext.setOnClickListener(v -> {
            Intent intent = new Intent(ExplainTransActivity.this, TodayStudyFeedbackActivity.class);
            startActivity(intent);

            // 지금 화면을 닫고 싶으면 아래도 추가
            // finish();
        });

        dimOverlay.setOnClickListener(v -> {
            dimOverlay.setVisibility(View.GONE);
            explainBubble.setVisibility(View.GONE);
        });

        // 추가 동작이 필요하다면 이곳에 더 구현
    }
}
