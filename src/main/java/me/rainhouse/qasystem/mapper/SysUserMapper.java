package me.rainhouse.qasystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.rainhouse.qasystem.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
