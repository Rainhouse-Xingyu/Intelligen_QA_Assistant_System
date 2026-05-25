package me.rainhouse.qasystem.controller;

import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.service.StatHotQuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stat")
public class DataStatController {

    @Autowired
    private StatHotQuestionService statHotQuestionService;

    /**
     * 【4.1 核心功能】获取热点咨询问题，供前端 ECharts (如词云数据) 接入
     */
    @GetMapping("/hot-questions")
    public Result<List<Map<String, Object>>> getHotQuestions(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "20") int limit) {
        List<Map<String, Object>> hots = statHotQuestionService.getHotQuestions(days, limit);
        return Result.success(hots);
    }
}