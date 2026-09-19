package com.example.malro.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.kakao.sdk.user.UserApiClient;

public final class LogoutManager {
    private LogoutManager() {}

    public interface Callback {
        void onSuccess();
        void onError(String msg);
    }

    public static void logout(Activity activity, Callback cb) {
        // 1) 카카오 로그아웃 (카카오 토큰이 없더라도 호출해도 무방)
        try {
            UserApiClient.getInstance().logout(throwable -> {
                // throwable != null 이어도 진행(토큰 없음 등)
                // 2) Firebase signOut
                try { FirebaseAuth.getInstance().signOut(); } catch (Exception ignore) {}

                // 3) 로컬 기록/세션 정리 (필요한 것만)
                clearPrefs(activity, "feedback_records"); // 예: "문장||피드백"
                clearPrefs(activity, "archive_local");    // 예: local JSON 저장소
                clearPrefs(activity, "login_prefs");      // 마지막 로그인 방식 등

                if (cb != null) cb.onSuccess();
                return null;
            });
        } catch (Exception e) {
            if (cb != null) cb.onError(e.getMessage());
        }
    }

    private static void clearPrefs(Context ctx, String name) {
        try {
            SharedPreferences.Editor ed = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit();
            ed.clear().apply();
        } catch (Exception ignore) {}
    }
}
