// CourseQueryTools.java
package com.atguigu.guliai.tools;

import com.atguigu.guliai.query.CourseQuery;
import com.atguigu.system.domain.Course;
import com.atguigu.system.service.ICourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CourseQueryTools {

    private final ICourseService courseService;

    @Tool(description = "根据条件查询课程")
    public List<Course> queryCourse(@ToolParam(required = false, description = "课程查询条件") CourseQuery query) {
        return courseService.query()
                .eq(query.getType() != null, "type", query.getType())
                .le(query.getEdu() != null, "edu", query.getEdu())
                .list();
    }
}