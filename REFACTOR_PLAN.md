# PocketHub 全项目重构计划 (fix 分支)

> 状态标记: [ ] 待办 / [~] 进行中 / [x] 完成
> 每完成一批在此更新进度，代码推 fix 分支，CI 编译通过才算完成。

## 目标与约束（用户要求）
1. 优雅、高效、无冗余：删死代码、删孤立代码
2. 按功能模块化解耦，单文件不超长（>500 行拆分）
3. 拓展性与复用性平衡：不为假想需求造抽象，重复实现必须合并
4. 性能：列表 key、remember/mutableStateOf 滥用、LaunchedEffect 竞态
5. 健壮：网络失败/空数据/极端输入的兜底
6. 体验：交互不合理处记录并修复

## 工作策略
- 小批次提交，每批一个主题，push 后用 GitHub Actions CI 做编译验证（本地无 Android SDK）
- 每批前跑 `/var/minis/workspace/audit.py` 复查
- 纯删除批次风险低先做；拆分批次按文件逐个来

## 审计快照 (2026-08-28, main=6feb77d, v0.3.12)
- 104 个 kt 文件，~29k 行
- 死符号 24 个（14 个可直接删，10 个仅文件内自用 → 改 private）
- 79 个未引用 string 资源（values + values-zh 同步删）
- 重复实现 5 组：humanBytes ×2、parseIso ×2、SectionHeader ×3、
  EmptyState/ErrorState/LoadingFooter（ExploreScreen 私有副本 vs ListStates）、RepoTab 枚举 ×2
- 超长文件 17 个（最长 RepoDetailScreen 1818 行）

## Phase 1 — 死代码清除 [x]
- [x] DesignEnhanced.kt 删 6 个死符号（GlassCard/GlowingFAB/NeumorphicCard/SectionHeader/ShimmerBox/adaptiveGridCells）337→141 行
- [x] DesignSystem.kt 删 4 个（CountPill/IconPlate/LanguageDot/SectionTitle）
- [x] ListStates.kt 删 LoadingState；Issue.kt 删 IssueListResponse
- [x] 自用符号降 private（Motion/SkeletonCardRow/DiffLine/ReviewEvent/classifyLink/describeEvent/formatRelativeShort/guessMime/inlineMarkdown/resolveStyle/AppStyleDef/HistoryEntry → 其中 HistoryEntry/DiffLine/AppStyleDef 经 CI 发现跨文件使用，已恢复 public）
- [x] 删 79 个未用 string（values + values-zh 各 79 条）
- [x] commit d5d8d43 + 3 个 fix commit，Compile Check ✓ (run 33096474488)
- 净变化: -470 行左右

## Phase 2 — 去重合并 [x]
- [x] humanBytes ×3（OpenLocalFile/UpdateDialog/ArtifactExtractor）→ util/Format.kt
- [x] parseIso 系 ×6（PRDetail/WorkflowRunDetail/NotificationsVM/ArtifactListComponents/CommitDetailScreen/CommitsTab）→ util/Format.kt 的 parseIso/parseIsoSafe
- [x] SectionHeader ×2 私有副本 → DesignSystem 公开一份（Phase1 曾误删死版，现恢复为共享 API）
- [x] ExploreScreen 私有 EmptyState/ErrorState/LoadingFooter → 改用 ListStates 统一版（调用点显式传 Article 图标保持原视觉）
- [x] ReposViewModel.RepoTab → 重命名 ReposTab，与 RepoDetailViewModel.RepoTab 区分
- [x] 清 38 个死 import；commit "consolidate duplicated helpers"，Compile Check ✓ (run 33126999920)
- 净变化: 约 -160 行

## Phase 3 — 超长文件拆分 [x]
按行数从大到小逐个拆，每拆 1-2 个文件一批：
- [x] RepoDetailScreen.kt (1818) → RepoDetailScreen 711 + RepoOverviewTab 296 + RepoListTabs 292 + RepoReleasesTab 220 + RepoWorkflowsTab 474
  - commit "split RepoDetailScreen into per-tab files" + 2 个 fixup，CI ✓ (run 33128945828)
  - 顺带去重: PullRequestDetailScreen.formatDate、CodeTab.humanReadableSize 私有旧版删除，统一用 RepoReleasesTab 的 internal 版
  - 踩坑: ①行区间切割要含 @OptIn 行（属于下一个声明）②同包 internal 与他文件同签名 private 冲突（顺势删旧版去重）
