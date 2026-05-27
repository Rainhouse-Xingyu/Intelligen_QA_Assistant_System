package me.rainhouse.qasystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.rainhouse.qasystem.entity.StudentGrowthArchive;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生成长档案表 Mapper 接口
 */
@Mapper
public interface StudentGrowthArchiveMapper extends BaseMapper<StudentGrowthArchive> {
}
