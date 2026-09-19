package com.example.malro;

public class ArchiveItem {
    private String sentence;
    private String feedback;

    public ArchiveItem(String sentence, String feedback) {
        this.sentence = sentence;
        this.feedback = feedback;
    }

    public String getSentence() {
        return sentence;
    }

    public String getFeedback() {
        return feedback;
    }
}
