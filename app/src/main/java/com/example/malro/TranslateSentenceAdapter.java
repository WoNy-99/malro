package com.example.malro;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TranslateSentenceAdapter extends RecyclerView.Adapter<TranslateSentenceAdapter.VH> {

    public interface OnItemClickListener {
        void onClick(int position);
    }

    private final List<String> originals;
    private final List<String> translations;
    private final OnItemClickListener listener;

    public TranslateSentenceAdapter(List<String> originals, List<String> translations, OnItemClickListener l) {
        this.originals = originals;
        this.translations = translations;
        this.listener = l;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_list_item_archive, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        String origin = originals.get(position);
        String trans = translations.get(position);
        h.sentenceNumber.setText(String.valueOf(position + 1));
        h.sentenceHeader.setText("Sentence " + (position + 1));
        h.sentenceSubhead.setText(origin + " → " + trans);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(position);
        });
    }

    @Override
    public int getItemCount() {
        return Math.min(originals.size(), translations.size());
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView sentenceNumber, sentenceHeader, sentenceSubhead;
        public VH(@NonNull View itemView) {
            super(itemView);
            sentenceNumber = itemView.findViewById(R.id.sentence_number);
            sentenceHeader = itemView.findViewById(R.id.sentence_header);
            sentenceSubhead = itemView.findViewById(R.id.sentence_subhead);
        }
    }
}