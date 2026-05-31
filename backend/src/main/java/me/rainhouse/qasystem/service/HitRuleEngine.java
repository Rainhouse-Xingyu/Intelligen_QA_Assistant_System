package me.rainhouse.qasystem.service;

import me.rainhouse.qasystem.service.vector.HitDecision;

public interface HitRuleEngine {

    HitDecision decide(double score);
}
