package cn.zt.middleware;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.Test;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * @author 墨城有珠
 * @version 1.0
 * @description: TODO
 * @date 2026/3/20 16:33
 */
@Slf4j
@SpringBootTest
public class ApiTest {

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
    private static String APP_ID = "wxf0c05633e2eacdb8";
    private static String SECRET = "06c217a6ae0142cce4e590562f6b0217";
    private static String GET_ACCESSTOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";
    private static String TEMPLATE_ID = "1Cka7v0P3ALTZWFtx_ttBfnY_6EGEscDZopPxMAQm-Y";
    private static String SEND_TEMPLATEMSG_URL = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=%s";

    public static void main(String[] args) throws IOException, InterruptedException, GitAPIException {

        // 1. 执行 Git 命令获取 Diff
        ProcessBuilder processBuilder = new ProcessBuilder("git", "diff", "HEAD~1", "HEAD");
        processBuilder.directory(new File("."));
        Process process = processBuilder.start();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        StringBuilder diffCode = new StringBuilder();
        while ((line = bufferedReader.readLine()) != null) {
            diffCode.append(line).append("\n"); // 建议加上换行符，保证 diff 格式不乱
        }

        int exitCode = process.waitFor();
        System.out.println("Git diff process exited with code: " + exitCode);

        if (exitCode != 0 || diffCode.length() == 0) {
            System.out.println("没有检测到代码变更或执行出错，退出评审。");
            return;
        }

        // 2. 调用 AI 接口进行代码评审
        System.out.println("正在请求 AI 进行代码评审...");
        String reviewLog = codeReview(diffCode.toString());
        System.out.println("AI 评审完成！");

        String projectName = System.getenv("GITHUB_REPOSITORY");
        if (projectName == null) projectName = "ai-code-review-sdk"; // 兜底项目名


        String response = getAccessTokenAndSendTemplateMessage(projectName, "测试log");
        System.out.println(response);
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
        OpenAiChatModel openAiChatModel = new OpenAiChatModel(
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

    public static String writeLog(String log,String projectName,String commitId,String token ) throws GitAPIException, IOException {
        File repoDir = new File("repo");
        deleteDirectory(repoDir);

        Git git = null;
        try {
            git = Git.cloneRepository()
                    .setURI("https://github.com/PriessFlower/Ai-Code-Review-Log.git")
                    .setDirectory(new File("repo"))
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                    .call();

            String dateFolderName = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            File folder = new File("repo/" + dateFolderName);
            if(!folder.exists()){
                folder.mkdirs();
            }

            // 修复文件目录 Bug 将 projectName 中的 "/" 替换为 "-"，防止被识别为子目录
            String safeProjectName = projectName.replace("/", "-");

            //只留下 commitId 前8位
            String shortCommitId = commitId.length() > 8 ? commitId.substring(0, 8) : commitId;
            // commitId + projectName做文件名
            String fileName = safeProjectName + "_" + shortCommitId + ".md";
            File newFile = new File(folder, fileName);

            //在文件前几行输入一些元信息
            try (FileWriter writer = new FileWriter(newFile)) {
                writer.write("# " + projectName + " 代码评审日志\n");
                writer.write("> 关联 Commit: `" + commitId + "`\n\n");
                writer.write(log);
            }

            String gitPath = dateFolderName + "/" + fileName;
            git.add().addFilepattern(gitPath).call();

            String commitMsg = String.format("docs: add AI review for %s (commit: %s)", projectName, shortCommitId);
            git.commit().setMessage(commitMsg).call();

            git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(token,"")).call();
            System.out.println("Changes have been pushed to the repository.");
            return "https://github.com/PriessFlower/Ai-Code-Review-Log/blob/main/" + gitPath;
        } finally {
            if (git != null) {
                git.close(); // 释放 JGit 对 .git 目录下文件的锁,不然删除不了。
            }
            deleteDirectory(repoDir);
            System.out.println("临时仓库目录已清理干净。");
        }
    }


    private static void deleteDirectory(File directoryToBeDeleted) throws IOException {
        if (!directoryToBeDeleted.exists()) {
            return;
        }
        // 使用 Java 8 的 NIO 库优雅地遍历并删除所有文件和子目录
        Files.walk(directoryToBeDeleted.toPath())
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private static String getAccessTokenAndSendTemplateMessage(String projectName,String reviewUrl) throws IOException, InterruptedException {
        //get请求，需要把query参数拼接里
        String reUrl = String.format(GET_ACCESSTOKEN_URL, APP_ID, SECRET);
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(reUrl))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        String accessToken = JSON.parseObject(body).getString("access_token");

        String sendMsgUrl = String.format(SEND_TEMPLATEMSG_URL, accessToken);
        JSONObject jsonObject = new JSONObject();
        JSONObject data = new JSONObject();
        data.fluentPut("project",new AiCodeReview.TemplateItem(projectName,"#173177"))
                .fluentPut("review",new AiCodeReview.TemplateItem(reviewUrl,"#173177"));
        jsonObject.fluentPut("touser", "oh5Bu3AO8Bj6IIiBle_yqAGF-UTE")
                .fluentPut("template_id", TEMPLATE_ID)
                .fluentPut("topcolor", "#FF0000")
                .fluentPut("data", data);
        request = HttpRequest.newBuilder()
                .uri(URI.create(sendMsgUrl))
                .POST(HttpRequest.BodyPublishers.ofString(jsonObject.toJSONString()))
                .header("Content-Type", "application/json; charset=utf-8")
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("状态码: " + response.statusCode());
        System.out.println("响应结果: " + response.body());
        return response.body();
    }

    public static class TemplateItem {
        public String value;
        public String color;

        public TemplateItem(String value, String color) {
            this.value = value;
            this.color = color;
        }
    }
}
