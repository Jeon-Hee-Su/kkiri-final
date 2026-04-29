package com.kkiri.config;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.PostConstruct;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

@Configuration
public class FcmConfig {

    @PostConstruct
    public void init() {
        try {
            // resources 폴더에 있는 fcm-key.json 파일을 읽어옵니다.
            InputStream serviceAccount = new ClassPathResource("fcm-key.json").getInputStream();

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                System.out.println("Firebase 서버 연결 성공!");
            }
        } catch (IOException e) {
            System.out.println("Firebase 설정 파일을 찾을 수 없습니다.");
            e.printStackTrace();
        }
    }
    
    @Bean
    public FirebaseMessaging firebaseMessaging() {
        return FirebaseMessaging.getInstance(FirebaseApp.getInstance());
    }
}

/*

const firebaseConfig = {
  apiKey: "AIzaSyD2c1bWwo7SfUqOrYsNLO9I9E7Jd9-q-gQ",
  authDomain: "kkiri-64757.firebaseapp.com",
  projectId: "kkiri-64757",
  storageBucket: "kkiri-64757.firebasestorage.app",
  messagingSenderId: "432933470365",
  appId: "1:432933470365:web:4574a4150ad7b6bc4b5474"
  vapid key BHb0Mo70EYLStz5dp2P81xed_x-4SLcPKrzNzutsO3lYnyvG5_BVKgxmXd8snk2RJdqUFqOi-_raHLwRUP2BeyQ
};

*/


