package me.rainhouse.qasystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.rainhouse.qasystem.entity.BizContact;

public interface BizContactService extends IService<BizContact> {

    /**
     * 根据用户的提问意图或关键字，自动追加相关负责老师的联系方式
     * @param query 用户的提问文本
     * @param aiAnswer 大模型给出的基础回复
     * @return 拼装了联系方式后的最终回复（若没命中则原样返回）
     */
    String appendContactInfoIfMatch(String query, String aiAnswer);
}