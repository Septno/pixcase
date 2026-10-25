# 更新日志

本项目的所有重要变更都会记录在此文件。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/),
版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added

- 阶段 3.5 入口与配置流:相册详情页右上角 ⚡「快速整理」入口(空相册禁用);进入时按「是否有未完成 session」分流 —— 有则弹底部 Sheet 选「继续上次 / 从头开始」(从头只重置进度、不删不移照片),无则直接进目标相册多选配置(排除被整理相册自身,允许 0 目标即仅保留可用);配置确认创建 session 进入整理界面;顶栏 ← 退出自动保存进度。新建 session 清掉旧 decisions,防止重建后已决策残留导致直接完成态;卡片手势界面属下一批任务

- 阶段 3.5 数据层:快速整理的 session 与决策仓库 —— DB 1→2 迁移把 `quick_sort_session.targetAlbumIds`(逗号分隔字符串)拆为带顺序的 `quick_sort_session_target` 独立表(陷阱 5);决策(保留/跳过/移动)与相册关联变更同事务落库,完成态自动判定;「继续上次」按相册顺序过滤已决策,「从头开始」只清决策不删照片关联;同步补 MigrationTestHelper 基建(Robolectric + room-testing,schema 挂 debug 变体 assets)

- 数据一致性兜底:孤儿清扫 Worker(每日周期 + 进程启动补一次)—— 以 MediaStore 全量 id 为真源做差,统一清扫收藏/隐藏/相册关联/标签关联中指向已删照片的孤儿行(库外删除的兜底,幂等,批量分块避开 SQLite 变量上限);收藏反向同步(API 30+)—— 系统相册取消收藏后 Room 覆盖层自动对齐,与 Paging 失效共用 ContentObserver 触发,30 秒宽限期内新写入的收藏不参与对齐以避开 createFavoriteRequest 生效延迟的时序竞态

- 阶段 3 收尾:相册详情页多选 —— 长按进入多选(语义与时间线一致:选空自动退出、BACK 退出多选),操作栏含「移出」(仅解除相册关联,原图不动,封面按规则顺延/清空)与「设为封面」(选中 1 张时,覆盖「最近一张」自动封面);照片移空后显示空态

### Fixed

- 全屏查看器翻页窗口冻结:查看器此前只读 `itemSnapshotList` 快照取页内容,不产生任何 access hint(`LazyPagingItems` 里只有 `get()` 会上报访问位置),分页引擎感知不到用户翻近窗口尾部,预取永不发生 —— 查看器只能翻进入时的已加载窗口。修复:pager 页内经 `items[pagingIndex]` 回读当前页(带分页下标的快照映射),提示机制与网格一致;260 张图实测,修复前 205 页后停在窗口末尾,修复后可持续推进

- 阶段 3 收尾:智能相册详情(收藏 / 截图)—— 置灰卡片转正并补最近一张封面与条数统计;收藏在 API 30+ 直查 MediaStore `IS_FAVORITE` 条件分页(含系统相册打的收藏)、29 及以下回退 Room 收藏覆盖层,截图走 `RELATIVE_PATH` 匹配(API 29 起才有该列,低版本不展示入口);详情页复用相册详情骨架,条件分页在 API 30+ 走 Bundle 查询参数附带 selection,概览(条数 + 封面)自带 ContentObserver

- 阶段 3:相册 Tab 替换占位页 —— 自定义相册(新建 / 重命名 / 删除 / 封面自动取最近一张 / 条数统计)与详情页(照片网格,点进查看器);多选操作栏新增「加入相册」与「标签」(勾选语义 = 同时打在所有选中照片,按交集差量,对话框内可就地新建标签并自动配色)

- 阶段 2:多选操作栏新增分享、设为壁纸、重命名三项(操作栏扩为 6 项并支持横向滚动)。分享走 `ACTION_SEND_MULTIPLE` 多张原生支持;设为壁纸走系统裁剪流程、单张语义(多选时明确提示);重命名对话框只编辑主体名、扩展名自动拼回,API 30+ 对非本 App 照片先 `createWriteRequest` 拿写授权再重试

- 阶段 1 收尾:时间线支持双指捏合变列数(1 列到按屏幕宽度反推的上限,缩略图最小可读边长 64dp),列数持久化(切 tab / 转屏 / 进程重启不丢);捏合按 1.25 一档分步提交避免缩略图连发重解码,列数变化时保持视觉锚点
- 阶段 0:工程初始化(Gradle 骨架、CI、Hilt / Room / Compose 依赖、Room Schema 占位)
- 阶段 1.1:权限引导门 + MediaStore 数据层(Paging 3 分页、ContentObserver 失效重载、Room 收藏 / 隐藏覆盖层)
- 阶段 1.2:时间线照片墙 —— Coil 自定义 Fetcher 走系统缩略图、三列网格、按拍摄时间分组的日期表头
- 阶段 1.3:全屏查看器 —— 左右滑动翻页、双指缩放、复用时间线的分页数据、原图与缩略图按 model 类型分流
- 阶段 1.2 收尾:时间线下拉刷新(Paging refresh)、空相册状态、首屏加载骨架屏
- 阶段 2 核心子集:长按进入多选,支持收藏 / 隐藏 / 删除。删除在 API 30+ 走 `createDeleteRequest` 系统确认进回收站,29 及以下永久删除前 App 内确认;收藏写入 Room 覆盖层并按 API 分路径同步 `MediaStore.IS_FAVORITE`;删除完成后清理对应条目的 Room 收藏 / 隐藏记录,避免孤儿行

### Changed

- 权限面扩展:API 29 及以下新增 `WRITE_EXTERNAL_STORAGE` 与 legacy storage 标志(直接删除 / 收藏同步需要);API 30+ 维持只读权限
- 时间线现在过滤掉隐藏照片,`hidden_photo` 覆盖层从"仅打标"升级为实际隐藏

### Fixed

- 多选模式下按系统返回键现在退出多选,而不是直接退出页面(时间线是起始目的地,默认 BACK 行为会结束 Activity)

- 收藏 / 隐藏时必崩(`IllegalStateException: Attempt to collect twice from pageEventFlow`):`images()` 原先 combine 直接包住同一个 PagingData 实例,元数据变化会对其二次收集。改为 `flatMapLatest` 包住冷 Pager 流,元数据变化时产出全新分页代(真机模拟器 API 28 复现并验证修复)

- MediaStore 分页在 API 30+ 上必然失败:原先把 `LIMIT` / `OFFSET` 拼进 `sortOrder`,而 MediaProvider 会校验该字符串并抛 `IllegalArgumentException: Invalid token LIMIT`,导致时间线始终为空。改为 API 30 以上走 Bundle 查询参数(`QUERY_ARG_LIMIT` / `QUERY_ARG_OFFSET`)

### 计划中

- 阶段 1.2 剩余:网格捏合改变每行列数
- 阶段 2 剩余:重命名、移动文件夹、旋转、分享、设为壁纸、添加标签
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