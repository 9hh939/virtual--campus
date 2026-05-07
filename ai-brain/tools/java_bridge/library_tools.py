from langchain_core.tools import tool
import requests

from .client import BASE_URL


@tool
def call_java_borrow_book(student_id: str, book_title: str) -> str:
    """
    仅当用户明确要求代为借书、办理借阅时调用。
    不要用于图书查询、图书规则解释、预约规则解释。
    必须传入 student_id (学号/用户ID) 和 book_title (书名)。
    """
    print(f"🌐 [RPC 调用] Python 正在为 {student_id} 办理借书，书名={book_title} ...")
    try:
        response = requests.post(
            f"{BASE_URL}/api/ai-rpc/library/borrow-by-title",
            params={"userId": student_id, "title": book_title},
            timeout=5,
        )

        data = response.json()
        if response.status_code == 200 and data.get("success"):
            return (
                f"借书办理成功：书名《{data.get('title')}》，"
                f"副本号 {data.get('bookId')}，"
                f"结果：{data.get('message')}"
            )
        return f"借书办理失败：{data.get('message')}"
    except Exception:
        return "借书办理失败：微服务通信异常，Java 后端可能未启动。"