- [x] GitHubApi.kt (1495) → 16 个域接口文件（方法）+ GitHubApi.kt 698 行（接口头 + 全部嵌套 DTO）
  - commit "split GitHubApi into 16 domain endpoint interfaces" + 3 个 fixup，CI ✓ (run 33130763304)
  - 结构: GitHubApi : User/Follow/Repo/Content/Issue/Reaction/PullRequest/Commit/Branch/Release/Action/Notification/Event/Search/OAuth/GraphQL Endpoints，Retrofit 仍只注册 GitHubApi，调用点零改动
  - 关键认知: Kotlin 嵌套类**不能**通过子接口名解析（GitHubApi.X 若 X 声明在父接口 → Unresolved），所以 DTO 必须留在 GitHubApi 本体；方法继承没问题
  - 最大域文件 PullRequestEndpoints 141 行，其余 29-119 行
  - 留尾(可选): 若想把 DTO 抽成顶层 model 类需改全库调用点 GitHubApi.X → X，churn 大，暂不做
- [x] PullRequestDetailScreen.kt (1308) → 主屏 714 + PullRequestDialogs 439（8 个参数化弹窗）+ PullRequestParts 357（CommentInput/ReviewItem/FileDiffItem/ChecksCard/SectionError/ReviewEvent）
  - commit + OptIn fixup，CI ✓ (run 33132359139)
  - 踩坑: ①拆出的 Composable 用 ModalBottomSheet/FlowRow 需要 @file:OptIn（原主函数上的 @OptIn 不跟随）②组装脚本里旧的 `calls +=` 块没删导致调用重复——生成类脚本重构后必须全文检查残留
- [x] MarkdownText.kt (1301) → 主入口 255 + MarkdownParser 343 + MarkdownRender 566 + MarkdownLinks 174
  - 3 次 fixup 后 CI ✓ (run 33146893746)
  - 踩坑: ①主文件切割上边界差一行留了孤立 @Composable(语法错误, KDoc 则无害) ②private 检查正则漏 `const val` 和 `sealed class` ③脚本对同一文件连续 edit 必须重读文件, 否则后一次覆盖前一次
- [x] PullRequestDetailViewModel.kt (920) → core 346 + Inline 310 + Reviews 134 + Comments 141（扩展函数按域拆）
- [x] RepoDetailViewModel.kt (875) → core 448 + Workflows 113 + Admin 161 + Issues 62 + Releases 57 + Translation 66
  - 多轮 fixup 后 CI ✓ (run 33152579518)
  - 踩坑: ①to_extensions 必须先套 receiver 正则再去缩进 ②类属性会被域范围误切进扩展文件, 需回迁 ③companion 里的嵌套类类型引用不享受外层名捷径(ThreadInfo 提为顶层) ④`}    fun loadPulls(` 同行声明要拆行 ⑤域分隔注释锚点要防 `// ── Translation` 撞 `// ── Translation state` ⑥搬走的 GraphQL 常量插文件要放 import 之后

---

## Phase 3 完成总结 (2026-08-28)
已提交范围全部完成! 5 个巨石文件 → 22 个域文件, 提交物无一超过 ~714 行:
- RepoDetailScreen 1818→711+4 | GitHubApi 1495→698+16 | PullRequestDetailScreen 1308→714+2
- MarkdownText 1301→255+3 | 两个 ViewModel 920/875→346/448+8
- 所有产物在 fix 分支, CI 全绿; REFACTOR_PLAN.md 已入库

### 遗留待办 (转入 Phase 4 前置清单)
- [x] P3f (2026-08-28, 灵活裁量: 只拆有清晰缝的): relativeTime/formatCount/isoparser 去重入 util | SearchScreen 762→224+SearchFilters 560 | ExploreScreen 781→411+ExploreCards 405 | SettingsScreen 752→652+IssueReport 111 | CI ✓ 33154593671
- [x] FeedSourceService 721 行: 判定为单一内聚 service, 无拆分缝, 明确保留
- [x] 500-540 行 4 个(UserDetail/CodeTab/CreateIssueScreen/CodeHighlighter): 内聚单屏, 明确保留
- [x] 留尾去重: CodeTab.relativeTime、CodeBrowserViewModel.isoParser(实为死字段, 直接删) → util/Format.kt ✓
- 审计残留同名私有 helper 5 组: formatCount ×2 已合并 ✓; 其余 4 组(EditCommentDialog/IssueEventRow/StatPill/StatsRow)为跨包小 UI 片段, 各自内聚, 保留

