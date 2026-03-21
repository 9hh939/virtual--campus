import requests
from langchain_core.tools import tool

JAVA_BACKEND_URL = "http://127.0.0.1:12345"

@tool
def call_java_query_balance(student_id: str) -> str:
    """
    当用户询问自己的校园卡/银行余额时调用。
    必须传入 student_id (学号/用户ID)。
    """
    print(f"🌐 [RPC 调用] Python 正在向 Java 请求 {student_id} 的余额...")
    try:
        url = f"{JAVA_BACKEND_URL}/api/ai-rpc/balance"
        response = requests.get(url, params={"userId": student_id}, timeout=5)
        
        if response.status_code == 200:
            data = response.json()
            if data.get("success"):
                return f"Java 数据库返回：余额为 {data.get('balance')} 元。"
            else:
                return f"查询失败：{data.get('message')}"
        return f"Java 后端异常，状态码: {response.status_code}"
    except requests.exceptions.RequestException as e:
        return "微服务通信失败，Java 后端可能未启动。"

@tool
def call_java_search_products(keyword: str) -> str:
    """
    当用户想在校园商店查询某件商品、价格或库存时调用。
    必须传入商品的关键词 keyword。
    """
    print(f"🌐 [RPC 调用] Python 正在向 Java 查询包含 '{keyword}' 的商品...")
    try:
        url = f"{JAVA_BACKEND_URL}/api/ai-rpc/products"
        response = requests.get(url, params={"keyword": keyword}, timeout=5)
        
        if response.status_code == 200:
            data = response.json()
            if data.get("success"):
                return f"Java 返回的商品列表：{data.get('products')}"
            else:
                return "未查询到相关商品。"
        return f"Java 后端异常，状态码: {response.status_code}"
    except Exception as e:
        return "微服务通信失败，无法连接 Java 商店服务。"