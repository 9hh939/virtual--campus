from langchain_core.tools import tool

from .client import get


@tool
def call_java_query_balance(student_id: str) -> str:
    """
    仅当用户询问自己的余额、校园卡余额、账户余额时调用。
    如果用户只问余额，必须优先且仅调用这个工具，不要改用账户信息工具。
    必须传入 student_id (学号/用户ID)。
    """
    print(f"🌐 [RPC 调用] Python 正在向 Java 请求 {student_id} 的余额...")
    try:
        response = get("/api/ai-rpc/balance", {"userId": student_id})

        if response.status_code == 200:
            data = response.json()
            if data.get("success"):
                return f"Java 数据库返回：余额为 {data.get('balance')} 元。"
            return f"查询失败：{data.get('message')}"
        return f"Java 后端异常，状态码: {response.status_code}"
    except Exception:
        return "微服务通信失败，Java 后端可能未启动。"


@tool
def call_java_query_account_info(student_id: str) -> str:
    """
    仅当用户询问账户信息、账户状态、账户类型、开户时间时调用。
    不要用于回答单纯的余额问题，也不要用于回答最近交易记录问题。
    必须传入 student_id (学号/用户ID)。
    """
    print(f"🌐 [RPC 调用] Python 正在向 Java 请求 {student_id} 的账户信息...")
    try:
        response = get("/api/ai-rpc/account-info", {"userId": student_id})

        if response.status_code == 200:
            data = response.json()
            if data.get("success"):
                return (
                    "Java 返回的账户信息："
                    f"账户号={data.get('accountNumber')}，"
                    f"账户类型={data.get('accountType')}，"
                    f"账户状态={data.get('status')}，"
                    f"余额={data.get('balance')}元，"
                    f"开户时间={data.get('createdDate')}"
                )
            return f"查询失败：{data.get('message')}"
        return f"Java 后端异常，状态码: {response.status_code}"
    except Exception:
        return "微服务通信失败，无法连接 Java 银行服务。"


@tool
def call_java_recent_transactions(student_id: str) -> str:
    """
    仅当用户询问最近交易记录、消费记录、转账记录、流水时调用。
    不要用于回答余额问题或账户状态问题。
    必须传入 student_id (学号/用户ID)。
    """
    print(f"🌐 [RPC 调用] Python 正在向 Java 请求 {student_id} 的最近交易记录...")
    try:
        response = get("/api/ai-rpc/recent-transactions", {"userId": student_id})

        if response.status_code == 200:
            data = response.json()
            if data.get("success"):
                return f"Java 返回的最近交易记录：{data.get('transactions')}"
            return f"查询失败：{data.get('message')}"
        return f"Java 后端异常，状态码: {response.status_code}"
    except Exception:
        return "微服务通信失败，无法连接 Java 银行服务。"
