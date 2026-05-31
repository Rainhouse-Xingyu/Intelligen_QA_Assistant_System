package me.rainhouse.qasystem.controller;

import jakarta.servlet.http.HttpServletRequest;
import me.rainhouse.qasystem.common.dto.VectorSearchRequest;
import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.service.VectorSearchService;
import me.rainhouse.qasystem.service.vector.VectorSearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vector")
public class VectorSearchController {

    @Autowired
    private VectorSearchService vectorSearchService;

    @PostMapping("/rebuild")
    public Result<Integer> rebuildIndex() {
        return Result.success(vectorSearchService.rebuildIndex());
    }

    @PostMapping("/search")
    public Result<VectorSearchResponse> search(@RequestBody VectorSearchRequest searchRequest,
                                               HttpServletRequest request) {
        try {
            Long userId = getUserIdOpt(request);
            VectorSearchResponse response = vectorSearchService.search(
                    searchRequest.getQuery(),
                    searchRequest.getModuleType(),
                    searchRequest.getTopK(),
                    userId,
                    searchRequest.getSessionId()
            );
            return Result.success(response);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    private Long getUserIdOpt(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj != null) {
            return Long.valueOf(userIdObj.toString());
        }
        return 0L;
    }
}
