from langchain_openai import ChatOpenAI
from langgraph.checkpoint.memory import MemorySaver
from langgraph.prebuilt import create_react_agent

from config import settings
from tools.java_bridge import (
    call_java_add_to_cart,
    call_java_borrow_book,
    call_java_query_account_info,
    call_java_query_balance,
    call_java_recent_transactions,
    call_java_search_products,
)
from tools.knowledge_tools import search_campus_rules

AGENT_SYSTEM_PROMPT = """
你是虚拟校园系统的 AI 助手。

请严格遵循以下规则：
1. 当用户询问真实业务数据时，优先调用最贴切的单个工具，不要为了补充信息而额外调用无关工具。
2. 如果用户只问“余额”，只调用余额工具，不要额外调用账户信息工具。
3. 如果用户只问“账户状态、账户类型、开户时间、账户信息”，只调用账户信息工具。
4. 如果用户只问“最近交易、消费记录、转账记录”，只调用交易记录工具。
5. 如果用户想查询商品、价格、库存，只调用商品查询工具。
6. 如果用户明确要求你代为办理借书，只调用借书办理工具。
7. 如果用户明确要求你代为加入购物车，只调用加购物车工具。
8. 当用户询问校园规则、模块说明、业务流程时，调用知识库检索工具。
9. 如果工具已经返回结果，请基于工具结果直接回答，不要重复编造额外事实。
10. 回答尽量清晰、直接、简洁。
""".strip()


def build_llm() -> ChatOpenAI:
    return ChatOpenAI(
        model=settings.LLM_MODEL,
        temperature=settings.LLM_TEMPERATURE,
        api_key=settings.OPENAI_API_KEY,
        base_url=settings.OPENAI_API_BASE,
    )


def get_tools():
    return [
        call_java_query_balance,
        call_java_query_account_info,
        call_java_recent_transactions,
        call_java_borrow_book,
        call_java_add_to_cart,
        call_java_search_products,
        search_campus_rules,
    ]


def build_agent_executor():
    llm = build_llm()
    tools = get_tools()
    memory = MemorySaver()
    return create_react_agent(
        llm,
        tools,
        checkpointer=memory,
        prompt=AGENT_SYSTEM_PROMPT,
    )


agent_executor = build_agent_executor()
