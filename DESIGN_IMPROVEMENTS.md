# PocketHub UI Design Improvements

## 概述
本次更新对 PocketHub 的整体 UI 进行了设计升级，在保持原有主题系统的基础上，大幅提升了视觉设计感和现代感。

## 核心改进

### 1. 设计系统增强组件 (`DesignEnhanced.kt`)

新增了一系列现代化的设计组件：

#### **EnhancedCard**
- 带阴影和渐变的高级卡片
- 按压交互动画（缩放 + 阴影变化）
- 微妙的边框和渐变背景
- 可配置的圆角和阴影高度

#### **GlassCard**
- 玻璃态设计风格
- 半透明背景 + 模糊效果
- 适合叠加层和英雄内容

#### **NeumorphicCard**
- 拟态化设计（Soft UI）
- 内外阴影营造深度错觉
- 适合浅色主题

#### **GlowingFAB**
- 带辉光效果的浮动按钮
- 径向渐变 + 按压动画
- 更强的视觉吸引力

#### **SectionHeader**
- 章节标题带渐变下划线
- 清晰的内容分割

#### **ShimmerBox**
- 骨架屏加载占位符
- 流动的渐变动画效果

### 2. ActivityCard 升级

**之前：** 简单的圆角矩形 + 平面背景

**现在：**
- 使用 `EnhancedCard` 包装，带阴影和深度
- 图标放入彩色圆形背景（使用主题的 primaryContainer）
- 改进的视觉层次：动词 → 仓库名分行显示
- 更好的间距和呼吸感
- 时间戳更轻量化的显示

**视觉效果：**
- 卡片悬浮感（2-3dp elevation）
- 按压时有缩放和阴影变化动画
- 图标更突出，信息层次更清晰

### 3. RepositoryRow 升级

**之前：** 扁平的列表项 + 简单分隔

**现在：**
- 完整的卡片化设计，每个仓库都是独立的视觉单元
- 头像增加阴影，更立体
- 仓库名字体加粗（titleMedium + Bold）
- 标签（PRIVATE/FORK）带彩色背景：
  - PRIVATE: 红色系（errorContainer）
  - FORK: 蓝色系（secondaryContainer）
- 元信息改进：
  - 语言标签圆点更大（10dp）
  - Star 图标用 tertiary 色高亮
  - Fork 图标用 secondary 色
  - 数字自动格式化（1.2k / 3.5k）
  - 移除冗余的更新时间（保持简洁）

**间距优化：**
- 水平 padding: 16dp
- 垂直间距: 6dp（卡片之间）
- 卡片内部间距: 16dp
- 圆角: 16dp（更现代）

### 4. 设计原则

#### **视觉层次**
1. 使用阴影和海拔营造深度
2. 通过字重（FontWeight）区分重要性
3. 色彩对比突出关键信息

#### **动效**
- Spring 动画（spring animation）提供自然的物理反馈
- 按压状态：scale 0.98 + elevation 减半
- 流畅的 300-400ms 过渡

#### **色彩运用**
- 主色（primary）：关键操作和链接
- 次要色（secondary/tertiary）：图标和辅助信息
- 表面变体（surfaceVariant）：卡片背景
- 渐变：微妙的 5-15% 透明度叠加

#### **圆角策略**
- 小元素（标签、圆点）：6-8dp
- 卡片：14-16dp
- 大容器：18-20dp
- 头像：CircleShape

#### **间距系统**
- 超小：4dp（紧密相关元素）
- 小：8dp（相关元素）
- 中：12dp（段落间）
- 大：16dp（卡片边距）
- 超大：20-24dp（章节间）

## 兼容性

### 主题系统
所有改进完全兼容现有的 6 种主题风格：
- LinearDark（默认深色）
- PrimerLight（GitHub 风格）
- Paper（阅读友好）
- Neon（赛博朋克）
- Lavender（紫色梦幻）
- Forest（森林绿）

每个主题的颜色、字体、圆角都会自动应用到新组件。

### 向后兼容
- 未修改的屏幕仍使用旧组件
- 可以逐步迁移，不会破坏现有功能
- 所有新组件都是可选的增强

## 使用建议

### 推荐迁移顺序
1. ✅ **ActivityCard** - 已完成
2. ✅ **RepositoryRow** - 已完成
3. 📝 IssueRow / PullRequestRow - 下一步
4. 📝 CommitRow - 下一步
5. 📝 NotificationRow - 下一步
6. 📝 UserCard - 下一步
7. 📝 WorkflowRunCard - 下一步

### 代码示例

```kotlin
// 使用 EnhancedCard
EnhancedCard(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    onClick = { /* 处理点击 */ },
    elevation = 3.dp,
    cornerRadius = 16.dp,
    gradientIntensity = 0.08f,
) {
    // 你的内容
}

// 使用 GlassCard（适合对话框、抽屉）
GlassCard(
    modifier = Modifier.padding(24.dp),
    onClick = { /* 可选 */ },
) {
    // 半透明内容
}

// 使用 ShimmerBox（加载状态）
ShimmerBox(
    modifier = Modifier
        .fillMaxWidth()
        .height(80.dp)
        .padding(16.dp),
    cornerRadius = 14.dp,
)
```

## 性能影响

- **动画开销**：使用 Compose 原生动画，GPU 加速，性能优秀
- **绘制复杂度**：阴影和渐变由系统优化，影响极小
- **内存占用**：无额外资源加载，纯代码实现

## 设计参考

灵感来源：
- Material Design 3
- iOS Human Interface Guidelines
- GitHub Mobile
- Dribbble 优秀作品
- Neumorphism / Glassmorphism 设计趋势

## 后续计划

### 短期（1-2 周）
- [ ] 迁移所有列表项组件
- [ ] 添加页面过渡动画
- [ ] 优化加载状态设计
- [ ] 增强空状态插图

### 中期（1 个月）
- [ ] 添加微交互（haptic feedback、ripple effect）
- [ ] 设计系统文档完善
- [ ] Figma 设计规范同步
- [ ] 无障碍优化（对比度、触摸目标）

### 长期
- [ ] 自定义主题编辑器
- [ ] 动态颜色支持（Material You）
- [ ] 深色模式智能切换
- [ ] 视觉一致性检查工具

## 贡献指南

欢迎提交更多设计改进：
1. 保持与现有设计系统一致
2. 考虑所有主题的兼容性
3. 提供前后对比截图
4. 性能测试通过
5. 遵循 Material Design 原则

---

**版本**: v0.1.127+
**更新日期**: 2026-08-26
**作者**: Kiro AI Assistant
