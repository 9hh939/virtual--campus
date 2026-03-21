import os
from dotenv import load_dotenv

# 加载 .env 文件中的环境变量到系统环境中
load_dotenv()

class Settings:
    # 读取配置，如果 .env 里没有，就去读电脑系统里的环境变量
    OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
    OPENAI_API_BASE = os.getenv("OPENAI_API_BASE", "https://api.deepseek.com")
    
    # 以后还可以加数据库配置：
    # DB_HOST = os.getenv("DB_HOST", "localhost")

# 实例化一个单例供全局使用
settings = Settings()