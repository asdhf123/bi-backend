package com.sss.bibackend.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.sss.bibackend.mapper")
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
//        第三步：创建拦截器（这只是一个壳子）
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
//        第四步：添加内部拦截器  (分页的）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
//        可以添加多个内部拦截器
        return interceptor;
    }
}
