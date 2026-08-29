# 图匣 Pixcase

> 让每一张图像都被找到 —— 轻量、本地、强大的 Android 相册管理工具

[![Release](https://img.shields.io/github/v/release/Septno/pixcase)](https://github.com/Septno/pixcase/releases)
[![API 26+](https://img.shields.io/badge/minSdk-26-brightgreen)](https://developer.android.com/about/versions)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![CI](https://img.shields.io/github/actions/workflow/status/Septno/pixcase/ci.yml)](https://github.com/Septno/pixcase/actions)

[English](README.en.md) · 简体中文

## 截图

> 阶段 0 尚未实机截图,占位待补。

| 时间线 | 查看器 | 快速整理 |
|---|---|---|
| _占位_ | _占位_ | _占位_ |

## 特性

- 📸 **浏览 / 全屏查看 / 手势缩放** —— 基于 Jetpack Compose,60fps 流畅滚动
- 🗂️ **自定义相册 + 智能相册** —— 截图/自拍/视频/收藏,自动归类
- ⚡ **Tinder 式快速整理** —— 一键分流大批照片,大幅提升整理效率
- 🔍 **印象标记 + 大类分类** —— 给表情包 / 重要图片打标签,搜索直达
- 📤 **系统分享面板 + 批量导出** —— 多选一键分享或导出 ZIP
- 🔒 **完全本地** —— 无网络权限、无追踪、无数据上报,原图始终留在系统相册

## 下载

最低 Android 版本:**8.0 (API 26)**,编译目标 SDK 35 (Android 15)。

前往 [GitHub Releases](https://github.com/Septno/pixcase/releases) 下载最新 APK。
本项目主分发渠道为 GitHub Releases,**不计划上架 Google Play**(降低合规与维护成本)。

## 开发

### 环境要求

- JDK 17 (Adoptium Temurin 推荐)
- Android Studio Ladybug (2024.2.1) 或更新
- Android SDK Platform 35 + Build-Tools 35.0.0
- Gradle 8.10+(首次 Sync 时由 Android Studio 自动下载)

### 克隆与构建

```bash
git clone https://github.com/Septno/pixcase.git
cd pixcase
./gradlew assembleDebug
```

产物:`app/build/outputs/apk/debug/app-debug.apk`

### 调试安装

```bash
./gradlew installDebug
```

### 运行检查

```bash
./gradlew check            # ktlint + detekt + lint + test
./gradlew ktlintCheck      # 仅 ktlint
./gradlew detekt           # 仅 detekt
```

## 架构与文档

- 完整开发计划:[`相册管理工具开发计划.md`](相册管理工具开发计划.md)
- 数据模型与目录结构见计划 § 四 / § 五
- 阶段路线见计划 § 六

## 隐私承诺

本 App **不申请 `INTERNET` 权限** —— 从根源杜绝任何数据上传。
原图始终留在系统相册,App 仅维护元数据 + 索引 + 标注;
卸载 App 不会丢失用户照片。

## 贡献

欢迎 PR!详见 [`CONTRIBUTING.md`](CONTRIBUTING.md)。
提 PR 前请先跑通 `./gradlew check`。

## 讨论

- [GitHub Issues](https://github.com/Septno/pixcase/issues) —— Bug / Feature Request
- [GitHub Discussions](https://github.com/Septno/pixcase/discussions) —— 一般讨论

## 协议

[Apache License 2.0](LICENSE)