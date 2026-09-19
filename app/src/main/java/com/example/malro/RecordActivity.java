package com.example.malro;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * UI 없이 녹음 시작/정지를 도와주는 헬퍼형 Activity 클래스.
 * (Manifest에 등록되지 않아도 됨. 정 필요시 빈 Activity로 사용 가능)
 */
public class RecordActivity extends AppCompatActivity {

    public interface RecordCallback {
        void onStarted(@Nullable String wavPath);
        void onStopped(@Nullable String wavPath);
        void onError(String msg);
    }

    /**
     * 녹음을 시작하고, 호출자가 stop 할 수 있도록 AudioRecorderHelper를 반환.
     * 호출 전 RECORD_AUDIO 권한이 허용되어 있어야 합니다.
     */
    @Nullable
    public static AudioRecorderHelper record(Context ctx, @Nullable RecordCallback cb) {
        AudioRecorderHelper helper = new AudioRecorderHelper(ctx);
        String fileName = "record_" + System.currentTimeMillis();
        try {
            String path = helper.startRecording(fileName); // ★ 파일명 필수
            if (cb != null) cb.onStarted(path);
            return helper;
        } catch (SecurityException se) {
            if (cb != null) cb.onError("마이크 권한이 필요합니다.");
        } catch (Exception e) {
            if (cb != null) cb.onError("녹음 시작 실패: " + e.getMessage());
        }
        return null;
    }

    /** 녹음 중지 후 최종 WAV 경로를 콜백으로 전달 */
    public static void stop(@Nullable AudioRecorderHelper helper, @Nullable RecordCallback cb) {
        if (helper == null) return;
        helper.stopRecording();
        if (cb != null) cb.onStopped(helper.getOutputFilePath());
    }
}
