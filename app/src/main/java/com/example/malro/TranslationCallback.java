package com.example.malro;

import java.util.List;

public interface TranslationCallback {
    void onSuccess(List<String> originals, List<String> translations);
    void onError(String error);
}