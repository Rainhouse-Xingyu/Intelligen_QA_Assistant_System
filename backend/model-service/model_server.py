"""
本地模型推理服务 —— AIGC 智能问答助手的模型后端。
对外暴露 REST API，供 Java 主服务调用：
  - /embed    文本向量化 (embedding)
  - /rerank   重排序
  - /rewrite  问题改写
  - /classify 模块分类
  - /generate 根据知识库参考生成最终回答
"""
from __future__ import annotations

import os
import re
from functools import lru_cache
from typing import Any

import numpy as np
import torch
import torch.nn.functional as F
from fastapi import FastAPI
from pydantic import BaseModel, Field
from transformers import AutoModel, AutoModelForCausalLM, AutoModelForSequenceClassification, AutoTokenizer

# 图片多模态模型，transformers 版本较老时可能无法导入
try:
    from transformers import AutoModelForImageTextToText
except Exception:  # pragma: no cover - depends on transformers version
    AutoModelForImageTextToText = None


# ----- 环境配置 -----
def default_model_base() -> str:
    windows_model_base = r"D:\Application\models"
    if os.name == "nt" and os.path.isdir(windows_model_base):
        return windows_model_base
    return "/opt/aige-models"


MODEL_BASE = os.getenv("AIGE_MODEL_BASE", default_model_base())


def resolve_device() -> str:
    configured_device = os.getenv("AIGE_MODEL_DEVICE")
    if configured_device:
        return configured_device

    if torch.cuda.is_available():
        return "cuda"
    if getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
        return "mps"
    return "cpu"


DEVICE = resolve_device()
CAUSAL_LM_DTYPE = torch.float16 if DEVICE.startswith("cuda") or DEVICE == "mps" else torch.float32


def model_path(name: str) -> str:
    """根据模型名称拼接出完整的本地路径"""
    return os.path.join(MODEL_BASE, name)


app = FastAPI(title="AIGC Local Model Service", version="1.0.0")


# ----- Pydantic 请求/响应模型 -----

class EmbedRequest(BaseModel):
    """向量化请求：批量文本"""
    texts: list[str]


class EmbedResponse(BaseModel):
    """向量化响应：每条文本对应一个向量"""
    embeddings: list[list[float]]


class RerankRequest(BaseModel):
    """重排序请求：查询 + 候选文档"""
    query: str
    documents: list[str]


class RerankResponse(BaseModel):
    """重排序响应：每个文档的相关性得分"""
    scores: list[float]


class RewriteRequest(BaseModel):
    """问题改写请求"""
    query: str


class RewriteResponse(BaseModel):
    """问题改写响应：规范化后的问题"""
    rewrite: str


class ClassifyRequest(BaseModel):
    """模块分类请求：查询文本 + 候选模块类型"""
    query: str
    candidates: list[str] = Field(default_factory=list)


class ClassifyResponse(BaseModel):
    """模块分类响应：最佳模块 + 各模块得分"""
    moduleType: str | None
    scores: dict[str, float]


class Reference(BaseModel):
    """知识库参考条目，作为生成答案的依据"""
    knowledgeId: int | None = None
    question: str | None = None
    answer: str | None = None
    score: float | None = None


class GenerateRequest(BaseModel):
    """答案生成请求：原始问题 + 改写问题 + 知识库参考"""
    originalQuestion: str
    rewriteQuestion: str
    references: list[Reference] = Field(default_factory=list)


class GenerateResponse(BaseModel):
    """答案生成响应"""
    answer: str


class PsychologicalRequest(BaseModel):
    """心理指导请求"""
    studentMsg: str


class PsychologicalResponse(BaseModel):
    """心理指导响应"""
    answer: str


@app.get("/health")
def health() -> dict[str, Any]:
    """健康检查接口"""
    return {
        "status": "ok",
        "device": DEVICE,
        "modelBase": MODEL_BASE,
    }


