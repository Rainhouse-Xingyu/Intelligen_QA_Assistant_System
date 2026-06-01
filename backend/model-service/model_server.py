from __future__ import annotations

import os
from functools import lru_cache
from typing import Any

import numpy as np
import torch
import torch.nn.functional as F
from fastapi import FastAPI
from pydantic import BaseModel, Field
from transformers import AutoModel, AutoModelForCausalLM, AutoModelForSequenceClassification, AutoTokenizer

try:
    from transformers import AutoModelForImageTextToText
except Exception:  # pragma: no cover - depends on transformers version
    AutoModelForImageTextToText = None


MODEL_BASE = os.getenv("AIGE_MODEL_BASE", "/opt/aige-models")
DEVICE = os.getenv("AIGE_MODEL_DEVICE", "cuda" if torch.cuda.is_available() else "cpu")


def model_path(name: str) -> str:
    return os.path.join(MODEL_BASE, name)


app = FastAPI(title="AIGC Local Model Service", version="1.0.0")


class EmbedRequest(BaseModel):
    texts: list[str]


class EmbedResponse(BaseModel):
    embeddings: list[list[float]]


class RerankRequest(BaseModel):
    query: str
    documents: list[str]


class RerankResponse(BaseModel):
    scores: list[float]


class RewriteRequest(BaseModel):
    query: str


class RewriteResponse(BaseModel):
    rewrite: str


class ClassifyRequest(BaseModel):
    query: str
    candidates: list[str] = Field(default_factory=list)


class ClassifyResponse(BaseModel):
    moduleType: str | None
    scores: dict[str, float]


class Reference(BaseModel):
    knowledgeId: int | None = None
    question: str | None = None
    answer: str | None = None
    score: float | None = None


class GenerateRequest(BaseModel):
    originalQuestion: str
    rewriteQuestion: str
    references: list[Reference] = Field(default_factory=list)


class GenerateResponse(BaseModel):
    answer: str


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "device": DEVICE,
        "modelBase": MODEL_BASE,
    }


@lru_cache(maxsize=1)
def embedding_bundle():
    path = model_path("bge-small-zh-v1.5")
    tokenizer = AutoTokenizer.from_pretrained(path, local_files_only=True, trust_remote_code=True)
    model = AutoModel.from_pretrained(path, local_files_only=True, trust_remote_code=True).to(DEVICE)
    model.eval()
    return tokenizer, model


@lru_cache(maxsize=1)
def reranker_bundle():
    path = model_path("bge-reranker-large")
    tokenizer = AutoTokenizer.from_pretrained(path, local_files_only=True, trust_remote_code=True)
    model = AutoModelForSequenceClassification.from_pretrained(path, local_files_only=True, trust_remote_code=True).to(DEVICE)
    model.eval()
    return tokenizer, model


@lru_cache(maxsize=1)
def rewrite_bundle():
    path = model_path("Qwen3-0.6B")
    tokenizer = AutoTokenizer.from_pretrained(path, local_files_only=True, trust_remote_code=True)
    dtype = torch.float16 if DEVICE == "cuda" else torch.float32
    model = AutoModelForCausalLM.from_pretrained(path, local_files_only=True, trust_remote_code=True, torch_dtype=dtype).to(DEVICE)
    model.eval()
    return tokenizer, model


@lru_cache(maxsize=1)
def generator_bundle():
    path = model_path("Qwen3.5-4B")
    tokenizer = AutoTokenizer.from_pretrained(path, local_files_only=True, trust_remote_code=True)
    dtype = torch.float16 if DEVICE == "cuda" else torch.float32
    if AutoModelForImageTextToText is not None:
        try:
            model = AutoModelForImageTextToText.from_pretrained(
                path,
                local_files_only=True,
                trust_remote_code=True,
                torch_dtype=dtype,
            ).to(DEVICE)
        except Exception:
            model = AutoModelForCausalLM.from_pretrained(
                path,
                local_files_only=True,
                trust_remote_code=True,
                torch_dtype=dtype,
            ).to(DEVICE)
    else:
        model = AutoModelForCausalLM.from_pretrained(path, local_files_only=True, trust_remote_code=True, torch_dtype=dtype).to(DEVICE)
    model.eval()
    return tokenizer, model


@lru_cache(maxsize=1)
def macbert_bundle():
    path = model_path("chinese-macbert-base")
    tokenizer = AutoTokenizer.from_pretrained(path, local_files_only=True, trust_remote_code=True)
    model = AutoModel.from_pretrained(path, local_files_only=True, trust_remote_code=True).to(DEVICE)
    model.eval()
    return tokenizer, model


def mean_pooling(outputs, attention_mask: torch.Tensor) -> torch.Tensor:
    token_embeddings = outputs.last_hidden_state
    mask = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
    summed = torch.sum(token_embeddings * mask, dim=1)
    counts = torch.clamp(mask.sum(dim=1), min=1e-9)
    return summed / counts


