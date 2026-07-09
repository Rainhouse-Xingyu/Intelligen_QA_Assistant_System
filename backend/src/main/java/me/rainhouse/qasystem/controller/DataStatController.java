package me.rainhouse.qasystem.controller;

import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.entity.HotQuestionConfig;
import me.rainhouse.qasystem.service.DataStatService;
import me.rainhouse.qasystem.service.StatHotQuestionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stat")
public class DataStatController {

    @Autowired
    private StatHotQuestionService statHotQuestionService;

    @Autowired
    private DataStatService dataStatService;

    /**
     * 【4.1 核心功能】获取常见咨询问题，供前端 ECharts (如词云数据) 接入
     */
    @GetMapping("/hot-questions")
    public Result<List<Map<String, Object>>> getHotQuestions(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "20") int limit) {
        List<Map<String, Object>> hots = statHotQuestionService.getHotQuestions(days, limit);
        return Result.success(hots);
    }

    /**
     * 手动重建常见问题统计，便于管理员立即刷新报表。
     */
    @PostMapping("/hot-questions/rebuild")
    public Result<Integer> rebuildHotQuestions(
            @RequestParam(required = false) LocalDate statDate,
            @RequestParam(required = false) Integer days) {
        if (days != null) {
            return Result.success(dataStatService.refreshRecentHotQuestions(days));
        }
        return Result.success(dataStatService.rebuildHotQuestions(statDate == null ? LocalDate.now() : statDate));
    }

    /**
     * 获取兜底闭环概览：命中分布、未命中待处理量、Top 未识别问题。
     */
    @GetMapping("/fallback-overview")
    public Result<Map<String, Object>> getFallbackOverview(
            @RequestParam(defaultValue = "7") int days) {
        return Result.success(dataStatService.getFallbackOverview(days));
    }

    @GetMapping("/hot-question-configs")
    public Result<List<HotQuestionConfig>> listHotQuestionConfigs(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(statHotQuestionService.listHotQuestionConfigs());
    }

    @PostMapping("/hot-question-configs")
    public Result<HotQuestionConfig> saveHotQuestionConfig(@RequestBody HotQuestionConfig config,
                                                           HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(statHotQuestionService.saveHotQuestionConfig(config, getUserIdOpt(request)));
    }

    @DeleteMapping("/hot-question-configs/{id}")
    public Result<String> deleteHotQuestionConfig(@PathVariable Long id,
                                                  HttpServletRequest request) {
        requireAdmin(request);
        if (statHotQuestionService.deleteHotQuestionConfig(id)) {
            return Result.success("删除成功");
        }
        return Result.error("配置不存在");
    }

    private Long getUserIdOpt(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        return userIdObj == null ? 0L : Long.valueOf(userIdObj.toString());
    }

    private void requireAdmin(HttpServletRequest request) {
        Object roleObj = request.getAttribute("role");
        Integer role = roleObj == null ? null : Integer.valueOf(roleObj.toString());
        if (!Integer.valueOf(3).equals(role)) {
            throw new IllegalStateException("仅管理员可操作");
        }
    }
}
