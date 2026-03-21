# 文件路径: ai-brain/main.py

from fastapi import FastAPI
from fastapi.responses import RedirectResponse
from pydantic import BaseModel

# 【核心变更】直接从我们重构好的引擎层引入组装完毕的 Agent
from core.agent import agent_executor 

app = FastAPI(title="虚拟校园 AI 大脑 (企业级架构版)", version="4.0")

class ChatRequest(BaseModel):
    session_id: str = "user_001" 
    message: str

@app.get("/")
def read_root():
    return RedirectResponse(url="/docs")

@app.post("/chat")
def chat_with_ai(request: ChatRequest):
    print(f"\n👤 [用户 {request.session_id} 说]: {request.message}")
    
    config = {"configurable": {"thread_id": request.session_id}}
    
    # ------------------------------------------------------------------
    # 【核心修复】：动态上下文注入 (Prompt Wrapping)
    # 我们在发给大模型的话里，悄悄加上一句“系统旁白”。
    # 这样大模型就能在上下文中读取到学号，而前端用户根本察觉不到这个过程！
    # ------------------------------------------------------------------
    contextual_message = (
        f"【系统强制指令：当前登录用户的学号是 '{request.session_id}'。"
        f"当用户询问'我的'信息（如余额、选课等）时，请务必直接提取该学号作为参数去调用工具，绝对不要向用户反问学号！】\n\n"
        f"用户提问：{request.message}"
    )
    
    # 把拼接好的带有上下文的消息发给 Agent
    responses = agent_executor.invoke(
        {"messages": [("user", contextual_message)]}, 
        config=config
    )
    
    final_reply = responses["messages"][-1].content
    print(f"🧠 [Agent 回复]: {final_reply}")
    
    return {
        "session_id": request.session_id,
        "reply": final_reply
    }