package com.sss.bibackend.manager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiManagerTest {
    @Resource
    AiManager aiManager;
    @Test
    void doChat() {
        String response = aiManager.doChat("你好");
        System.out.println(response);
    }
}