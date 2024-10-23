package com.example.shagogram.chat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.shagogram.R;
import com.example.shagogram.adapter.ChatRecyclerAdapter;
import com.example.shagogram.model.ChatMessageModel;
import com.example.shagogram.model.ChatroomModel;
import com.example.shagogram.model.UserModel;
import com.example.shagogram.utils.AndroidUtil;
import com.example.shagogram.utils.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Query;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatActivity extends AppCompatActivity {

    private UserModel otherUser;
    private String chatroomId;
    private ChatroomModel chatroomModel;
    private ChatRecyclerAdapter adapter;

    private EditText messageInput;
    private ImageButton sendMessageBtn;
    private ImageButton backBtn;
    private TextView otherUsername;
    private RecyclerView recyclerView;
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        initializeUI();
        loadUserData();
        getOrCreateChatroomModel();
        setupChatRecyclerView();
    }

    private void initializeUI() {
        // Инициализация элементов интерфейса
        messageInput = findViewById(R.id.chat_message_input);
        sendMessageBtn = findViewById(R.id.message_send_btn);
        backBtn = findViewById(R.id.back_btn);
        otherUsername = findViewById(R.id.other_username);
        recyclerView = findViewById(R.id.chat_recycler_view);
        imageView = findViewById(R.id.profile_pic_image_view);

        // Обработчик кнопки "Назад"
        backBtn.setOnClickListener(v -> onBackPressed());

        // Обработчик кнопки "Отправить"
        sendMessageBtn.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if (!TextUtils.isEmpty(message)) {
                sendMessageToUser(message);
            }
        });
    }

    private void loadUserData() {
        // Получение данных другого пользователя из интента и установка ID чата
        otherUser = AndroidUtil.getUserModelFromIntent(getIntent());
        if (otherUser == null) {
            finish(); // Закрытие, если данные пользователя недоступны
            return;
        }

        chatroomId = FirebaseUtil.getChatroomId(FirebaseUtil.currentUserId(), otherUser.getUserId());

        // Установка имени пользователя и изображения профиля
        otherUsername.setText(otherUser.getUsername());
        loadProfilePicture();
    }

    private void loadProfilePicture() {
        FirebaseUtil.getOtherProfilePicStorageRef(otherUser.getUserId()).getDownloadUrl()
                .addOnSuccessListener(uri -> AndroidUtil.setProfilePic(this, uri, imageView));
    }


    private void setupChatRecyclerView() {
        // Настройка RecyclerView с FirestoreRecyclerOptions
        Query query = FirebaseUtil.getChatroomMessageReference(chatroomId)
                .orderBy("timestamp", Query.Direction.DESCENDING);

        FirestoreRecyclerOptions<ChatMessageModel> options = new FirestoreRecyclerOptions.Builder<ChatMessageModel>()
                .setQuery(query, ChatMessageModel.class)
                .build();

        adapter = new ChatRecyclerAdapter(options, getApplicationContext());
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setReverseLayout(true);
        recyclerView.setLayoutManager(manager);
        recyclerView.setAdapter(adapter);
        adapter.startListening();

        // Прокрутка к новому сообщению при добавлении
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                recyclerView.smoothScrollToPosition(0);
            }
        });
    }

    private void sendMessageToUser(String message) {
        // Обновление информации о чате и отправка нового сообщения
        chatroomModel.setLastMessageTimestamp(Timestamp.now());
        chatroomModel.setLastMessageSenderId(FirebaseUtil.currentUserId());
        chatroomModel.setLastMessage(message);
        FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel);

        ChatMessageModel chatMessageModel = new ChatMessageModel(message, FirebaseUtil.currentUserId(), Timestamp.now());
        FirebaseUtil.getChatroomMessageReference(chatroomId).add(chatMessageModel)
                .addOnSuccessListener(documentReference -> {
                    messageInput.setText("");
                    sendNotification(message);
                })
                .addOnFailureListener(e -> AndroidUtil.showToast(this, "Не удалось отправить сообщение"));
    }

    private void getOrCreateChatroomModel() {
        FirebaseUtil.getChatroomReference(chatroomId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                chatroomModel = task.getResult().toObject(ChatroomModel.class);
                if (chatroomModel == null) {
                    // Если чат еще не существует, создаем новый
                    chatroomModel = new ChatroomModel(
                            chatroomId,
                            Arrays.asList(FirebaseUtil.currentUserId(), otherUser.getUserId()),
                            Timestamp.now(),
                            ""
                    );
                    FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel);
                }
            } else {
                AndroidUtil.showToast(this, "Не удалось загрузить чат");
            }
        });
    }

    private void sendNotification(String message) {
        // Отправка уведомления другому пользователю
        FirebaseUtil.currentUserDetails().get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                UserModel currentUser = task.getResult().toObject(UserModel.class);
                if (currentUser != null) {
                    JSONObject jsonObject = createNotificationJson(currentUser.getUsername(), message, otherUser.getFcmToken());
                    callApi(jsonObject);
                }
            }
        });
    }

    private JSONObject createNotificationJson(String title, String message, String toToken) {
        try {
            JSONObject notificationObj = new JSONObject();
            notificationObj.put("title", title);
            notificationObj.put("body", message);

            JSONObject dataObj = new JSONObject();
            dataObj.put("userId", FirebaseUtil.currentUserId());

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("notification", notificationObj);
            jsonObject.put("data", dataObj);
            jsonObject.put("to", toToken);

            return jsonObject;
        } catch (Exception e) {
            Log.e("ChatActivity", "Ошибка при создании JSON уведомления", e);
            return null;
        }
    }

    private void callApi(JSONObject jsonObject) {
        if (jsonObject == null) return;

        OkHttpClient client = new OkHttpClient();
        String url = "https://fcm.googleapis.com/fcm/send";
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonObject.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization", "Bearer YOUR_API_KEY")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("ChatActivity", "Не удалось отправить уведомление", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("ChatActivity", "Не удалось отправить уведомление: " + response.code());
                }
            }
        });
    }
}
