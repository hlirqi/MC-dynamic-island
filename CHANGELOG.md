# 更新日志 · Dynamic Island

本模组的版本变更记录。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [1.0.1] - 2026-10-05

### 新增 · 灵动焦点（`设置 → 灵动焦点` 可各自开关）

三个原版「只有状态、没有倒计时」的隐性信息，现在纳入两段式展示：**先在胶囊上提示数秒，随后并入胶囊两侧的圆形焦点持续显示进度环**。

- **经验球消失倒计时** `focusXpOrb`（默认开）
  - 与掉落物是同一套机制（存活 6000 tick = 5 分钟），但原版对经验球只字不提。打完一群怪退开后，地上那摊经验球什么时候会悄无声息地消失，现在一目了然。
  - 只统计**够不着**的经验球（超出拾取跟随范围，默认 >12 格）：贴身那几颗马上会被吸走，给它们倒计时没有意义。焦点副标题会顺带显示「附近 N 个」。
  - 圆环中心显示剩余时间（>60 秒时显示分钟），胶囊图标为附魔之瓶。
- **滞留药水 / 效果云剩余时间** `focusCloud`（默认开）
  - `AreaEffectCloud` 放下之后就完全隐性。战斗里踩着持续伤害云、或者守着治疗云的时候，剩余时间现在随时可见。
  - 剩余 = `waitTime + duration − tickCount`，全部走公开成员读取，**不依赖 Mixin**。
  - 云尚未生效时（`tickCount < waitTime`）显示「x 秒后生效」，生效后显示「剩余 x」。
  - **圆环颜色直接取云自身的药水颜色**，一眼能分清是伤害云还是治疗云。
- **末影螨存活时间** `focusEndermite`（默认开）
  - 末影螨固定 2 分钟（2400 tick）后自然死亡，原版界面毫无提示。末影珍珠刷怪 / 珍珠传送翻车时，它还能撑多久不再靠猜。
  - 存活进度 = `life / 2400`，圆环随时间倒空；胶囊图标为末影螨刷怪蛋。

### 变更

- 灵动焦点的**占位优先级重新编号**，保持原有相对顺序不变，为三个新来源腾出位置：
  `氧气 > 掉落物回收 > 经验球 > 被标记 > 效果云 > 装备耐久 > 饥饿 > 长效药水 > 村民治愈 > 末影螨 > 音乐 > 昼夜`。
  设置界面「并入来源」分区下方的优先级说明文案已同步更新（中英文）。
- 模组版本号 `1.0.0 → 1.0.1`；`mods.toml` 描述、README、DEVNOTES 同步补上新焦点源。
- 设置面板「灵动焦点 → 并入来源」新增三个开关，布局沿用原有两列网格，无需翻页即可看到。

### 修复

- **`mods.toml` 中文描述乱码**（1.0.0 起就存在）：`processResources` 过滤 `mods.toml` 时用的是平台默认编码，
  在中文 Windows 上按 GBK 读取 UTF-8 文件，导致模组列表里的中文描述变成乱码甚至半个汉字。
  已在 `build.gradle` 显式指定 `filteringCharset = 'UTF-8'`，现在模组描述是完整的 UTF-8 中文。

### 技术细节

- 新增三个采集类（照抄 `MarkWatch` 结构，遵循两段式模型）：
  - `client/XpOrbWatch.java` — 扫描 24 格内经验球，取年龄最大（最快消失）的一颗，字段 `age`。
  - `client/CloudWatch.java` — 扫描 24 格内 `AreaEffectCloud`，取剩余最少的一团，公开 API 读取。
  - `client/EndermiteWatch.java` — 扫描 16 格内末影螨，取 `life` 最大的一只。
- 新增两个只读 Mixin accessor（已注册进 `dynamicisland.mixins.json`）：
  - `mixin/ExperienceOrbAccessor.java` → `ExperienceOrb.age`
  - `mixin/EndermiteAccessor.java` → `Endermite.life`
  - 两者读取失败时都**退回 `Entity.tickCount` 兜底**，静默降级，不影响其它功能。
- 接入点：`FocusOrb`（Kind / priority / collect / pinnedKind / release）、`ProgressTracker.tryFocusStage`、`ClientEvents.onTick`（排在 `FocusOrb.update` 之前）。
- 三源均采用「从无到有才算一次上台」的触发策略，避免走一步换一颗经验球就把胶囊刷一遍。

### 兼容性

- 纯客户端模组，行为不变；服务端无需更新。
- 三个开关默认开启，不想看某个来源可在设置面板单独关掉。

---

## [1.0.0] - 初始版本

首个公开版本。三种核心形态（待机 / 挖掘 / 使用）、信息条、灵动焦点（音乐、昼夜、耐久、药水、饥饿氧气、死亡掉落物、被标记、村民治愈）、通知中心、环境与状态感知、Tritium Music 联动与四套皮肤。