def encode_with_bert(tokenizer, model, texts: list[str], max_length: int = 512) -> torch.Tensor:
    encoded = tokenizer(texts, padding=True, truncation=True, max_length=max_length, return_tensors="pt").to(DEVICE)
    with torch.inference_mode():
        outputs = model(**encoded)
        embeddings = mean_pooling(outputs, encoded["attention_mask"])
        embeddings = F.normalize(embeddings, p=2, dim=1)
    return embeddings.cpu()


def chat_generate(tokenizer, model, messages: list[dict[str, str]], max_new_tokens: int) -> str:
    if hasattr(tokenizer, "apply_chat_template"):
        text = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    else:
        text = "\n".join(f"{m['role']}: {m['content']}" for m in messages) + "\nassistant:"
    inputs = tokenizer([text], return_tensors="pt").to(DEVICE)
    input_length = inputs["input_ids"].shape[-1]
    with torch.inference_mode():
        output_ids = model.generate(
            **inputs,
            max_new_tokens=max_new_tokens,
            do_sample=False,
            temperature=None,
            top_p=None,
            eos_token_id=tokenizer.eos_token_id,
            pad_token_id=tokenizer.eos_token_id,
        )
    generated = output_ids[0][input_length:]
    return tokenizer.decode(generated, skip_special_tokens=True).strip()


@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest) -> EmbedResponse:
    tokenizer, model = embedding_bundle()
    embeddings = encode_with_bert(tokenizer, model, request.texts)
    return EmbedResponse(embeddings=embeddings.numpy().astype(float).tolist())


@app.post("/rerank", response_model=RerankResponse)
def rerank(request: RerankRequest) -> RerankResponse:
    if not request.documents:
        return RerankResponse(scores=[])
    tokenizer, model = reranker_bundle()
    pairs = [(request.query, document) for document in request.documents]
    encoded = tokenizer(pairs, padding=True, truncation=True, max_length=512, return_tensors="pt").to(DEVICE)
    with torch.inference_mode():
        logits = model(**encoded).logits.squeeze(-1)
        scores = torch.sigmoid(logits).detach().cpu().numpy().astype(float).tolist()
    if isinstance(scores, float):
        scores = [scores]
    return RerankResponse(scores=scores)


@app.post("/rewrite", response_model=RewriteResponse)
def rewrite(request: RewriteRequest) -> RewriteResponse:
    tokenizer, model = rewrite_bundle()
    messages = [
        {"role": "system", "content": "你是高校教务智能问答的问题改写助手。只输出一个规范、清晰、适合知识库检索的问题，不要解释。"},
        {"role": "user", "content": f"原始问题：{request.query}"},
    ]
    text = chat_generate(tokenizer, model, messages, max_new_tokens=96)
    text = text.splitlines()[0].strip(" ：:")
    return RewriteResponse(rewrite=text or request.query)


@app.post("/classify", response_model=ClassifyResponse)
def classify(request: ClassifyRequest) -> ClassifyResponse:
    candidates = request.candidates or ["考务通知", "教学运行", "学业帮扶", "心理辅导"]
    descriptions = {
        "考务通知": "考试安排、考场、准考证、成绩、补考、缓考、四六级等考务问题",
        "教学运行": "选课、课表、调课、重修、课程、教室、学分、培养方案等教学运行问题",
        "学业帮扶": "挂科、绩点、学业预警、学习困难、复习资源、帮扶措施等学业支持问题",
        "心理辅导": "焦虑、压力、失眠、情绪低落、心理咨询、心理疏导等心理健康问题",
    }
    tokenizer, model = macbert_bundle()
    texts = [request.query] + [descriptions.get(candidate, candidate) for candidate in candidates]
    vectors = encode_with_bert(tokenizer, model, texts, max_length=256).numpy()
    query_vector = vectors[0]
    scores: dict[str, float] = {}
    best_module = None
    best_score = -1.0
    for candidate, vector in zip(candidates, vectors[1:]):
        score = float(np.dot(query_vector, vector))
        scores[candidate] = score
        if score > best_score:
            best_score = score
            best_module = candidate
    return ClassifyResponse(moduleType=best_module, scores=scores)


@app.post("/generate", response_model=GenerateResponse)
def generate(request: GenerateRequest) -> GenerateResponse:
    tokenizer, model = generator_bundle()
    context_lines = []
    for index, reference in enumerate(request.references[:3], start=1):
        context_lines.append(
            f"[{index}] 知识库ID：{reference.knowledgeId}\n"
            f"问题：{reference.question or ''}\n"
            f"答案：{reference.answer or ''}\n"
            f"匹配分：{reference.score if reference.score is not None else ''}"
        )
    context = "\n\n".join(context_lines)
    messages = [
        {
            "role": "system",
            "content": "你是高校智能问答助手。必须严格基于给定知识库内容回答；如果依据不足，说明暂未找到可靠依据，并建议联系人工老师。",
        },
        {
            "role": "user",
            "content": (
                f"用户原始问题：{request.originalQuestion}\n"
                f"规范化问题：{request.rewriteQuestion}\n\n"
                f"知识库依据：\n{context}\n\n"
                "请给出简洁、准确、面向学生的回答。"
            ),
        },
    ]
    answer = chat_generate(tokenizer, model, messages, max_new_tokens=384)
    return GenerateResponse(answer=answer)
