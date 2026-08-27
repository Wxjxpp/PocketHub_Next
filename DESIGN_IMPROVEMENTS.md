# PocketHub UI Redesign (v2)

## 概述
本轮为全局 UI 重设计：统一设计系统 + 全部界面覆盖，重点是质感（hairline 描边卡片、渐变微光）、动效（spring 按压、级联入场、页面转场、Tab 内容过渡）与状态体验（全 App 骨架屏、动画空状态）。

## 设计系统核心 (`ui/components/DesignSystem.kt`)

| 组件 | 用途 |
|---|---|
| `Motion` | 全局动效令牌：`press()` spring / `enter()` tween / 级联步长 |
| `Modifier.pressScale()` | 任意可点元素的按压缩放反馈（可共享 InteractionSource 保 ripple） |
| `PhCard` | 签名卡片：发丝描边 + 顶部微光渐变 + spring 按压反馈 |
| `IconPlate` | 柔和圆角色块图标底座 |
| `StaggeredAppear` | 列表项级联入场（fade+上滑，45ms 步长，360ms 封顶） |
| `SkeletonBox / SkeletonCardRow / SkeletonList` | 流光骨架屏（默认加载态） |
| `CountPill / LanguageDot` | 圆角计数胶囊 / 语言色点 |
| `EmptyStateV2` | 动画空状态（图标圆盘 scale-in） |
| `SectionTitle` | 强调色 tick 区块标题 |

## 全局状态组件 (`ListStates.kt`)
- `LoadingState` → 全屏骨架屏（原来只是一个转圈）
- `EmptyState(icon=)` → 新动画空状态
- `ErrorState` → scale-in 动画

## 逐屏改动
- **导航** (`AppNavigation`): 页面转场 320ms 滑入+淡入（原来 200ms 硬切）
- **主框架** (`HomeScreen`): 顶栏标题 AnimatedContent 切换；底栏选中图标 spring 弹跳 + 颜色渐变
- **仓库列表**: RepositoryRow → PhCard；骨架屏；级联入场；EmptyStateV2
- **探索页**: EnhancedCard 全面重写（发丝描边+按压）；趋势/关注骨架屏；空状态升级
- **搜索页**: 骨架屏；空/无结果配 Search / SearchOff 图标
- **通知页**: 通知行卡片化 + 未读主色高亮圆点 + 图标着色；骨架屏
- **Profile**: 头部卡片渐变光；仓库/动态骨架屏
- **Repo 详情**: Tab 内容 AnimatedContent 滑动过渡；StatsRow star/fork 按压反馈；Overview/Issues/PRs/Releases/Workflows 全部骨架屏；Workflows 空状态
- **Commits**: Commit 行卡片化（去 divider）
- **Issue/PR/Commit 详情**: 全屏加载 → 骨架屏
- **Code**: 文件浏览骨架屏；文件夹图标 tertiary 着色
- **User 详情**: 头部渐变；骨架屏
- **历史页**: 整页卡片化重构 + IconPlate
- **下载页**: 空状态 → EmptyStateV2
- **登录页**: 品牌 logo 渐变块 + 整页入场动画（fade+上滑）+ 圆角错误条
- **更新弹窗**: 头部图标盘 + 版本副标题重排
- **设置页**: 7 个分区全部分组卡片化（去 divider）
- **新建 Issue**: 模板卡片描边化
- **构建产物**: ArtifactCard 圆角+描边

## 设计原则
- 卡片表面 = `surface`，描边 = `outlineVariant` 55% 透明——所有 6 套主题自动适配
- 动效只用 spring(0.55 damping) / tween(FastOutSlowIn)，感知一致
- 加载永远显示"内容的形状"（骨架），而不是转圈

**版本**: v0.3.9+
**日期**: 2026-08-27
