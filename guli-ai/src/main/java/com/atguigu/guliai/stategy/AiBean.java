package com.atguigu.guliai.stategy;

import com.atguigu.guliai.constant.SystemConstant;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface AiBean {

    String value() default SystemConstant.MODEL_TYPE_OPENAI;
}
