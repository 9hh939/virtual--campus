# 文件路径: ai-brain/tools/campus.py

from langchain_core.tools import tool

@tool
def check_campus_card_balance(student_id: str) -> str:
    """当用户询问自己的校园卡余额、饭卡还剩多少钱时，必须调用此工具！"""
    print(f"🛠️ [工具执行日志]: 正在去数据库查询学号 {student_id} 的校园卡余额...")
    
    # 这里的逻辑我们后续会换成通过 HTTP 去请求你的 Java Spring Boot 接口
    if student_id == "user_001":
        return "余额为：150.50 元"
    else:
        return "余额为：0.00 元"

# 如果以后有诸如 search_library_books 的工具，也全部写在这个文件里，或者新建一个 library.py