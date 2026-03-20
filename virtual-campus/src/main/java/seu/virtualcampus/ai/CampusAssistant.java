package seu.virtualcampus.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface CampusAssistant {

    // {{username}} 会在每次调用时被自动替换为真实的登录用户ID
    @SystemMessage({
            "你是东南大学虚拟校园的 AI 智能私人助手。",
            "你有能力调用本地数据库工具来查询公共信息以及用户的私人信息。",
            "【极其重要】当前与你对话的用户的学号/工号(ID)是：{{username}}。",
            "当用户询问'我的'信息（如我的余额、我借的书、我的订单）时，请务必作为参数传递该用户的ID来调用对应的私有工具。",
            "请用温柔、拟人化的语气回答，直接告诉用户结果，不要暴露你调用的工具名称、SQL或JSON格式数据。"
    })
    TokenStream chat(@MemoryId Integer sessionId, @V("username") Integer username, @UserMessage String userMessage);
}