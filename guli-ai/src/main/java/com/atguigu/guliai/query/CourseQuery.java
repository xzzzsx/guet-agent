package com.atguigu.guliai.query;

import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

@Data
public class CourseQuery {
    @ToolParam(required = false, description = "学院类型：计算机工程学院、经济与管理学院、海洋工程学院、电子信息学院、设计与创意学院")
    private String type;
    @ToolParam(required = false, description = "高考分数要求：0-380分以下(包含380分)、1-380分到405分(包含405分)、2-405分到425分(包含425分)、3-425分到455分(包含455分)、4-455分以上")
    private Integer edu;
    @ToolParam(required = false, description = "排序方式")
    private List<Sort> sorts;

    @Data
    public static class Sort {
        @ToolParam(required = false, description = "排序字段: price或duration")
        private String field;
        @ToolParam(required = false, description = "是否是升序: true/false")
        private Boolean asc;
    }
}