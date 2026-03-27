package cn.zt.middleware.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

@Slf4j
@Service
public class GitDiffService {

    public String getGitDiff() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("git", "diff", "HEAD~1", "HEAD");
            processBuilder.directory(new File("."));
            Process process = processBuilder.start();

            StringBuilder diffCode = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    diffCode.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            log.info("Git diff process exited with code: {}", exitCode);

            if (exitCode != 0 || diffCode.isEmpty()) {
                throw new IllegalStateException("没有检测到代码变更或 git diff 执行出错，退出评审。");
            }

            return diffCode.toString();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("执行 git diff 时发生异常: " + e.getMessage(), e);
        }
    }
}
