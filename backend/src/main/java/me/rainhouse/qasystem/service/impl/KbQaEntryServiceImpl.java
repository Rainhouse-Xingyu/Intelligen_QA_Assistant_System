package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.rainhouse.qasystem.entity.KbQaEntry;
import me.rainhouse.qasystem.mapper.KbQaEntryMapper;
import me.rainhouse.qasystem.service.KbQaEntryService;
import org.springframework.stereotype.Service;

@Service
public class KbQaEntryServiceImpl extends ServiceImpl<KbQaEntryMapper, KbQaEntry> implements KbQaEntryService {
}