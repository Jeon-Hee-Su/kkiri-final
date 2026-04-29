package com.kkiri.service;

import java.util.Map;

public interface ollamaService {
    String getChatResponse(String message);
     
    void ensureServerRunning();
}

