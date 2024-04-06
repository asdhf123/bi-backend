package com.sss.bibackend.common;

public class ResultUtils {

    public static <T> BaseResponse success(T result){
        return new BaseResponse(200,result,"成功");
    }
    public static BaseResponse error(ErrorCode errorCode){
        return new BaseResponse(errorCode);
    }
    public static BaseResponse error(int code,String message){
        return new BaseResponse(code,null,message);
    }
    public static BaseResponse error(ErrorCode errorCode,String message){
        return new BaseResponse(errorCode.getCode(),null,message);
    }
}
