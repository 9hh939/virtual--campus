from fastapi import FastAPI
from fastapi.responses import RedirectResponse
from pydantic import BaseModel

from core.agent import agent_executor

app = FastAPI(title="虚拟校园 AI 大脑", version="4.0")


class ChatRequest(BaseModel):
    user_id: str = "user_001"
    message: str


def build_chat_messages(user_id: str, message: str):
    system_prompt = (
        f"当前登录用户的学号是 '{user_id}'。"
        f"当用户询问‘我的’信息（如余额、选课等）时，请务必直接提取该学号作为参数去调用工具，绝对不要向用户反问学号。"
    )
    return [
        ("system", system_prompt),
        ("user", message),
    ]


@app.get("/")
def read_root():
    return RedirectResponse(url="/docs")


@app.post("/chat")
def chat_with_ai(request: ChatRequest):
    print(f"\n👤 [用户 {request.user_id} 说]: {request.message}")

    config = {"configurable": {"thread_id": request.user_id}}
    messages = build_chat_messages(request.user_id, request.message)

    responses = agent_executor.invoke(
        {"messages": messages},
        config=config,
    )

    final_reply = responses["messages"][-1].content
    print(f"🧠 [Agent 回复]: {final_reply}")

    return {
        "user_id": request.user_id,
        "reply": final_reply,
    }
