package cn.zt.middleware.runner;

import cn.zt.middleware.service.CodeReviewService;
import cn.zt.middleware.service.GitDiffService;
import cn.zt.middleware.service.GitLogService;
import cn.zt.middleware.service.WeChatNotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CodeReviewRunner implements CommandLineRunner {

    private final GitDiffService gitDiffService;
    private final CodeReviewService codeReviewService;
    private final GitLogService gitLogService;
    private final WeChatNotifyService weChatNotifyService;

    @Override
    public void run(String... args) throws Exception {
        String diff = gitDiffService.getGitDiff();
        String review = codeReviewService.review(diff);
        String projectName = Optional.ofNullable(System.getenv("GITHUB_REPOSITORY")).orElse("ai-code-review");
        String commitId = Optional.ofNullable(System.getenv("GITHUB_SHA")).orElse("unknown-commit");
        String logUrl = gitLogService.writeLog(review, projectName, commitId);
        weChatNotifyService.sendReviewNotification(projectName, logUrl);
        log.info("Review complete. Log URL: {}", logUrl);
    }
}
