package cn.zt.middleware.service;

import cn.zt.middleware.config.GitHubProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitLogService {

    private final GitHubProperties gitHubProperties;

    public String writeLog(String log, String projectName, String commitId) throws GitAPIException, IOException {
        Path tempDir = Files.createTempDirectory("ai-review-repo-");
        Git git = null;
        try {
            git = Git.cloneRepository()
                    .setURI(gitHubProperties.getLogRepoUrl())
                    .setDirectory(tempDir.toFile())
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitHubProperties.getToken(), ""))
                    .call();

            String dateFolderName = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            File folder = new File(tempDir.toFile(), dateFolderName);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            String safeProjectName = projectName.replace("/", "-");
            String shortCommitId = commitId.length() > 8 ? commitId.substring(0, 8) : commitId;
            String fileName = safeProjectName + "_" + shortCommitId + ".md";
            File newFile = new File(folder, fileName);

            try (FileWriter writer = new FileWriter(newFile)) {
                writer.write("# " + projectName + " 代码评审日志\n");
                writer.write("> 关联 Commit: `" + commitId + "`\n\n");
                writer.write(log);
            }

            String gitPath = dateFolderName + "/" + fileName;
            git.add().addFilepattern(gitPath).call();

            String commitMsg = String.format("docs: add AI review for %s (commit: %s)", projectName, shortCommitId);
            git.commit().setMessage(commitMsg).call();

            git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitHubProperties.getToken(), "")).call();
            GitLogService.log.info("Changes have been pushed to the repository.");

            // Derive blob URL from the repo URL
            String repoBaseUrl = gitHubProperties.getLogRepoUrl()
                    .replaceFirst("\\.git$", "");
            return repoBaseUrl + "/blob/main/" + gitPath;
        } finally {
            if (git != null) {
                git.close();
            }
            deleteDirectory(tempDir);
            GitLogService.log.info("临时仓库目录已清理干净。");
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!dir.toFile().exists()) {
            return;
        }
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
