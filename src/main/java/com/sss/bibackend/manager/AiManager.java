package com.sss.bibackend.manager;

import com.sss.bibackend.common.ErrorCode;
import com.sss.bibackend.exception.BusinessException;
import io.github.briqt.spark4j.SparkClient;
import io.github.briqt.spark4j.constant.SparkApiVersion;
import io.github.briqt.spark4j.exception.SparkException;
import io.github.briqt.spark4j.model.SparkMessage;
import io.github.briqt.spark4j.model.SparkSyncChatResponse;
import io.github.briqt.spark4j.model.request.SparkRequest;
import io.github.briqt.spark4j.model.response.SparkTextUsage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.security.spec.RSAOtherPrimeInfo;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AiManager {

    private SparkRequest sparkRequest;

    private SparkClient sparkClient = new SparkClient();

// 设置认证信息

    public String doChat(String message){
        List<SparkMessage> messages = new ArrayList<>();
        messages.add(SparkMessage.systemContent("你是一个数据分析师，接下来我会给你我的分析目标和原始数据，告诉我分析结论。"));
        messages.add(SparkMessage.userContent(message));
        SparkRequest sparkRequest = SparkRequest.builder()
                .messages(messages)
                .maxTokens(2048)
                .temperature(0.2)
                .apiVersion(SparkApiVersion.V3_5)
                .build();
        try {
            // 同步调用
            sparkClient.appid="de66bd27";
            sparkClient.apiKey="58c64aada676a75b8be59643565fb39f";
            sparkClient.apiSecret="NThlOGZhN2RmMjk0YzMzMzVjMjA4MGE0";
            SparkSyncChatResponse chatResponse = sparkClient.chatSync(sparkRequest);
            SparkTextUsage textUsage = chatResponse.getTextUsage();
            System.out.println(chatResponse.getContent());
            return chatResponse.getContent();

        } catch (SparkException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"AI出错"+e);
        }
    }
}
