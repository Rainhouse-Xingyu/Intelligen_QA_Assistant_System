package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.rainhouse.qasystem.entity.BizContact;
import me.rainhouse.qasystem.mapper.BizContactMapper;
import me.rainhouse.qasystem.service.BizContactService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BizContactServiceImpl extends ServiceImpl<BizContactMapper, BizContact> implements BizContactService {

    @Override
    public String appendContactInfoIfMatch(String query, String aiAnswer) {
        if (query == null || query.trim().isEmpty()) {
            return aiAnswer;
        }

        // 简单实现：全量查询配置的业务联系方式规则，循环匹配关键字
        // 生产中由于数据量极少完全可以放入 Redis 或本地应用缓存中
        List<BizContact> contacts = this.list();
        if (contacts == null || contacts.isEmpty()) {
            return aiAnswer;
        }

        StringBuilder appendStr = new StringBuilder();
        for (BizContact contact : contacts) {
            String keyword = contact.getBizModule();
            // 如果用户提问中包含如 "选课"、"重修" 等关键字
            if (keyword != null && !keyword.isEmpty() && query.contains(keyword)) {
                appendStr.append(String.format("【%s】负责老师：%s，联系电话：%s\n", 
                        keyword, 
                        contact.getTeacherName(), 
                        contact.getPhoneNumber()));
            }
        }

        if (appendStr.length() > 0) {
            return aiAnswer + "\n\n--- 业务联动提示 ---\n根据您的问题，您可能还需要以下联系方式：\n" + appendStr.toString().trim();
        }

        return aiAnswer;
    }
}