package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.service.HitRuleEngine;
import me.rainhouse.qasystem.service.vector.HitDecision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HitRuleEngineImpl implements HitRuleEngine {

    private final double strongHitThreshold;
    private final double weakHitThreshold;

    public HitRuleEngineImpl(@Value("${vector.search.strong-hit-threshold:0.85}") double strongHitThreshold,
                             @Value("${vector.search.weak-hit-threshold:0.70}") double weakHitThreshold) {
        this.strongHitThreshold = strongHitThreshold;
        this.weakHitThreshold = weakHitThreshold;
    }

    @Override
    public HitDecision decide(double score) {
        if (score >= strongHitThreshold) {
            return new HitDecision(2, "强命中");
        }
        if (score > weakHitThreshold) {
            return new HitDecision(1, "弱命中");
        }
        return new HitDecision(0, "未命中");
    }
}
