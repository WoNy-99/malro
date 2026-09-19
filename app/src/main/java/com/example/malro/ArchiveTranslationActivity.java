package com.example.malro;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.malro.utils.LogoutManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ArchiveTranslationActivity extends AppCompatActivity {

    private RecyclerView archiveRecyclerView;
    private ArchiveAdapter archiveAdapter;
    private final ArrayList<ArchiveItem> archiveList = new ArrayList<>();
    private ImageButton backButton;

    // Firestore / Auth
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive_translation);

        // 🔗 View
        archiveRecyclerView = findViewById(R.id.archive_recycler_view);
        backButton = findViewById(R.id.btnBack);

        // 🔙 뒤로가기
        if (backButton != null) backButton.setOnClickListener(v -> finish());

        // 🔧 RecyclerView
        archiveAdapter = new ArchiveAdapter(archiveList);
        archiveRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        archiveRecyclerView.setAdapter(archiveAdapter);

        // ✅ 상단 우측(아바타) 로그아웃 연결
        wireLogoutButton();

        // ✅ 하단 네비 자체 배선 (유틸 사용 안 함)
        wireBottomNavIfPresent();

        // Firebase
        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // 🗃 데이터 로드 (로컬 → 로컬 JSON → Firestore 병합)
        loadFromFeedbackPrefs();
        loadFromLocalJson();
        loadFromFirestore();

        // 정렬(최근 추가순처럼 보이도록 역순)
        archiveList.sort(Comparator.comparingInt(archiveList::indexOf).reversed());
        archiveAdapter.notifyDataSetChanged();
    }

    /** 상단 우측 버튼(avatar_image)에 로그아웃 연결 */
    private void wireLogoutButton() {
        ImageView logoutBtn = findViewById(R.id.avatar_image);
        if (logoutBtn == null) return;

        logoutBtn.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle("로그아웃")
                        .setMessage("정말 로그아웃하시겠어요?")
                        .setPositiveButton("로그아웃", (d, w) ->
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
                                })
                        )
                        .setNegativeButton("취소", null)
                        .show()
        );
    }

    /** 하단 네비게이션: 화면마다 레이아웃이 조금씩 달 수 있어 두 가지 방식 모두 지원 */
    private void wireBottomNavIfPresent() {
        // 1) MaterialButton id가 있는 버전
        MaterialButton navStudy = findViewById(R.id.nav_study);
        MaterialButton navArchive = findViewById(R.id.nav_archive);
        MaterialButton navTranslate = findViewById(R.id.nav_translate);
        if (navStudy != null && navArchive != null && navTranslate != null) {
            navStudy.setOnClickListener(v ->
                    startActivity(new Intent(this, StudyStartActivity.class)));
            navArchive.setOnClickListener(v ->
                    startActivity(new Intent(this, ArchiveTranslationActivity.class)));
            navTranslate.setOnClickListener(v ->
                    startActivity(new Intent(this, TranslateActivity.class)));
            return;
        }

        // 2) LinearLayout 3분할(아이콘+텍스트) 버전
        LinearLayout bar = findViewById(R.id.navigation_bar);
        if (bar != null && bar.getChildCount() >= 3) {
            LinearLayout tab1 = (LinearLayout) bar.getChildAt(0); // 학습
            LinearLayout tab2 = (LinearLayout) bar.getChildAt(1); // 메인
            LinearLayout tab3 = (LinearLayout) bar.getChildAt(2); // 번역
            tab1.setOnClickListener(v ->
                    startActivity(new Intent(this, StudyStartActivity.class)));
            tab2.setOnClickListener(v ->
                    startActivity(new Intent(this, MainActivity.class)));
            tab3.setOnClickListener(v ->
                    startActivity(new Intent(this, TranslateActivity.class)));
        }
    }

    /** 기존 SharedPreferences("feedback_records"): value = "sentence||feedback" */
    private void loadFromFeedbackPrefs() {
        try {
            SharedPreferences prefs = getSharedPreferences("feedback_records", MODE_PRIVATE);
            Map<String, ?> all = prefs.getAll();
            for (Map.Entry<String, ?> e : all.entrySet()) {
                String value = String.valueOf(e.getValue());
                if (TextUtils.isEmpty(value)) continue;
                String[] parts = value.split("\\|\\|");
                if (parts.length >= 2) {
                    String sentence = parts[0];
                    String feedback = parts[1];
                    archiveList.add(new ArchiveItem(sentence, feedback));
                }
            }
        } catch (Exception ignore) {}
    }

    /** (옵션) 로컬 JSON 저장분: SharedPreferences("archive_local").getString("items","[]") */
    private void loadFromLocalJson() {
        try {
            SharedPreferences sp = getSharedPreferences("archive_local", MODE_PRIVATE);
            String json = sp.getString("items", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String src = o.optString("src");
                String dst = o.optString("dst");
                if (!TextUtils.isEmpty(src) || !TextUtils.isEmpty(dst)) {
                    archiveList.add(new ArchiveItem(src, dst));
                }
            }
        } catch (Exception ignore) {}
    }

    /** (옵션) Firestore 저장분 병합: users/{uid}/translations */
    private void loadFromFirestore() {
        try {
            if (auth.getCurrentUser() == null) return;
            String uid = auth.getCurrentUser().getUid();
            db.collection("users").document(uid)
                    .collection("translations")
                    .get()
                    .addOnSuccessListener(qs -> {
                        List<ArchiveItem> adds = new ArrayList<>();
                        for (QueryDocumentSnapshot d : qs) {
                            String sentence = d.getString("src");
                            String feedback = d.getString("dst");
                            if (sentence == null) sentence = "";
                            if (feedback == null) feedback = "";
                            if (sentence.isEmpty() && feedback.isEmpty()) continue;
                            adds.add(new ArchiveItem(sentence, feedback));
                        }
                        if (!adds.isEmpty()) {
                            // 앞쪽에 붙여 최신처럼 보이게
                            archiveList.addAll(0, adds);
                            archiveAdapter.notifyItemRangeInserted(0, adds.size());
                        }
                    })
                    .addOnFailureListener(e -> {
                        // 실패해도 로컬 데이터는 이미 표시됨
                    });
        } catch (Exception ignore) {}
    }
}
