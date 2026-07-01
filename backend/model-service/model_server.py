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
import json
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


class ChunkRequest(BaseModel):
    """文档切片请求：将原始文档抽取为 FAQ 知识片段"""
    text: str
    title: str | None = None
    maxItems: int = 24


class ChunkItem(BaseModel):
    """单个 FAQ 知识片段"""
    question: str
    answer: str


class ChunkResponse(BaseModel):
    """文档切片响应"""
    items: list[ChunkItem]


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


def extract_json_array(text: str) -> list[dict[str, Any]]:
    """从模型输出中提取 JSON 数组，避免解释性文本影响调用方。"""
    cleaned = strip_thinking(text or "").strip()
    start = cleaned.find("[")
    end = cleaned.rfind("]")
    if start < 0 or end <= start:
        return []
    try:
        data = json.loads(cleaned[start:end + 1])
    except Exception as exc:
        print(f"[chunk] JSON 解析失败: {exc}; output={cleaned[:300]}")
        return []
    if not isinstance(data, list):
        return []
    return [item for item in data if isinstance(item, dict)]


def release_chunk_model(force: bool = False) -> None:
    """Release the FAQ chunking model only when explicitly requested or during OOM recovery."""
    unload_enabled = os.getenv("AIGE_UNLOAD_CHUNK_MODEL", "false").strip().lower() in {"1", "true", "yes", "y"}
    if not force and not unload_enabled:
        return
    rewrite_bundle.cache_clear()
    if DEVICE.startswith("cuda"):
        torch.cuda.empty_cache()


# ================================================================
#  API 端点
# ================================================================

@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest) -> EmbedResponse:
    """文本向量化 —— 将一批文本转为稠密向量，用于语义检索"""
    print(f"[API] /embed 收到 {len(request.texts)} 条文本，正在用 bge-base-zh-v1.5 推理...")
    try:
        tokenizer, model = embedding_bundle()
    except torch.OutOfMemoryError:
        release_chunk_model(force=True)
        embedding_bundle.cache_clear()
        if DEVICE.startswith("cuda"):
            torch.cuda.empty_cache()
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
        {
            "role": "system",
            "content": (
                "你是高校教务智能问答的问题改写助手。只输出一个适合知识库检索的标准问题，不要解释。"
                "必须补全用户省略的检索意图，例如时间、条件、流程、材料、入口、注意事项。"
                "禁止只是照抄原句、只加问号、只改标点。"
            ),
        },
        {
            "role": "user",
            "content": (
                "请把原始问题改写成一个完整、明确、可检索的政策问题。\n"
                "示例：\n"
                "原始问题：补考\n"
                "改写：补考的报名条件、时间安排和考试流程是什么？\n"
                "原始问题：选课怎么弄\n"
                "改写：选课的开放时间、操作流程和退补选规则是什么？\n"
                f"原始问题：{request.query}\n"
                "改写："
            ),
        },
    ]
    text = chat_generate(tokenizer, model, messages, max_new_tokens=96)
    text = text.splitlines()[0].strip(" ：:")
    if text and not text.endswith(("？", "?")):
        text = f"{text}？"
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


@app.post("/chunk", response_model=ChunkResponse)
def chunk_document(request: ChunkRequest) -> ChunkResponse:
    """文档智能切片 —— 将政策/指南文档抽取成可检索 FAQ 片段"""
    text = (request.text or "").strip()
    if not text:
        return ChunkResponse(items=[])

    max_items = max(1, min(request.maxItems, 40))
    # 控制单次上下文，超长文档由 Java 侧分批调用或规则兜底处理。
    text = text[:12000]
    print(f"[API] /chunk 收到文档「{request.title or ''}」，长度 {len(text)}，最多 {max_items} 条")

    try:
        tokenizer, model = rewrite_bundle()
    except Exception as exc:
        release_chunk_model(force=True)
        print(f"[API] /chunk 加载轻量切片模型失败，返回空结果交给 Java 规则兜底: {exc}")
        return ChunkResponse(items=[])
    messages = [
        {
            "role": "system",
            "content": (
                "你是高校教务知识库的文档切片专家。你的任务是把原始文档切成可用于 RAG 检索的 FAQ 知识片段。\n"
                "必须遵守：\n"
                "1. 只输出 JSON 数组，不要 Markdown，不要解释，不要代码块。\n"
                "2. 数组元素格式固定为 {\"question\":\"...\",\"answer\":\"...\"}。\n"
                "3. 只能使用本次输入片段中的内容配对 question 和 answer，禁止跨标题、跨事项、跨段落借用其他内容。\n"
                "4. question 的核心名词必须能在 answer 中找到对应依据；如果找不到明确答案，就不要输出该问题。\n"
                "5. 一个片段只回答一个独立业务问题；不同业务主题、不同办理事项、不同对象条件必须拆开。\n"
                "6. 同一事项下的条件、材料、步骤、时间、入口、注意事项要保留在同一个 answer 中，不要机械拆成碎句。\n"
                "7. question 必须是学生/老师会真实检索的完整问句，且要包含具体主题名词，例如“补考报名时间是什么？”；不能写“第一部分”“相关规定”“该内容是什么”。\n"
                "8. answer 必须忠实摘取原文事实，不得编造；保留时间、地点、对象、条件、材料、流程、联系方式、例外情况。\n"
                "9. 每个 answer 建议 80-700 字；太短的相邻同主题内容要合并，太长且含多个事项时要拆分。\n"
                "10. 表格内容按每行或每类事项整理成自然语言 FAQ；标题要与正文合并理解。\n"
                "11. 去掉页眉页脚、目录、空泛介绍、重复标题和无意义编号。\n"
                "12. 如果当前片段只是过渡文字、目录或没有可回答的政策事实，输出 []。\n"
                "反例：不要把“补考报名时间”的问题配到“缓考申请材料”的答案上；不要把上一节标题配到下一节正文上。\n"
            ),
        },
        {
            "role": "user",
            "content": (
                f"文档标题：{request.title or '未命名文档'}\n"
                f"最多输出 {max_items} 条高质量 FAQ。\n\n"
                "请只基于下面这一个片段做精准切片。先确认每个 question 都能被对应 answer 直接回答，再输出 JSON 数组：\n"
                "[{\"question\":\"...\",\"answer\":\"...\"}]\n\n"
                "当前片段：\n"
                f"{text}"
            ),
        },
    ]
    try:
        raw = chat_generate(tokenizer, model, messages, max_new_tokens=2048)
    except Exception as exc:
        print(f"[API] /chunk 推理失败，返回空结果交给 Java 规则兜底: {exc}")
        return ChunkResponse(items=[])
    finally:
        release_chunk_model()
    parsed_items = extract_json_array(raw)
    items: list[ChunkItem] = []
    for item in parsed_items[:max_items]:
        question = str(item.get("question", "")).strip()
        answer = str(item.get("answer", "")).strip()
        if question and answer and len(question) <= 500 and len(answer) >= 20:
            items.append(ChunkItem(question=question, answer=answer))
    print(f"[API] /chunk 完成，输出 {len(items)} 条")
    return ChunkResponse(items=items)


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
