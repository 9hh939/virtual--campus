package seu.virtualcampus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import seu.virtualcampus.domain.AiMessage;
import seu.virtualcampus.domain.AiSession;
import seu.virtualcampus.mapper.AiMessageMapper;
import seu.virtualcampus.mapper.AiSessionMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * AI 聊天服务 (企业微服务架构重构版)
 * <p>
 * 现已作为纯业务网关，所有大模型推理、RAG及工具调用均转发至 Python FastAPI(8080端口) 中枢大脑。
 * </p>
 */
@Service
public class AiChatService {

    // 指向你本地刚刚搭好的 Python 大脑
    private static final String PYTHON_AI_URL = "http://127.0.0.1:8080/chat";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.create();

    @Autowired
    private AiSessionMapper aiSessionMapper;
    @Autowired
    private AiMessageMapper aiMessageMapper;

    public List<AiSession> getSessionsByUsername(Integer username) {
        return aiSessionMapper.getSessionsByUsername(username);
    }

    public List<AiMessage> getMessagesBySessionId(Integer sessionId) {
        return aiMessageMapper.getMessagesBySessionId(sessionId);
    }

    public Integer createSession(Integer username) {
        AiSession session = new AiSession();
        session.setUsername(username);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setTitle("新会话");
        aiSessionMapper.insertSession(session);
        return session.getSessionId();
    }

    public void addMessage(Integer sessionId, String role, String content) {
        AiMessage message = new AiMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        aiMessageMapper.insertMessage(message);
    }

    public void updateSession(Integer sessionId, String aiMsg, String userMsg) {
        AiSession session = aiSessionMapper.getSessionById(sessionId);
        if (session == null) throw new RuntimeException("会话不存在");
        session.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        addMessage(sessionId, "user", userMsg);
        addMessage(sessionId, "assistant", aiMsg);

        // 极简标题生成策略：取用户第一句话的前 7 个字（不再浪费大模型资源去生成标题）
        if ("新会话".equals(session.getTitle())) {
            String title = userMsg.length() > 7 ? userMsg.substring(0, 7) + "..." : userMsg;
            session.setTitle(title);
        }
        aiSessionMapper.updateSession(session);
    }

    public int deleteSession(Integer sessionId) {
        aiMessageMapper.deleteMessagesBySessionId(sessionId);
        return aiSessionMapper.deleteSession(sessionId);
    }

    public int deleteMessage(Integer msgId) {
        return aiMessageMapper.deleteMessage(msgId);
    }

    /**
     * 核心通信枢纽：连接 Java 前端与 Python 大脑
     */
    public void handleChatStream(Integer sessionId, String userMsg, Consumer<String> callback) {
        AiSession session = aiSessionMapper.getSessionById(sessionId);
        if (session == null) throw new RuntimeException("会话不存在");

        // 开启异步线程处理网络请求，防止卡死 JavaFX 界面
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 组装发给 Python 的数据 (带上学号，供 Python 进行鉴权和数据库查询)
                Map<String, String> requestBody = new HashMap<>();
                requestBody.put("session_id", String.valueOf(session.getUsername()));
                requestBody.put("message", userMsg);

                // 2. 发起 HTTP POST 请求到 Python 大脑
                String responseJson = webClient.post()
                        .uri(PYTHON_AI_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(); // 阻塞等待 Python 思考完毕

                // 3. 解析 Python 返回的 JSON {"reply": "..."}
                JsonNode rootNode = objectMapper.readTree(responseJson);
                String reply = rootNode.path("reply").asText();

                // 4. 模拟“流式打字机”效果 (因为 Python 目前是一次性返回完整结果，我们切碎了发给前端以保持炫酷的动画效果)
                for (char c : reply.toCharArray()) {
                    callback.accept(String.valueOf(c));
                    Thread.sleep(15); // 每个字停顿 15 毫秒
                }

                // 5. 将完整的聊天记录持久化到 SQLite 数据库
                updateSession(sessionId, reply, userMsg);

            } catch (Exception e) {
                callback.accept("\n[⚠️ 网络连接失败：无法连接到 Python AI 大脑，请检查 8080 端口是否已启动！\n错误详情: " + e.getMessage() + "]");
            }
        });
    }
}