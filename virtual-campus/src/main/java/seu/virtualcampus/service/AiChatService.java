package seu.virtualcampus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import seu.virtualcampus.domain.AiMessage;
import seu.virtualcampus.domain.AiSession;
import seu.virtualcampus.mapper.AiMessageMapper;
import seu.virtualcampus.mapper.AiSessionMapper;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * AI 聊天服务。
 * <p>
 * 提供会话管理、消息管理、与 AI 聊天模型的流式和非流式交互、会话摘要等功能。
 * </p>
 */
@Service
public class AiChatService {
    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions";
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private AiSessionMapper aiSessionMapper;
    @Autowired
    private AiMessageMapper aiMessageMapper;
    @Value("${deepseek.api.key}")
    private String deepSeekApiKey;

    /**
     * 获取指定用户的所有会话。
     *
     * @param username 用户名（用户唯一标识）
     * @return 该用户的所有会话列表
     */
    public List<AiSession> getSessionsByUsername(Integer username) {
        return aiSessionMapper.getSessionsByUsername(username);
    }

    /**
     * 获取指定会话的所有消息。
     *
     * @param sessionId 会话ID
     * @return 该会话下的所有消息列表
     */
    public List<AiMessage> getMessagesBySessionId(Integer sessionId) {
        return aiMessageMapper.getMessagesBySessionId(sessionId);
    }

    /**
     * 创建新会话。
     *
     * @param username 用户名（用户唯一标识）
     * @return 新建会话的ID
     */
    public Integer createSession(Integer username) {
        AiSession session = new AiSession();
        session.setUsername(username);
        String now = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setTitle("新会话");
        aiSessionMapper.insertSession(session);
        return session.getSessionId();
    }

    /**
     * 新增一条消息。
     *
     * @param sessionId 会话ID
     * @param role      消息角色（如 user、assistant）
     * @param content   消息内容
     */
    public void addMessage(Integer sessionId, String role, String content) {
        AiMessage message = new AiMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        aiMessageMapper.insertMessage(message);
    }

