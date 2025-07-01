// ReservationTools.java
package com.atguigu.guliai.tools;

import com.atguigu.system.domain.CourseReservation;
import com.atguigu.system.domain.School;
import com.atguigu.system.service.ICourseReservationService;
import com.atguigu.system.service.ISchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ReservationTools {

    private final ISchoolService schoolService;
    private final ICourseReservationService courseReservationService;

    @Tool(description = "查询所有校区")
    public List<School> queryAllSchools() {
        return schoolService.list();
    }

    @Tool(description = "生成课程预约单,并返回生成的预约单号")
    public String generateReservation(
            String courseName, String studentName, String contactInfo, String school, String remark) {
        CourseReservation reservation = new CourseReservation();
        reservation.setCourse(courseName);
        reservation.setStudentName(studentName);
        reservation.setContactInfo(contactInfo);
        reservation.setSchool(school);
        reservation.setRemark(remark);
        courseReservationService.save(reservation);
        return String.valueOf(reservation.getId());
    }
}