# ================================================================
#  模型加载器（全部使用 lru_cache 确保只加载一次）
# ================================================================

# 向量化模型：bge-base-zh-v1.5，将文本转为稠密向量,768维
@lru_cache(maxsize=1)
def embedding_bundle():
    path = model_path("bge-base-zh-v1.5")
    print(f"[加载模型] bge-base-zh-v1.5 (embedding) from {path}")
    tokenizer = AutoTokenizer.from_pretrained(path, local_files_only=True, trust_remote_code=True)
    model = AutoModel.from_pretrained(path, local_files_only=True, trust_remote_code=True).to(DEVICE)
    model.eval()
    print(f"[模型就绪] bge-base-zh-v1.5 (embedding) on {DEVICE}")
    return tokenizer, model

# 重排序模型：bge-reranker-large，对检索结果按相关性重新打分排序
@lru_cache(maxsize=1)
def reranker_bundle():
    path = model_path("bge-reranker-large")
    print(f"[加载模型] bge-reranker-large (reranker) from {path}")
    tokenizer = AutoTokenizer.from_pretrained(path, local_files_only=True, trust_remote_code=True)
    model = AutoModelForSequenceClassification.from_pretrained(path, local_files_only=True, trust_remote_code=True).to(DEVICE)
    model.eval()
    print(f"[模型就绪] bge-reranker-large (reranker) on {DEVICE}")
    return tokenizer, model

# 问题改写模型：Qwen3-0.6B，将口语化问题规范化以提升检索命中率
@lru_cache(maxsize=1)
def rewrite_bundle():
    path = model_path("Qwen3-0.6B")
    print(f"[加载模型] Qwen3-0.6B (rewrite) from {path}")
    tokenizer = AutoTokenizer.from_pretrained(path, local_files_only=True, trust_remote_code=True)
    model = AutoModelForCausalLM.from_pretrained(path, local_files_only=True, trust_remote_code=True, torch_dtype=CAUSAL_LM_DTYPE).to(DEVICE)
    model.eval()
    print(f"[模型就绪] Qwen3-0.6B (rewrite) on {DEVICE}")
    return tokenizer, model

# 答案生成模型：Qwen2.5-1.5B-Instruct，基于知识库参考生成最终回答
@lru_cache(maxsize=1)
def generator_bundle():
    path = model_path("Qwen2.5-1.5B-Instruct")
    print(f"[加载模型] Qwen2.5-1.5B-Instruct (generate) from {path}")
    tokenizer = AutoTokenizer.from_pretrained(path, local_files_only=True, trust_remote_code=True)
    if AutoModelForImageTextToText is not None:
        try:
            model = AutoModelForImageTextToText.from_pretrained(
                path,
                local_files_only=True,
                trust_remote_code=True,
                torch_dtype=CAUSAL_LM_DTYPE,
            ).to(DEVICE)
        except Exception:
            model = AutoModelForCausalLM.from_pretrained(
                path,
                local_files_only=True,
                trust_remote_code=True,
                torch_dtype=CAUSAL_LM_DTYPE,
            ).to(DEVICE)
    else:
        model = AutoModelForCausalLM.from_pretrained(path, local_files_only=True, trust_remote_code=True, torch_dtype=CAUSAL_LM_DTYPE).to(DEVICE)
    model.eval()
    print(f"[模型就绪] Qwen2.5-1.5B-Instruct (generate) on {DEVICE}")
    return tokenizer, model


# 模块分类模型：chinese-macbert-base，将用户问题归类到教务子模块
@lru_cache(maxsize=1)
def macbert_bundle():
    path = model_path("chinese-macbert-base")
    print(f"[加载模型] chinese-macbert-base (classify) from {path}")
    tokenizer = AutoTokenizer.from_pretrained(path, local_files_only=True, trust_remote_code=True)
    model = AutoModel.from_pretrained(path, local_files_only=True, trust_remote_code=True).to(DEVICE)
    model.eval()
    print(f"[模型就绪] chinese-macbert-base (classify) on {DEVICE}")
    return tokenizer, model


