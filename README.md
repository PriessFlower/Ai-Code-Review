# AI Code Review SDK 集成指南

## 概述

`ai-code-review` 是一个基于 Spring AI 的自动化代码审查工具，通过 GitHub Actions 触发，对每次 Push 的代码变更执行 AI 审查，并将结果推送至指定日志仓库、通过微信模板消息通知开发者。

本文档面向希望在自己项目中接入该审查能力的开发者。

---

## 项目模块结构

```
ai-code-review/
├── ai-code-review-sdk/          # 核心逻辑库（普通 jar，可作为依赖引入）
│   └── src/main/java/cn/zt/middleware/
│       ├── config/
│       │   ├── GitHubProperties.java    # GitHub 配置绑定
│       │   └── WeChatProperties.java    # 微信配置绑定
│       ├── model/
│       │   └── TemplateItem.java        # 微信模板消息数据项
│       └── service/
│           ├── GitDiffService.java      # 执行 git diff 获取变更
│           ├── CodeReviewService.java   # 调用 Spring AI ChatClient 审查
│           ├── GitLogService.java       # JGit 写日志到 GitHub 仓库
│           └── WeChatNotifyService.java # 微信模板消息通知
│
├── ai-code-review-app/          # 可执行应用（Spring Boot fat jar）
│   └── src/main/java/cn/zt/middleware/
│       ├── AiCodeReviewApplication.java # Spring Boot 启动入口
│       └── runner/
│           └── CodeReviewRunner.java    # CommandLineRunner 主流程编排
│── .github/workflows/
   └── workflow.yml             # 本项目 CI/CD：构建 + 审查 + 发布 Release
```

---

## 数据流

```
目标项目 push 到 master
  → GitHub Actions 触发
  → 从本项目 GitHub Releases 下载 fat jar
  → java -jar ai-code-review-app-x.x.x.jar（通过环境变量注入配置）
  → GitDiffService: git diff HEAD~1 HEAD
  → CodeReviewService: 调用 AI 大模型审查 diff
  → GitLogService: JGit clone 日志仓库 → 写 Markdown → push
  → WeChatNotifyService: 推送微信模板消息（含日志链接）
```

---

## 目标项目接入步骤

### 第一步：配置 GitHub Secrets

在目标项目的 **Settings → Secrets and variables → Actions** 中添加以下 Secrets：

| Secret 名称         | 说明                                          | 是否必填 |
|---------------------|-----------------------------------------------|----------|
| `CODE_TOKEN`        | GitHub PAT，需要有目标仓库和日志仓库的读写权限 | 必填     |
| `AI_API_KEY`        | AI 大模型 API Key（默认使用阿里云 DashScope）  | 必填     |
| `WECHAT_APP_ID`     | 微信公众号 AppID                              | 必填     |
| `WECHAT_SECRET`     | 微信公众号 AppSecret                          | 必填     |
| `WECHAT_TEMPLATE_ID`| 微信消息模板 ID                               | 必填     |
| `WECHAT_TO_USER`    | 接收通知的微信用户 OpenID                     | 必填     |

可选覆盖（有默认值，无需配置）：

| Secret / 环境变量名    | 默认值                                                        |
|------------------------|---------------------------------------------------------------|
| `AI_BASE_URL`          | `https://dashscope.aliyuncs.com/compatible-mode`             |
| `AI_MODEL`             | `qwen3-max`                                                   |
| `GITHUB_LOG_REPO_URL`  | `https://github.com/PriessFlower/Ai-Code-Review-Log.git`     |

---

### 第二步：在目标项目中添加 workflow 文件

在目标项目创建 `.github/workflows/ai-code-review.yml`，内容如下：

```yaml
name: AI Code Review

on:
  push:
    branches:
      - 'master'
  pull_request:
    branches:
      - 'master'

jobs:
  ai-code-review:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 2

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Download AI Code Review SDK
        run: |
          wget -O ai-code-review.jar \
            https://github.com/PriessFlower/ai-code-review/releases/latest/download/ai-code-review-app-1.0-SNAPSHOT.jar

      - name: Run AI Code Review
        env:
          GITHUB_TOKEN: ${{ secrets.CODE_TOKEN }}
          AI_API_KEY: ${{ secrets.AI_API_KEY }}
          WECHAT_APP_ID: ${{ secrets.WECHAT_APP_ID }}
          WECHAT_SECRET: ${{ secrets.WECHAT_SECRET }}
          WECHAT_TEMPLATE_ID: ${{ secrets.WECHAT_TEMPLATE_ID }}
          WECHAT_TO_USER: ${{ secrets.WECHAT_TO_USER }}
        run: java -jar ai-code-review.jar
```

> **注意**：将 `PriessFlower/ai-code-review` 替换为本项目的实际 GitHub 路径。

---

## 本项目 CI/CD 说明（workflow.yml）

本项目自身的 workflow 完成三件事：

| 步骤                        | 触发条件           | 说明                                          |
|-----------------------------|--------------------|-----------------------------------------------|
| `Build fat jar`             | push / PR          | `mvn clean package -DskipTests` 构建可执行 jar |
| `Run AI Code Review`        | push / PR          | 对本项目自身代码执行一次审查                   |
| `Publish SDK to GitHub Releases` | 仅 push to master | 发布 fat jar 到 Releases，版本号为 `v1.0.{run_number}` |

发布后的下载地址格式：
```
https://github.com/{owner}/{repo}/releases/latest/download/ai-code-review-app-1.0-SNAPSHOT.jar
```

---

## 微信模板消息配置

模板消息需要包含两个变量，在微信公众平台创建模板时使用以下格式：

```
项目名称：{{project.DATA}}
审查报告：{{review.DATA}}
```

---
