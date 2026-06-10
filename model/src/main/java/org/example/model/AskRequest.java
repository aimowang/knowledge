package org.example.model;

import lombok.Data;

@Data
public class AskRequest {
    private String userId;
    private String question;
    private String source; // 文档来源过滤（可选）
}
