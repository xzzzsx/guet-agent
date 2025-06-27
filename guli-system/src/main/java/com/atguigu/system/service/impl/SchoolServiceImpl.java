package com.atguigu.system.service.impl;

import com.atguigu.system.domain.School;
import com.atguigu.system.mapper.SchoolMapper;
import com.atguigu.system.service.ISchoolService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 校区表 服务实现类
 */
@Service
public class SchoolServiceImpl extends ServiceImpl<SchoolMapper, School> implements ISchoolService {

}