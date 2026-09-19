package com.example.malro;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.malro.utils.PronunciationExtras;

// ✅ MPAndroidChart
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

// ✅ 로그아웃 다이얼로그/유틸
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.malro.utils.LogoutManager;

import java.util.ArrayList;
import java.util.List;

public class TranslateFeedbackActivity extends AppCompatActivity {

    // XML과 1:1 매칭되는 뷰
    // (중요) activity_study_today_feedback.xml에는 LineChart(@id/feedbackChart)가 있으므로 타입을 LineChart로 맞춥니다.
    private LineChart feedbackChart;         // @id/feedbackChart
    private TextView  feedbackDetailsText;   // @id/feedback_details_text
    private Button    buttonRetry;           // @id/button_retry
    private Button    buttonNext;            // @id/button_next

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_today_feedback);

        // ✅ 상단 아바타 → 로그아웃 연결
        wireLogoutButton();

        // 1) View 바인딩
        feedbackChart       = findViewById(R.id.feedbackChart);
        feedbackDetailsText = findViewById(R.id.feedback_details_text);
        buttonRetry         = findViewById(R.id.button_retry);
        buttonNext          = findViewById(R.id.button_next);

        // 하단 네비게이션: XML에 각 항목 id가 없으므로 자식 인덱스로 가져옴
        LinearLayout navigationBar = findViewById(R.id.navigation_bar);
        if (navigationBar != null && navigationBar.getChildCount() >= 3) {
            LinearLayout navLearn     = (LinearLayout) navigationBar.getChildAt(0); // "학습"
            LinearLayout navMain      = (LinearLayout) navigationBar.getChildAt(1); // "메인"
            LinearLayout navTranslate = (LinearLayout) navigationBar.getChildAt(2); // "번역"

            navLearn.setOnClickListener(v ->
                    startActivity(new Intent(this, StudyStartActivity.class)));

            navMain.setOnClickListener(v ->
                    startActivity(new Intent(this, MainActivity.class)));

            navTranslate.setOnClickListener(v ->
                    startActivity(new Intent(this, TranslateActivity.class)));
        }

        // 2) Intent 데이터 수신
        float   overall    = getIntent().getFloatExtra(PronunciationExtras.OVERALL, -1f);
        float[] scores     = getIntent().getFloatArrayExtra(PronunciationExtras.SCORES);
        String  prompt     = getIntent().getStringExtra(PronunciationExtras.PROMPT_TEXT);
        String  recognized = getIntent().getStringExtra(PronunciationExtras.RECOGNIZED_TEXT);
        String  rawJson    = getIntent().getStringExtra(PronunciationExtras.PRON_RESULT_JSON);

        // 3) 텍스트 UI 반영
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(prompt))     sb.append("원문: ").append(prompt).append('\n');
        if (!TextUtils.isEmpty(recognized)) sb.append("인식: ").append(recognized).append('\n');
        if (overall >= 0)                   sb.append("점수: ").append(String.format("%.1f", overall));
        feedbackDetailsText.setText(sb.toString());

        // 4) 차트 표시 (scores 있을 때만)
        if (scores != null && scores.length > 0 && feedbackChart != null) {
            setupChart(scores);
        }

        // 5) 버튼 동작
        buttonRetry.setOnClickListener(v -> finish()); // 다시 연습으로 복귀
        buttonNext.setOnClickListener(v -> {
            // TODO: 다음 문장 로직 연결
        });
    }

    /** MPAndroidChart 세팅 */
    private void setupChart(float[] scores) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            entries.add(new Entry(i, scores[i]));
        }

        LineDataSet dataSet = new LineDataSet(entries, "발음 정확도");
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);

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

        feedbackChart.getAxisLeft().setAxisMinimum(0f);
        feedbackChart.getAxisLeft().setAxisMaximum(100f);

        feedbackChart.animateY(800);
        feedbackChart.invalidate();
    }

    /** 상단 아바타(avatar_image) → 로그아웃 다이얼로그 */
    private void wireLogoutButton() {
        ImageView logoutBtn = findViewById(R.id.avatar_image); // 상단 오른쪽 이미지 버튼(id가 레이아웃에 있어야 합니다)
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
}
