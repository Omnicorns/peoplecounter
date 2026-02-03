package com.sarinah.peoplecounter.configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

        @Bean
        public FirebaseApp firebaseApp() throws IOException {
            String path = "/opt/secret/firebase-admin.json";

            try (FileInputStream serviceAccount = new FileInputStream(path)) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                if (FirebaseApp.getApps().isEmpty()) {
                    return FirebaseApp.initializeApp(options);
                }
                return FirebaseApp.getInstance();
            }
        }

        @Bean
        public FirebaseMessaging firebaseMessaging(FirebaseApp app) {
            return FirebaseMessaging.getInstance(app);
        }
    }