def mean_pooling(outputs, attention_mask: torch.Tensor) -> torch.Tensor:
    """对 BERT 类模型的 last_hidden_state 做平均池化，得到整句向量"""
    token_embeddings = outputs.last_hidden_state
    mask = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
    summed = torch.sum(token_embeddings * mask, dim=1)
    counts = torch.clamp(mask.sum(dim=1), min=1e-9)
    return summed / counts


def encode_with_bert(tokenizer, model, texts: list[str], max_length: int = 512) -> torch.Tensor:
    """用 BERT 类模型将文本编码为归一化向量（CPU Tensor）"""
    encoded = tokenizer(texts, padding=True, truncation=True, max_length=max_length, return_tensors="pt").to(DEVICE)
    with torch.inference_mode():
        outputs = model(**encoded)
        embeddings = mean_pooling(outputs, encoded["attention_mask"])
        embeddings = F.normalize(embeddings, p=2, dim=1)
    return embeddings.cpu()


def strip_thinking(text: str) -> str:
    """去掉 Qwen3 可能输出的内部思考过程，只保留正式回答。"""
    result = (text or "").strip()
    result = re.sub(r"<think\b[^>]*>.*?</think>\s*", "", result, flags=re.DOTALL | re.IGNORECASE)
    result = re.sub(r"<think\b[^>]*>.*", "", result, flags=re.DOTALL | re.IGNORECASE)

    # Qwen3 thinking 模式下，chat template 可能把 <think> 放在 prompt 里。
    # 此时代码只解码新生成 token，会看到“思考正文 + </think> + 正式回答”。
    close_index = result.lower().rfind("</think>")
    if close_index >= 0:
        result = result[close_index + len("</think>"):]
    result = re.sub(r"</think>\s*", "", result, flags=re.IGNORECASE)
    return result.strip()


def chat_generate(tokenizer, model, messages: list[dict[str, str]], max_new_tokens: int) -> str:
    """用 Chat 模型根据多轮消息生成文本，返回解码后的字符串"""
    if hasattr(tokenizer, "apply_chat_template"):
        try:
            text = tokenizer.apply_chat_template(
                messages,
                tokenize=False,
                add_generation_prompt=True,
                enable_thinking=False,
            )
        except TypeError:
            text = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    else:
        # 降级：手工拼接消息格式
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
    # 截取新生成的部分（去掉输入的 prompt）
    generated = output_ids[0][input_length:]
    result = tokenizer.decode(generated, skip_special_tokens=True).strip()
    cleaned = strip_thinking(result)
    if cleaned != result:
        print(f"[chat_generate] 已过滤 thinking 内容：{len(result)} → {len(cleaned)} 字")
    return cleaned


# ================================================================
#  API 端点
# ================================================================

@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest) -> EmbedResponse:
    """文本向量化 —— 将一批文本转为稠密向量，用于语义检索"""
    print(f"[API] /embed 收到 {len(request.texts)} 条文本，正在用 bge-base-zh-v1.5 推理...")
    tokenizer, model = embedding_bundle()
    embeddings = encode_with_bert(tokenizer, model, request.texts)
    print(f"[API] /embed 完成，输出 {len(embeddings)} 个向量")
    return EmbedResponse(embeddings=embeddings.numpy().astype(float).tolist())


@app.post("/rerank", response_model=RerankResponse)
def rerank(request: RerankRequest) -> RerankResponse:
    """重排序 —— 对候选文档按与查询的相关性重新打分"""
    if not request.documents:
        return RerankResponse(scores=[])
    print(f"[API] /rerank 收到查询，候选文档 {len(request.documents)} 篇，正在用 bge-reranker-large 推理...")
    tokenizer, model = reranker_bundle()
    pairs = [(request.query, document) for document in request.documents]
    encoded = tokenizer(pairs, padding=True, truncation=True, max_length=512, return_tensors="pt").to(DEVICE)
    with torch.inference_mode():
        logits = model(**encoded).logits.squeeze(-1)
        scores = torch.sigmoid(logits).detach().cpu().numpy().astype(float).tolist()
    if isinstance(scores, float):
        scores = [scores]
    print(f"[API] /rerank 完成，返回 {len(scores)} 个分数")
    return RerankResponse(scores=scores)


