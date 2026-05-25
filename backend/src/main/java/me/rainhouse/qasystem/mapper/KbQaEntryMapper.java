package me.rainhouse.qasystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.rainhouse.qasystem.entity.KbQaEntry;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbQaEntryMapper extends BaseMapper<KbQaEntry> {
}