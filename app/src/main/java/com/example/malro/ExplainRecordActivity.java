package com.example.malro;

import android.content.Intent; // 이 import가 필요합니다
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ExplainRecordActivity extends AppCompatActivity {

    private LinearLayout dotIndicator;
    private View dimOverlay;
    private androidx.constraintlayout.widget.ConstraintLayout explainBubble;
    private TextView explainText;
    private Button btnNext;
    private ImageView whiteCircle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_explain_record);

        dotIndicator = findViewById(R.id.dotIndicator);
        dimOverlay = findViewById(R.id.dimOverlay);
        explainBubble = findViewById(R.id.explainBubble);
        explainText = findViewById(R.id.explainText);
        btnNext = findViewById(R.id.btnNext);
        whiteCircle = findViewById(R.id.white_circle);

        // NEXT 버튼 클릭 시 ExplainStudyActivity로 이동
        btnNext.setOnClickListener(v -> {
            Intent intent = new Intent(ExplainRecordActivity.this, ExplainStudyActivity.class);
            startActivity(intent);
            // 현재 액티비티를 종료하려면 아래도 추가
            // finish();
        });

        dimOverlay.setOnClickListener(v -> {
            // 예: 말풍선 숨기기
            // explainBubble.setVisibility(View.GONE);
            // dimOverlay.setVisibility(View.GONE);
        });

        // 필요시 추가 로직 구현
    }
}
