from langchain_core.tools import tool
import requests

from .client import BASE_URL, get


@tool
def call_java_search_products(keyword: str) -> str:
    """
    仅当用户想查询商品、价格、库存、有没有某件商品时调用。
    不要用于加入购物车、下单等办理动作。
    必须传入商品关键词 keyword。
    """
    print(f"🌐 [RPC 调用] Python 正在向 Java 查询包含 '{keyword}' 的商品...")
    try:
        response = get("/api/ai-rpc/products", {"keyword": keyword})

        if response.status_code == 200:
            data = response.json()
            if data.get("success"):
                return f"Java 返回的商品列表：{data.get('products')}"
            return "未查询到相关商品。"
        return f"Java 后端异常，状态码: {response.status_code}"
    except Exception:
        return "微服务通信失败，无法连接 Java 商店服务。"


@tool
def call_java_add_to_cart(student_id: str, product_keyword: str, quantity: int) -> str:
    """
    仅当用户明确要求把商品加入购物车、办理加购时调用。
    不要用于普通商品查询、价格查询、库存查询。
    必须传入 student_id (学号/用户ID)、product_keyword (商品关键词) 和 quantity (数量)。
    """
    print(f"🌐 [RPC 调用] Python 正在为 {student_id} 办理加购物车，商品={product_keyword}，数量={quantity} ...")
    try:
        response = requests.post(
            f"{BASE_URL}/api/ai-rpc/shop/add-to-cart",
            params={"userId": student_id, "keyword": product_keyword, "quantity": quantity},
            timeout=5,
        )

        data = response.json()
        if response.status_code == 200 and data.get("success"):
            return (
                f"加购物车成功：商品《{data.get('productName')}》，"
                f"商品ID {data.get('productId')}，"
                f"数量 {data.get('quantity')}，"
                f"结果：{data.get('message')}"
            )
        return f"加购物车失败：{data.get('message')}"
    except Exception:
        return "加购物车失败：微服务通信异常，Java 后端可能未启动。"