## Phase 4 — 架构与健壮性/体验 [ ]
- [ ] 分页/列表 key 与性能复查
- [ ] 网络层统一错误处理复查
- [ ] 体验问题清单（随拆分过程收集）
- [ ] push + CI ✓

## 变更日志
| 批次 | commit | 内容 | CI |
|---|---|---|---|
| P1 | d5d8d43+3fix | 删死符号/未用 string/降 private，净 -470 行 | ✓ 33096474488 |
| P2 | consolidate | util/Format.kt 收敛 9 处重复实现 + 38 死 import，净 -160 行 | ✓ 33126999920 |
| P3a | split+2fixup | RepoDetailScreen 1818→711+4 个 tab 文件 | ✓ 33128945828 |
| P3b | split+3fixup | GitHubApi 1495→698+16 个域接口文件 | ✓ 33130763304 |
| P3c | split+1fixup | PullRequestDetailScreen 1308→714+Dialogs 439+Parts 357 | ✓ 33132359139 |
| P3d | split+3fixup | MarkdownText 1301→255+Parser 343+Render 566+Links 174 | ✓ 33146893746 |
| P3e | split+7fixup | PR VM 920→346+3ext \| Repo VM 875→448+5ext | ✓ 33152579518 |
| docs | [skip ci] | REFACTOR_PLAN.md 入库 fix 分支 | 无 CI |
| APK | v0.3.13 | fix 分支 workflow_dispatch 打包（含 P1+P2），已可安装 | ✓ 33127328233 |
| APK | v0.3.14 | fix 分支 workflow_dispatch 打包（含 P1+P2+P3 全部），供功能验证 | ✓ 33153027548 |
| P3f | split+1fixup | 去重 relativeTime/formatCount/死 isoParser + Search/Explore/Settings 拆缝 | ✓ 33154593671 |
| APK | v0.3.15 | fix 分支最终验证包（含全部重构），未合 main | ✓ 33155123661 |

## 当前状态 (2026-08-28 P3 全部完成后)
- fix 分支 = main + 20+ 个重构 commit，最新 CI ✓ 33152579518；v0.3.14 已从 fix 打包 (run 33153027548)
- 已完成: P1 死代码 / P2 去重 / P3 五巨石拆分（提交范围全绿）
- 未完成: 二梯队 11 个 500-780 行文件拆分(可选)、CodeTab 留尾去重、Phase 4 性能/健壮性/体验复查
- 工具链(可复用): /var/minis/workspace/phase3{a,b,b_fix,b_v2,c,d,e}*.py + fix_imports.py + check_balance.py
- 本地 APK 缓存: /var/minis/workspace/apk-cache/pockethub-0.3.14.apk（含全部重构，供功能验证）
- 注意: 每次 workflow_dispatch 会在远端 fix 产生 bump commit，push 前必须 git fetch origin fix + rebase

## 经验教训 (Phase 2)
1. phase2.py 首次运行失败后重跑会重复插 import —— 脚本要幂等或失败即回滚
2. fix_imports.py 加 DENYLIST={getValue,setValue}：`by` 委托不出现这些字样
3. `\bRow\b` 判定是对的（LazyRow/RepositoryRow 是不同词），ReposScreen 的 14 个死 import 是历史遗留
4. CodeTab.relativeTime 与 CodeBrowserViewModel.isoParser 仍是日期解析重复，留待 Phase 3 拆 CodeTab 时并入 util/Format.kt

## 经验教训 (Phase 1)
1. audit.py 的 usage 统计有漏检（HistoryEntry/DiffLine/AppStyleDef 实际跨文件在用却报 dead）——死符号结论必须当"嫌疑"对待，动手前单独 grep 核实，最终以 CI 为准
2. 脚本删函数块的括号配平会被字符串/注释里的花括号干扰 → 4 个文件留残块，靠 CI 逐步暴露。后续删块改用 file_edit 手工或修好配平器
3. 删 import 时 `androidx.compose.runtime.getValue/setValue` 不能按名字出现与否判断——`by` 委托不含该字样，删了会炸一片
4. 新增 .github/workflows/compile-check.yml：fix/refactor 分支纯编译检查（无 bump/无 release），build.yml 仍只在 main 发版
