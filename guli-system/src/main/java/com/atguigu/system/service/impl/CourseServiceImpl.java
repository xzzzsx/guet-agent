package com.atguigu.system.service.impl;

import com.atguigu.system.domain.Course;
import com.atguigu.system.mapper.CourseMapper;
import com.atguigu.system.service.ICourseService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 学科表 服务实现类
 */
@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements ICourseService {

}