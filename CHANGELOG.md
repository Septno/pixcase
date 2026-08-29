# 更新日志

本项目的所有重要变更都会记录在此文件。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/),
版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### 计划中

- 阶段 0:工程初始化(Gradle 骨架、CI、Hilt/Room/Compose 依赖、Room Schema 占位)
- 阶段 1:基础浏览 MVP(时间线 + 查看器 + 缩略图)
- 阶段 2:图像操作(多选 + 删除 / 收藏 / 隐藏 / 分享)
- 阶段 3:自定义相册 + 智能相册 + 标签
- 阶段 3.5:快速整理(Tinder 式卡片)
- 阶段 4:搜索
- 阶段 5:分享与导出
- 阶段 7:工具二(印象标记 + 大类分类)
- 阶段 8:高级特性(按优先级挑选)

## [0.1.0] - 计划日期未定

### Added

- 项目初始化:Gradle / Kotlin / Jetpack Compose / Hilt / Room / Paging 3 / Coil / ML Kit 骨架
- CI:GitHub Actions 跑 `ktlintCheck` + `detekt` + `lint` + `test` + `assembleDebug`
- 开源治理:LICENSE(Apache 2.0) + README + CONTRIBUTING + CODE_OF_CONDUCT + SECURITY + Issue/PR 模板 + CHANGELOG
- Room Schema 占位:12 张表(相册/标签/收藏/隐藏/搜索历史/快速整理/大类/印象)
- 应用图标占位 + Material3 主题 + Dynamic Color + 深色模式
- 中英 strings.xml

[Unreleased]: https://github.com/Septno/pixcase/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Septno/pixcase/releases/tag/v0.1.0