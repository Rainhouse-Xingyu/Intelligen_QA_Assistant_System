package me.rainhouse.qasystem.service;

import me.rainhouse.qasystem.common.dto.CampusSsoUserDTO;

public interface CampusSsoService {
    
    /**
     * 验证一网通获取的Token，解析为校园用户实体
     * @param ssoToken 回调带来的Token/Ticket
     * @return 解析后的校园用户信息
     */
    CampusSsoUserDTO verifyToken(String ssoToken);
}
