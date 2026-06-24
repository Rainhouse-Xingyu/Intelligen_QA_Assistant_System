package me.rainhouse.qasystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.rainhouse.qasystem.entity.KbDocument;
import org.springframework.web.multipart.MultipartFile;

public interface KbDocumentService extends IService<KbDocument> {

    /**
     * 上传知识库文档（Excel/Word）并启动异步解析
     * @param file 用户上传的文件
     * @param uploaderId 管理员ID
     * @return 上存记录
     */
    KbDocument uploadAndParse(MultipartFile file, String uploaderId);
}