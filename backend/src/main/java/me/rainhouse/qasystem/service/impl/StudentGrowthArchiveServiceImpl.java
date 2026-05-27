package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.rainhouse.qasystem.entity.StudentGrowthArchive;
import me.rainhouse.qasystem.mapper.StudentGrowthArchiveMapper;
import me.rainhouse.qasystem.service.StudentGrowthArchiveService;
import org.springframework.stereotype.Service;

@Service
public class StudentGrowthArchiveServiceImpl extends ServiceImpl<StudentGrowthArchiveMapper, StudentGrowthArchive> implements StudentGrowthArchiveService {
}
