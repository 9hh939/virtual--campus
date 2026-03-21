# 文件路径: ai-brain/core/agent.py

from langchain_openai import ChatOpenAI
from langgraph.prebuilt import create_react_agent
from langgraph.checkpoint.memory import MemorySaver
from config import settings

# 引入微服务通信工具
from tools.java_bridge import call_java_query_balance, call_java_search_products
# 引入 RAG 知识库工具
from tools.knowledge_tools import search_campus_rules

llm = ChatOpenAI(model="deepseek-chat", temperature=0.7)

# 【核心震撼点】：你的 Agent 现在拥有了调用 Java 和查阅文档的双重超能力！
tools = [
    call_java_query_balance, 
    call_java_search_products,
    search_campus_rules
]

memory = MemorySaver()

agent_executor = create_react_agent(llm, tools, checkpointer=memory)