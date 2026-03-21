# 文件路径: ai-brain/core/rag_engine.py

import os
from langchain_community.document_loaders import DirectoryLoader, TextLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_community.vectorstores import Chroma

class CampusKnowledgeBase:
    """
    企业级 RAG (检索增强生成) 引擎。
    负责将 Markdown 文档转化为向量，并提供语义检索能力。
    """
    def __init__(self):
        print("🗄️ [RAG 引擎] 正在启动并加载本地嵌入模型 (首次运行会下载模型，请耐心等待)...")
        # 使用 HuggingFace 的开源免费模型，完全在本地 CPU/GPU 运行，不消耗 API Token！
        self.embeddings = HuggingFaceEmbeddings(model_name="shibing624/text2vec-base-chinese")
        self.persist_directory = "./chroma_db"
        self.vector_store = None
        self._init_vector_store()

    def _init_vector_store(self):
        """初始化向量数据库"""
        if os.path.exists(self.persist_directory):
            print("🗄️ [RAG 引擎] 发现已有的 Chroma 向量库，直接加载...")
            self.vector_store = Chroma(persist_directory=self.persist_directory, embedding_function=self.embeddings)
        else:
            print("🗄️ [RAG 引擎] 未发现向量库，正在从 Markdown 文件构建全新知识库...")
            self.build_knowledge_base()

    def build_knowledge_base(self):
        """从你的项目目录读取所有 md 文件并切块入库"""
        # 指向你 Java 项目中的 md 文档目录 (假设 ai-brain 和 virtual-campus 在同一级)
        doc_path = "./docs"
        
        if not os.path.exists(doc_path):
            print(f"⚠️ [警告] 找不到路径 {doc_path}，请确保路径正确。已跳过构建。")
            return

        print(f"📄 [RAG 引擎] 开始扫描 {doc_path} 下的 Markdown 文档...")
        loader = DirectoryLoader(doc_path, glob="**/*.md", loader_cls=TextLoader, loader_kwargs={'autodetect_encoding': True})
        documents = loader.load()

        print(f"✂️ [RAG 引擎] 共读取到 {len(documents)} 篇文档，正在进行智能切块(Chunking)...")
        # 将长篇文档切分成 500 字一块的小段落，保留 50 字的重叠防断句
        text_splitter = RecursiveCharacterTextSplitter(chunk_size=500, chunk_overlap=50)
        chunks = text_splitter.split_documents(documents)

        print(f"🧬 [RAG 引擎] 正在将 {len(chunks)} 个文本块转化为向量并写入 ChromaDB...")
        self.vector_store = Chroma.from_documents(
            documents=chunks,
            embedding=self.embeddings,
            persist_directory=self.persist_directory
        )
        print("✅ [RAG 引擎] 校园知识库构建完毕！")

    def search_knowledge(self, query: str) -> str:
        """提供给 AI 调用的核心检索方法"""
        if not self.vector_store:
            return "对不起，校园知识库尚未初始化成功。"
        
        # 检索最相关的 3 个文档块
        docs = self.vector_store.similarity_search(query, k=3)
        if not docs:
            return "知识库中没有找到相关的规定或说明。"
        
        # 组装上下文
        context = "\n\n".join([f"【参考片段】: {doc.page_content}" for doc in docs])
        return f"我从《虚拟校园系统文档》中检索到了以下权威信息：\n{context}"

# 实例化一个单例供全局使用
campus_kb = CampusKnowledgeBase()