# 贡献指南

感谢您愿意为图匣 Pixcase 贡献代码!本文档说明参与方式与规范。

## 行为准则

参与本项目即表示您同意遵守 [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md)(Contributor Covenant v2.1)。

## 提 PR 之前必须做的事

1. **跑通 `./gradlew check`** —— ktlint / detekt / lint / test 必须全绿
2. **commit message 使用中文**(项目约定),标题 ≤ 70 字符
3. **单一 PR 不超过 400 行 diff**(鼓励小步提交,易于 review)
4. **UI 改动附截图或录屏** —— 视觉改动无法仅靠代码 review
5. **新增依赖需在 PR 描述说明用途和 License 兼容性**

## Commit 规范

```
<中文标题,≤70 字符,祈使句>

<正文,可选,补充动机与影响范围>
```

参考示例:

```
时间线页支持加载骨架屏

首屏渲染前显示 8 个占位卡片,降低用户感知等待。
图片到位后 fadeIn 替换。
```

## 报告 Bug

请使用 [Bug Report 模板](.github/ISSUE_TEMPLATE/bug_report.yml)。
报告前先搜索现有 Issue,避免重复。

## 提出功能请求

请使用 [Feature Request 模板](.github/ISSUE_TEMPLATE/feature_request.yml)。
本项目为个人开源工具,功能取舍优先服务作者自身使用需求,期待您的反馈但不一定采纳。

## 代码风格

- Kotlin 官方代码风格(Android Studio 默认)
- ktlint 配置以 Gradle 插件为准,CI 强制
- 重要设计决策请在 PR 描述里说明 "为什么" 而非 "做了什么"

## 分支命名

- 主干分支:`main`
- 特性分支:`feature/<short-name>`(例:`feature/swipe-sort`)
- Bug 修复:`fix/<short-name>`(例:`fix/timeline-crash`)

## 隐私

**严禁**在 PR 中引入任何网络权限申请(`INTERNET` 等),
或任何会主动上传用户数据的代码。本项目的核心承诺是"完全本地"。