package org.example.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AskRequest {
    @NotBlank(message = "userId 不能为空")
    private String userId;
    @NotBlank(message = "question 不能为空")
    private String question;
    private String source; // 文档来源过滤（可选）
}
