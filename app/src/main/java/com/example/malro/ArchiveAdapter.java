
package com.example.malro;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/** 기존 카드 UI를 그대로 유지하되, 데이터만 안정적으로 바인딩 */
public class ArchiveAdapter extends RecyclerView.Adapter<ArchiveAdapter.ViewHolder> {

    private final ArrayList<ArchiveItem> archiveList;

    public ArchiveAdapter(@NonNull ArrayList<ArchiveItem> archiveList) {
        this.archiveList = archiveList;
        setHasStableIds(false);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView sentenceNumber;
        public TextView sentenceHeader;
        public TextView sentenceSubhead;

        public ViewHolder(View view) {
            super(view);
            sentenceNumber = view.findViewById(R.id.sentence_number);
            sentenceHeader = view.findViewById(R.id.sentence_header);
            sentenceSubhead = view.findViewById(R.id.sentence_subhead);
        }
    }

    @NonNull
    @Override
    public ArchiveAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.activity_list_item_archive, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ArchiveItem item = archiveList.get(position);

        // 번호
        holder.sentenceNumber.setText(String.valueOf(position + 1));

        // 헤더: Sentence N (기존 표기 유지)
        holder.sentenceHeader.setText("Sentence " + (position + 1));

        // 서브헤더: "문장 - 피드백" (기존 표기 유지)
        String sentence = item.getSentence() != null ? item.getSentence() : "";
        String feedback = item.getFeedback() != null ? item.getFeedback() : "";
        if (feedback.isEmpty()) {
            holder.sentenceSubhead.setText(sentence);
        } else if (sentence.isEmpty()) {
            holder.sentenceSubhead.setText(feedback);
        } else {
            holder.sentenceSubhead.setText(sentence + " - " + feedback);
        }
    }

    @Override
    public int getItemCount() {
        return archiveList.size();
    }
}
