package com.atguigu.system.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("course")
public class Course implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 学科名称
     */
    private String name;

    /**
     * 学历背景要求：0-无，1-初中，2-高中、3-大专、4-本科以上
     */
    private Integer edu;

    /**
     * 类型: 编程、非编程
     */
    private String type;

    /**
     * 课程价格
     */
    private Long price;

    /**
     * 学习时长，单位: 天
     */
    private Integer duration;


}