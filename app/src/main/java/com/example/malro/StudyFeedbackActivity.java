package com.example.malro;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.malro.utils.LogoutManager;
import com.example.malro.utils.PronunciationExtras;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class StudyFeedbackActivity extends AppCompatActivity {

    private LineChart feedbackChart;
    private TextView sentenceTextView;
    private TextView feedbackDetailsText;
    private Button buttonRetry, buttonNext;
    private ExtendedFloatingActionButton saveFab;
    private ImageButton backButton;
    private MaterialButton navStudy, navArchive, navTranslate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_feedback);

        // XML id 바인딩
        feedbackChart       = findViewById(R.id.feedbackChart);
        sentenceTextView    = findViewById(R.id.sentence_text_2);
        feedbackDetailsText = findViewById(R.id.feedback_details_text);
        buttonRetry         = findViewById(R.id.button_retry);
        buttonNext          = findViewById(R.id.button_next);
        saveFab             = findViewById(R.id.extended_fab);
        backButton          = findViewById(R.id.btnBack);
        navStudy            = findViewById(R.id.nav_study);
        navArchive          = findViewById(R.id.nav_archive);
        navTranslate        = findViewById(R.id.nav_translate);

        // 오른쪽 상단 아바타(로그아웃)
        wireLogoutButton();

        // 뒤로가기
        backButton.setOnClickListener(v -> finish());

        // 전달 데이터
        float overall      = getIntent().getFloatExtra(PronunciationExtras.OVERALL, 0f);
        float[] scores     = getIntent().getFloatArrayExtra(PronunciationExtras.SCORES);
        String prompt      = getIntent().getStringExtra(PronunciationExtras.PROMPT_TEXT);
        String recognized  = getIntent().getStringExtra(PronunciationExtras.RECOGNIZED_TEXT);
        String rawJson     = getIntent().getStringExtra(PronunciationExtras.PRON_RESULT_JSON);

        // UI 반영
        if (recognized != null) {
            sentenceTextView.setText(recognized);
        }
        if (prompt != null) {
            feedbackDetailsText.setText(
                    "원문: " + prompt +
                            "\n인식: " + (recognized == null ? "" : recognized) +
                            "\n점수: " + overall
            );
        }

        // 차트 표시
        if (scores != null && scores.length > 0) {
            setupFeedbackChart(scores);
        }

        // 버튼 이벤트
        buttonRetry.setOnClickListener(v -> finish()); // 다시 학습으로 복귀
        buttonNext.setOnClickListener(v -> {
            // TODO: 다음 문장 로딩 로직
        });

        saveFab.setOnClickListener(v -> {
            // TODO: 결과 저장 로직
        });

        // 하단 네비게이션
        navStudy.setOnClickListener(v ->
                startActivity(new Intent(this, StudyStartActivity.class)));

        navArchive.setOnClickListener(v ->
                startActivity(new Intent(this, ArchiveTranslationActivity.class)));

        navTranslate.setOnClickListener(v ->
                startActivity(new Intent(this, TranslateActivity.class)));
    }

    /** 전달받은 발음 점수 배열로 차트 세팅 */
    private void setupFeedbackChart(float[] scores) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            entries.add(new Entry(i, scores[i]));
        }

        LineDataSet dataSet = new LineDataSet(entries, "발음 정확도");
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);

        int lineColor = ContextCompat.getColor(this, android.R.color.holo_blue_dark);
        dataSet.setColor(lineColor);
        dataSet.setCircleColor(lineColor);

        LineData lineData = new LineData(dataSet);
        feedbackChart.setData(lineData);

        Description desc = new Description();
        desc.setText("문장별 발음 정확도");
        feedbackChart.setDescription(desc);

        feedbackChart.getAxisRight().setEnabled(false);

        XAxis x = feedbackChart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setDrawGridLines(false);

        // 점수 범위를 0~100으로 가정(필요시 조정/삭제)
        feedbackChart.getAxisLeft().setAxisMinimum(0f);
        feedbackChart.getAxisLeft().setAxisMaximum(100f);

        feedbackChart.animateY(1000);
        feedbackChart.invalidate();
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
                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // 뒤로가기 방지
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
}
