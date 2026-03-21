# 文件路径: ai-brain/tools/knowledge_tools.py

from langchain_core.tools import tool
from core.rag_engine import campus_kb

@tool
def search_campus_rules(query: str) -> str:
    """
    当用户询问虚拟校园的系统规则、模块功能（如商品系统怎么运作、订单如何发货、学籍如何管理）时，必须调用此工具！
    参数 query 必须是用户问题的核心关键词。
    """
    print(f"📚 [RAG 检索] AI 正在翻阅本地 Markdown 文档库寻找关于 '{query}' 的答案...")
    return campus_kb.search_knowledge(query)