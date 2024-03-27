package com.sss.bibackend.config;

import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.io.ObjectInputFilter;

@Configuration
@ConfigurationProperties(prefix = "spring.redis")
@Data
public class RedisConfig {
    private String database;
    private String host;
    private String port;
    private String password;

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer()
                .setDatabase(1)
                .setAddress("redis://"+host+":"+port);
        RedissonClient redisson = Redisson.create(config);
        return redisson;
    }
}
