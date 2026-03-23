package cn.zt.middleware;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * @author 墨城有珠
 * @version 1.0
 * @description: TODO
 * @date 2026/3/20 15:50
 */
public class AiCodeReview {

    private static String systemPrompt = """
            # Role
            你是一位拥有 10 年以上经验的资深后端架构师，精通各类后端技术栈与设计模式。你对待代码严谨苛刻，对潜在的性能瓶颈、安全漏洞和系统稳定性问题有着极高的敏锐度。
            
            # Task
            你的任务是对我提供的代码片段（或 Git Diff）进行专业的 Code Review，并提供具有可操作性的修改建议。
            
            # Focus Areas (重点关注领域)
            请严格按照以下维度对代码进行审查：
            1. **可靠性与 Bug**：是否存在空指针异常、越界、死循环、并发线程安全问题？
            2. **安全性**：是否存在 SQL 注入、XSS、越权访问、敏感信息硬编码等安全隐患？
            3. **性能**：是否存在慢查询隐患、不必要的循环嵌套、内存泄漏风险、锁粒度过大等问题？
            4. **可维护性**：代码是否符合 SOLID 原则？命名是否清晰规范？方法是否过于臃肿？
            5. **异常处理**：异常是否被生吞？是否抛出了合适的业务异常？
            
            # Constraints (约束条件)
            - **拒绝废话**：不要说“这段代码写得不错”之类的客套话，直接指出问题。
            - **精准定位**：指出问题时，必须明确说明是哪一行或哪个方法。
            - **提供解法**：发现问题后，必须提供重构后的代码示例，而不是只讲理论。
            - **如果没有发现问题**：请直接回复“代码逻辑清晰，未发现明显缺陷，建议通过 (LGTM)”。
            
            # Output Format (输出格式)
            请使用以下 Markdown 格式输出你的审查结果：
            
            ### 🚨 严重/致命问题 (如果没有请写“无”)
            - [具体问题描述]
            - **修改建议**：...
            
            ### ⚠️ 潜在隐患与优化点
            - [具体问题描述]
            - **优化建议**：...
            
            ### 💡 重构代码示例
            ```[语言]
            // 在这里给出你修改后的代码
            """;

    public static void main(String[] args) throws IOException {


        ProcessBuilder processBuilder = new ProcessBuilder("git", "diff", "HEAD~1", "HEAD");
        //设置工作目录，以当前目录为工作目录。
        processBuilder.directory(new File("."));

        Process process = processBuilder.start();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        StringBuilder diffCode = new StringBuilder();
        while ((line = bufferedReader.readLine()) != null) {
            diffCode.append(line);
        }

        int exitCode = process.exitValue();
        System.out.println("Exited with code:" + exitCode);
        System.out.println("评审代码：" + diffCode.toString());
        System.out.println(codeReview(diffCode.toString()));
    }

    private static String codeReview(String diffCode){
        ApiKey apiKey = new SimpleApiKey("sk-e7ae89159bbd4fd585e9f60eda5e9d41");
        OpenAiApi openAiApi = new OpenAiApi(
                "https://dashscope.aliyuncs.com/compatible-mode",
                apiKey, // 再次提醒：跑通后记得去阿里云控制台重置这个 Key 哦！
                new HttpHeaders(), // 安全的空请求头
                "/v1/chat/completions", // 关键修复：大模型聊天补全接口路径
                "/v1/embeddings",       // 关键修复：向量化接口路径
                RestClient.builder(),
                WebClient.builder(), // WebClient (不使用响应式编程则传 null)
                new DefaultResponseErrorHandler()
        );
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("qwen3-max")
                .temperature(0.7)
                .build();
        org.springframework.ai.openai.OpenAiChatModel openAiChatModel = new org.springframework.ai.openai.OpenAiChatModel(
                openAiApi,
                options,
                ToolCallingManager.builder().build(), // ToolCallingManager，纯文本评审暂不提供工具回调
                RetryTemplate.defaultInstance(), // 默认重试策略
                ObservationRegistry.NOOP // 禁用探针监控
        );

        SystemMessage systemMessage = new SystemMessage(systemPrompt);
        UserMessage userMessage = new UserMessage(diffCode);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
        ChatResponse response = openAiChatModel.call(prompt);
        return response.getResult().getOutput().getText();
    }
}
