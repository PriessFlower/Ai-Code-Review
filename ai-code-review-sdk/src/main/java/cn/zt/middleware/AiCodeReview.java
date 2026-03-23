package cn.zt.middleware;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author 墨城有珠
 * @version 1.0
 * @description: TODO
 * @date 2026/3/20 15:50
 */
public class AiCodeReview {
    public static void main(String[] args) throws IOException {
        System.out.println("Hello World");

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
    }
}