@app.post("/rewrite", response_model=RewriteResponse)
def rewrite(request: RewriteRequest) -> RewriteResponse:
    """问题改写 —— 将口语化、不规范的问题转成适合知识库检索的标准问法"""
    print(f"[API] /rewrite 收到问题：「{request.query[:60]}...」，正在用 Qwen3-0.6B 改写...")
    tokenizer, model = rewrite_bundle()
    messages = [
        {"role": "system", "content": "你是高校教务智能问答的问题改写助手。只输出一个规范、清晰、适合知识库检索的问题，不要解释。"},
        {"role": "user", "content": f"原始问题：{request.query}"},
    ]
    text = chat_generate(tokenizer, model, messages, max_new_tokens=96)
    text = text.splitlines()[0].strip(" ：:")
    print(f"[API] /rewrite 完成 → 「{text or request.query}」")
    return RewriteResponse(rewrite=text or request.query)


@app.post("/classify", response_model=ClassifyResponse)
def classify(request: ClassifyRequest) -> ClassifyResponse:
    """模块分类 —— 判断用户问题属于哪个教务子模块（如考务通知、教学运行等）"""
    # 默认模块类型及对应的描述文字
    candidates = request.candidates or ["考务通知", "教学运行", "学业帮扶", "心理辅导"]
    print(f"[API] /classify 收到问题，候选模块 {candidates}，正在用 chinese-macbert-base 分类...")
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
    print(f"[API] /classify 完成 → {best_module} (score: {best_score:.4f})")
    return ClassifyResponse(moduleType=best_module, scores=scores)


@app.post("/generate", response_model=GenerateResponse)
def generate(request: GenerateRequest) -> GenerateResponse:
    """答案生成 —— 基于原始问题、改写问题和知识库参考，生成最终回答"""
    print(f"[API] /generate 收到请求「{request.originalQuestion[:50]}...」，参考 {len(request.references)} 条，正在用 Qwen2.5-1.5B-Instruct 生成...")
    tokenizer, model = generator_bundle()
    # 构建知识库参考上下文（最多取前 3 条）
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
    print(f"[API] /generate 完成，输出 {len(answer)} 字")
    return GenerateResponse(answer=answer)


@app.post("/psychological", response_model=PsychologicalResponse)
def psychological(request: PsychologicalRequest) -> PsychologicalResponse:
    """心理指导 —— 使用生成模型给出轻松、支持性的陪伴回复"""
    print(f"[API] /psychological 收到心理指导请求「{request.studentMsg[:50]}...」")
    tokenizer, model = generator_bundle()
    messages = [
        {
            "role": "system",
            "content": (
                "你是高校里的温暖心理支持助手。请用轻松、亲切、像学长学姐一样的语气回应，"
                "先共情，再给2到4条具体可执行的小建议。不要诊断疾病，不要夸大问题。"
                "如果用户表达自伤、自杀、伤害他人或极端危险，请温和但明确地建议立刻联系辅导员、"
                "学校心理咨询中心、家人朋友或当地紧急救助。"
            ),
        },
        {
            "role": "user",
            "content": (
                f"学生说：{request.studentMsg}\n\n"
                "请回复得自然一点，不要像公告，不要使用教务政策话术。"
            ),
        },
    ]
    answer = chat_generate(tokenizer, model, messages, max_new_tokens=256)
    print(f"[API] /psychological 完成，输出 {len(answer)} 字")
    return PsychologicalResponse(answer=answer)
