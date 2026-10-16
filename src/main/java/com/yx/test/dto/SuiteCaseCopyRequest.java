package com.yx.test.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 套件内用例副本更新请求
 */
@Data
public class SuiteCaseCopyRequest {

    /** 套件内副本步骤（JSON数组，自然语言步骤无action字段，Midscene格式含action字段） */
    private List<Map<String, String>> steps;

    /** 套件内副本预期结果（NULL表示继承父用例） */
    private String expectedResult;
}
