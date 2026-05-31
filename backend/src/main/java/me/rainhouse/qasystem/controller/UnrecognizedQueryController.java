package me.rainhouse.qasystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.entity.UnrecognizedQuery;
import me.rainhouse.qasystem.service.UnrecognizedQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/unrecognized")
public class UnrecognizedQueryController {

    @Autowired
    private UnrecognizedQueryService unrecognizedQueryService;

    /**
     * 【4.3 模块】面向管理员的未识别问题列表分页
     */
    @GetMapping("/list")
    public Result<Page<UnrecognizedQuery>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status) {
            
        QueryWrapper<UnrecognizedQuery> qw = new QueryWrapper<>();
        if (status != null) {
            qw.eq("status", status);
        }
        qw.orderByDesc("create_time");
        
        Page<UnrecognizedQuery> page = unrecognizedQueryService.page(new Page<>(current, size), qw);
        return Result.success(page);
    }

    /**
     * 【4.3 模块】面向管理员：处理/更新问题状态
     */
    @PostMapping("/update-status")
    public Result<String> updateStatus(
            @RequestParam("id") Long id,
            @RequestParam("status") Integer status,
            HttpServletRequest request) {
        unrecognizedQueryService.updateState(id, status, getUserIdOpt(request));
        return Result.success("状态更新成功");
    }

    private Long getUserIdOpt(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj != null) {
            return Long.valueOf(userIdObj.toString());
        }
        return null;
    }
}
