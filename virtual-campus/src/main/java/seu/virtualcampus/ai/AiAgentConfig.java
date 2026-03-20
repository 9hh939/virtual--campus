package seu.virtualcampus.ai;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiAgentConfig {

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Bean
    public CampusAssistant campusAssistant(CampusTools campusTools) {
        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(apiKey)
                .modelName("deepseek-chat")
                .build();

        return AiServices.builder(CampusAssistant.class)
                .streamingChatLanguageModel(model)
                // 【修改这里】使用 Provider 为每个 sessionId (MemoryId) 动态分配一个独立的记忆窗口
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .tools(campusTools)
                .build();
    }
}