    /**
     * 更新会话信息，并将用户和AI的消息写入数据库。
     *
     * @param sessionId 会话ID
     * @param aiMsg     AI回复内容
     * @param userMsg   用户输入内容
     * @throws RuntimeException 如果会话不存在
     */
    public void updateSession(Integer sessionId, String aiMsg, String userMsg) {
        AiSession session = aiSessionMapper.getSessionById(sessionId);
        if (session == null) throw new RuntimeException("会话不存在");
        session.setUpdatedAt(java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        addMessage(sessionId, "user", userMsg);
        addMessage(sessionId, "assistant", aiMsg);
        List<AiMessage> history = aiMessageMapper.getMessagesBySessionId(sessionId);
        List<String> recentContents = history.stream()
                .skip(Math.max(0, history.size() - 4))
                .map(AiMessage::getContent)
                .toList();
        aiSessionMapper.updateSession(session);
        session.setTitle(summarizeChat(recentContents));
        aiSessionMapper.updateSession(session);
    }

    /**
     * 删除指定会话及其所有消息。
     *
     * @param sessionId 会话ID
     * @return 受影响的行数（通常为1）
     */
    public int deleteSession(Integer sessionId) {
        aiMessageMapper.deleteMessagesBySessionId(sessionId);
        return aiSessionMapper.deleteSession(sessionId);
    }

    /**
     * 删除指定消息。
     *
     * @param msgId 消息ID
     * @return 受影响的行数（通常为1）
     */
    public int deleteMessage(Integer msgId) {
        return aiMessageMapper.deleteMessage(msgId);
    }

    /**
     * 处理用户聊天请求并以流式方式返回AI响应。
     * <p>
     * 会自动将用户消息和AI回复写入数据库，并在AI回复完成后异步更新会话。
     * </p>
     *
     * @param sessionId 会话ID
     * @param userMsg   用户输入内容
     * @param callback  每次AI回复新内容时的回调（chunk为本次增量内容）
     * @throws RuntimeException 如果会话不存在
     */
    @Autowired
    private seu.virtualcampus.ai.CampusAssistant campusAssistant;

    /**
     * 重构后的流式处理：通过 LangChain4j AiServices 驱动，支持 Function Calling
     */
    public void handleChatStream(Integer sessionId, String userMsg, Consumer<String> callback) {
        AiSession session = aiSessionMapper.getSessionById(sessionId);
        if (session == null) throw new RuntimeException("会话不存在");

        StringBuilder aiResponseBuilder = new StringBuilder();

        // session.getUsername() 获取的就是当前登录用户的真实ID
        campusAssistant.chat(sessionId, session.getUsername(), userMsg)
                .onNext(chunk -> {
                    aiResponseBuilder.append(chunk);
                    callback.accept(chunk);
                })
                .onComplete(response -> {
                    CompletableFuture.runAsync(() -> {
                        try {
                            // 将最终回答写入数据库
                            updateSession(sessionId, aiResponseBuilder.toString(), userMsg);
                        } catch (Exception e) {
                            System.err.println("异步更新会话失败: " + e.getMessage());
                        }
                    });
                })
                .onError(error -> {
                    callback.accept("\n[AI 思考时遇到一点小问题：" + error.getMessage() + "]");
                })
                .start();
    }

    /**
     * 以流式方式调用 DeepSeek API 获取AI回复。
     *
     * @param roles    消息角色列表（如 user、assistant）
     * @param contents 消息内容列表
     * @return AI回复内容的流，每个元素为增量内容
     * @throws IllegalArgumentException 如果参数不合法
     */
    public Flux<String> chatStream(List<String> roles, List<String> contents) {
        if (roles == null || contents == null || roles.size() != contents.size())
            throw new IllegalArgumentException("roles 和 contents 必须非空且长度一致");

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是虚拟校园系统的AI助手，回答时简洁明了，不要使用复杂Markdown语法回答。");
        messages.add(systemMsg);
        for (int i = 0; i < roles.size(); i++) {
            Map<String, String> msg = new HashMap<>();
            msg.put("role", roles.get(i));
            msg.put("content", contents.get(i));
            messages.add(msg);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", "deepseek-chat");
        body.put("messages", messages);
        body.put("stream", true);

        return WebClient.builder()
                .baseUrl(DEEPSEEK_API_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + deepSeekApiKey)
                .build()
                .post()
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .takeUntil(line -> line.contains("[DONE]"))
                .map(line -> {
                    try {
                        JsonNode node = objectMapper.readTree(line);
                        JsonNode delta = node.path("choices").get(0).path("delta").path("content");
                        return delta.isMissingNode() ? "" : delta.asText();
                    } catch (Exception e) {
                        Logger.getLogger(this.getClass().getName()).severe("解析流式响应失败: " + e.getMessage());
                    }
                    return "";
                });
    }

    /**
     * 阻塞式获取AI回复（非流式）。
     *
     * @param roles    消息角色列表
     * @param contents 消消息内容列表
     * @return AI完整回复内容
     */
    public String chat(List<String> roles, List<String> contents) {
        // 使用内部流式接口
        StringBuilder fullResponse = new StringBuilder();

        // 阻塞订阅流式输出
        chatStream(roles, contents)
                .filter(chunk -> !chunk.isEmpty()) // 忽略空字符串
                .doOnNext(fullResponse::append)    // 累加每个chunk
                .blockLast();                      // 阻塞直到流结束

        return fullResponse.toString();
    }

    /**
     * 对聊天内容进行总结，生成会话标题。
     *
     * @param messages 聊天内容列表
     * @return 总结后的标题（不超过五个字）
     */
    public String summarizeChat(List<String> messages) {
        String prompt = "请总结以下内容，提炼标题。直接输出结果，不要超过五个字，不要拒绝回答：\n" + String.join("\n", messages);
        List<String> roles = List.of("user");
        List<String> contents = List.of(prompt);
        return chat(roles, contents);
    }
}
