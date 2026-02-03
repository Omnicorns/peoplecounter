package com.sarinah.peoplecounter.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FcmService {
    private final FirebaseMessaging messaging;

    public FcmService(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    public String broadcastTopic(String topic, String title, String body, Map<String, String> data)
            throws FirebaseMessagingException {

        Message message = Message.builder()
                .setTopic(topic)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data == null ? Map.of() : data)
                .build();

        return messaging.send(message); // return messageId
    }
}
