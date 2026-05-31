package me.rainhouse.qasystem.service;

public interface EmbeddingService {

    float[] embed(String text);

    int dimension();
}
