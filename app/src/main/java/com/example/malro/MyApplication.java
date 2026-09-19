package com.example.malro;

import android.app.Application;
import android.content.SharedPreferences;

public class MyApplication extends Application {

    private static MyApplication instance;

    private boolean saveRecord = false;
    private boolean guestMode = true; // 기본값은 게스트로 설정

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // 앱 실행 시 로그인 상태 복원
        SharedPreferences prefs = getSharedPreferences("user_status", MODE_PRIVATE);
        guestMode = prefs.getBoolean("isGuest", true);
        saveRecord = prefs.getBoolean("saveRecord", false);
    }

    public static MyApplication getInstance() {
        return instance;
    }

    // 🔒 기록 저장 여부
    public boolean shouldSaveRecord() {
        return saveRecord;
    }

    public void setSaveRecord(boolean saveRecord) {
        this.saveRecord = saveRecord;
    }

    // 🙍‍♂️ 게스트 모드 여부
    public boolean isGuestMode() {
        return guestMode;
    }

    public void setGuestMode(boolean guestMode) {
        this.guestMode = guestMode;
    }
}
