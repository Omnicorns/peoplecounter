package com.sarinah.peoplecounter.controller;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.sarinah.peoplecounter.service.FcmService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/fcm")
public class BroadcastController {
    private final FcmService fcmService;

    public BroadcastController(FcmService fcmService) {
        this.fcmService = fcmService;
    }

    @PostMapping("/topic/{topic}")
    public ResponseEntity<?> sendToTopic(@PathVariable String topic, @RequestBody Req req)
            throws FirebaseMessagingException {

        String messageId = fcmService.broadcastTopic(topic, req.title, req.body, req.data);
        return ResponseEntity.ok(Map.of("ok", true, "messageId", messageId));
    }

    public static class Req {
        public String title;
        public String body;
        public Map<String, String> data; // optional
    }
}
