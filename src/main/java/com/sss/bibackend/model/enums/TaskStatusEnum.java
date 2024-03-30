package com.sss.bibackend.model.enums;

public enum TaskStatusEnum {
    WAIT("等待中","wait"),
    RUNNING("执行中","running"),
    SUCCEED("成功","succeed"),
    FAILED("失败","failed");
    private final String text;
    private final String value;

    TaskStatusEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public String getText() {
        return text;
    }

    public String getValue() {
        return value;
    }
}
