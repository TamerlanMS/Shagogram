package com.example.shagogram.chat;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.shagogram.R;
import com.example.shagogram.adapter.RecentChatRecyclerAdapter;
import com.example.shagogram.model.ChatroomModel;
import com.example.shagogram.utils.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.Query;

public class ChatFragment extends Fragment {

    private RecyclerView recyclerView;
    private RecentChatRecyclerAdapter adapter;

    public ChatFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);
        initViews(view);
        setupRecyclerView();
        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyler_view);
    }

    private void setupRecyclerView() {
        String currentUserId = FirebaseUtil.currentUserId();
        if (currentUserId == null) {
            Toast.makeText(getContext(), "Ошибка: не удалось определить пользователя", Toast.LENGTH_SHORT).show();
            return;
        }

        Query query = FirebaseUtil.allChatroomCollectionReference()
                .whereArrayContains("userIds", currentUserId)
                .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING);

        FirestoreRecyclerOptions<ChatroomModel> options = new FirestoreRecyclerOptions.Builder<ChatroomModel>()
                .setQuery(query, ChatroomModel.class)
                .build();

        adapter = new RecentChatRecyclerAdapter(options, getContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter != null) {
            adapter.startListening();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (adapter != null) {
            adapter.stopListening();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Очищаем ссылки на объекты, чтобы избежать утечек памяти
        recyclerView.setAdapter(null);
        adapter = null;
    }
}
