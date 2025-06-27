package com.atguigu.guliai.tools;

import com.atguigu.system.domain.Course;
import com.atguigu.system.domain.CourseReservation;
import com.atguigu.system.domain.School;
import com.atguigu.guliai.query.CourseQuery;
import com.atguigu.system.service.ICourseReservationService;
import com.atguigu.system.service.ICourseService;
import com.atguigu.system.service.ISchoolService;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CourseTools {

    private final ICourseService courseService;
    private final ISchoolService schoolService;
    private final ICourseReservationService courseReservationService;

    @Tool(name = "queryCourse", description = "description = \"根据条件查询课程信息。当用户询问任何课程相关问题时，必须使用此工具查询数据库！参数示例：{type: '编程', edu: 4}\"")
    public List<Course> queryCourse(
            @ToolParam(required = false, description = "课程查询条件") CourseQuery query) {
        QueryChainWrapper<Course> wrapper = courseService.query();
        wrapper
                .eq(query.getType() != null, "type", query.getType())
                .le(query.getEdu() != null, "edu", query.getEdu());
        if(query.getSorts() != null) {
            for (CourseQuery.Sort sort : query.getSorts()) {
                wrapper.orderBy(true, sort.getAsc(), sort.getField());
            }
        }
        return wrapper.list();
    }

    @Tool(name = "querySchools", description = "查询所有校区信息")
    public List<School> query_schools() {
        return schoolService.list();
    }

    @Tool(name = "generateReservation", description = "生成课程预约单")
    public String generateCourseReservation(
            @ToolParam(description = "课程名称") String courseName,
            @ToolParam(description = "学生姓名") String studentName,
            @ToolParam(description = "联系方式") String contactInfo,
            @ToolParam(description = "校区名称") String school,
            @ToolParam(description = "备注", required = false) String remark) {
        CourseReservation courseReservation = new CourseReservation();
        courseReservation.setCourse(courseName);
        courseReservation.setStudentName(studentName);
        courseReservation.setContactInfo(contactInfo);
        courseReservation.setSchool(school);
        courseReservation.setRemark(remark);
        courseReservationService.save(courseReservation);
        return String.valueOf(courseReservation.getId());
    }
}