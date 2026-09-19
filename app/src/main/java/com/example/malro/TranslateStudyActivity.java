package com.example.malro;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.malro.utils.LogoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

public class TranslateStudyActivity extends AppCompatActivity {

    private ImageButton backButton;
    private RecyclerView recycler;           // 레이아웃에 존재(숨김)
    private AppCompatSpinner spinner;
    private TextView sentenceText;

    private ArrayList<String> originals;
    private ArrayList<String> translations;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translation_study);

        wireLogoutButton();

        backButton = findViewById(R.id.btnBack);
        if (backButton != null) backButton.setOnClickListener(v -> finish());

        sentenceText = findViewById(R.id.sentence_text);

        // 1) 데이터 준비
        originals    = getIntent().getStringArrayListExtra("originals");
        translations = getIntent().getStringArrayListExtra("translations");
        if (originals == null || translations == null || originals.size() != translations.size()) {
            originals    = new ArrayList<>();
            translations = new ArrayList<>();
            originals.add("Where are you going?");
            translations.add("어디 가고 있니?");
            originals.add("How are you today?");
            translations.add("오늘 기분은 어때?");
        }

        // 2) 스피너 세팅
        spinner = findViewById(R.id.spinner_sentence);
        ArrayList<String> display = new ArrayList<>();
        for (int i = 0; i < originals.size(); i++) {
            display.add(originals.get(i) + "  —  " + translations.get(i));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                display
        );
        spinner.setAdapter(adapter);

        if (!originals.isEmpty() && sentenceText != null) {
            sentenceText.setText(originals.get(0));
        }

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (sentenceText != null) sentenceText.setText(originals.get(position));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
        });

        // 3) RecyclerView는 심볼만 유지 (숨김)
        recycler = findViewById(R.id.translation_recycler_view);
        if (recycler != null) {
            recycler.setLayoutManager(new LinearLayoutManager(this));
            recycler.setVisibility(View.GONE);
        }

        // 4) 학습하기 → 선택 문장 전달
        View fab = findViewById(R.id.extended_fab);
        if (fab != null) {
            fab.setOnClickListener(v -> {
                int pos = spinner != null ? spinner.getSelectedItemPosition() : 0;
                String selected = originals.isEmpty() ? "" : originals.get(Math.max(0, pos));
                Intent i = new Intent(this, TranslatePronunciationActivity.class);
                i.putExtra("sentence", selected);
                startActivity(i);
            });
        }
    }

    // 로그아웃 버튼 연결
    private void wireLogoutButton() {
        ImageView logoutBtn = findViewById(R.id.avatar_image);
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
