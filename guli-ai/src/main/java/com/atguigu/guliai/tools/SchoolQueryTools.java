// CourseQueryTools.java
package com.atguigu.guliai.tools;

import com.atguigu.guliai.query.CourseQuery;
import com.atguigu.system.domain.Course;
import com.atguigu.system.domain.School;
import com.atguigu.system.service.ICourseService;
import com.atguigu.system.service.ISchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SchoolQueryTools {

    private final ISchoolService schoolService;

    @Tool(description = "查询所有校区")
    public List<School> queryAllSchools() {
        return schoolService.list();
    }
}