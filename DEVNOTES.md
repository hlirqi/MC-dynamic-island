# 开发要点与维护手册 · Dynamic Island

> 面向后续维护者。读完这一份，你应该能在不看全部源码的情况下，安全地加一个模块、修一个 bug、排一次编译错误。  
> 配套文档：`README.md`（功能与操作）、`BUILD.md`（从零编译教程）。

- **modId**：`dynamicisland`
- **版本**：1.0.3
- **平台**：Minecraft 1.20.1 / Forge 47.1.3，**纯客户端模组**（服务端零逻辑，`server` 侧仅有一个空的 `ServerBridge` 占位以满足 `mods.toml` 两侧加载检查）
- **代码规模**：约 8900 行 Java / 62 个文件
- **许可**：MIT
- **更新日志**：`CHANGELOG.md`

---

## 一、总体架构

```
com.dynamicisland
├── DynamicIsland.java        模组主类：注册配置、按键、设置界面入口
├── Config.java               ForgeConfigSpec + 静态热缓存 + bake()/save()
├── client/                   全部客户端逻辑（约 40 个类）
├── command/IslandCommand.java  /island 命令
└── mixin/                    只读 accessor + 成就 toast 拦截
```

数据流是**单向流水线**，每帧只跑一次：

```
[采集层]  Metrics / 各 Watch 类   ──每 tick 更新──►  静态状态字段
                     │
                     ▼
[仲裁层]  ProgressTracker.update(dt)   ──产出──►  IslandStatus（本帧要显示什么，单值）
                     │
                     ▼
[渲染层]  IslandRenderer.render()      ──读取──►  IslandStatus + 各源静态字段 → 画面
```

**三条铁律**（破坏它们是最常见的 bug 来源）：

1. **采集层只写自己的静态字段，永远不碰渲染。** 渲染层每帧去读。
2. **同一时刻只有一个 `IslandStatus`。** 需要"谁优先显示"时，在 `ProgressTracker` 里按优先级 `return`，不要在渲染层做 if-else 堆叠。
3. **长驻信息走"两段式"模型**（详见第四节），不要自己往胶囊上画。

---

## 二、模块速查（想改什么，看哪个文件）

### 2.1 采集层（client/）

| 文件                     | 负责                                                | 触发/来源                        |
| ---------------------- | ------------------------------------------------- | ---------------------------- |
| `Metrics.java`         | FPS、TPS、延迟、内存、坐标朝向、群系维度、时间、速度、天气、游戏时长、实体数、粒子数、结构名 | 每 tick + 每 4 秒查 ping         |
| `ProgressTracker.java` | **仲裁中枢**：挖掘、使用、蓄力、鞘翅飞行、传送门、跳过夜晚、死亡、焦点三源           | 每 tick                       |
| `Notifier.java`        | 通知队列 + 历史 12 条 + 音效分级                             | push / 每秒 check              |
| `BuildTracker.java`    | 放置方块后的剩余数量                                        | 右键事件                         |
| `Reticle.java`         | 准星指向的生物 / 容器信息                                    | 每 tick                       |
| `BlockPanel.java`      | 准星方块信息弹窗                                          | 渲染                           |
| `DeathMark.java`       | 死亡点回溯（手动按键 `N` 开启）                                | 每 tick                       |
| `XpWatch.java`         | 经验获得 / 升级进度                                       | 每 tick                       |
| `CooldownWheel.java`   | 物品冷却环                                             | 每 tick                       |
| `EffectQueue.java`     | 药水效果队列                                            | 每 tick                       |
| `ItemPop.java`         | 拾取物品上岛                                            | 拾取事件                         |
| `MusicCard.java`       | 唱片 / BGM 识别与进度                                    | 音效事件 + 每 0.5s 扫唱片机           |
| `TritiumLink.java`     | **反射**联动 Tritium Music 模组                         | 每 tick                       |
| `DayClock.java`        | 昼夜更替、天气切换提醒                                       | 每 tick                       |
| `DepthWatch.java`      | 深层环境（Y 矿物带 / 洞穴预警）                                | 每 tick                       |
| `BlastWatch.java`      | TNT / 苦力怕 引爆倒计时                                   | 每 tick                       |
| `FallWatch.java`       | 坠落预测（落地伤害 / 是否致死 / 剩余时间）                          | 每 tick                       |
| `RideWatch.java`       | 矿车/船/鞘翅 实时速度                                      | 每 tick                       |
| `TradeWatch.java`      | 村民交易补货倒计时                                         | 每 tick（读 `VillagerAccessor`） |
| `DurabilityWatch.java` | 装备耐久                                              | 每 tick                       |
| `PotionWatch.java`     | 长效药水剩余                                            | 每 tick                       |
| `VitalsWatch.java`     | 饥饿 / 氧气                                           | 每 tick                       |
| `DropWatch.java`       | **死亡掉落物回收倒计时**                                    | 死亡打点 + 每 tick                |
| `XpOrbWatch.java`      | **经验球消失倒计时**（与掉落物同为 6000 tick）                     | 每 tick                       |
| `CloudWatch.java`      | **滞留药水 / 效果云剩余时间**（AreaEffectCloud）                   | 每 tick                       |
| `EndermiteWatch.java`  | **末影螨存活时间**（2400 tick）                              | 每 tick                       |
| `MarkWatch.java`       | 被标记（发光效果）                                         | 每 tick                       |
| `CureWatch.java`       | **僵尸村民治愈进度**                                      | 每 tick                       |
| `PetWatch.java`        | 宠物死亡 / 拴绳断裂通知                                     | 每 tick                       |
| `SurvivalWatch.java`   | **细雪冻结进度 + 头卡方块的窒息存活估算**                            | 每 tick                       |
| `CombatWatch.java`     | **盾牌破防禁用倒计时（5 秒）**                                | 每 tick                       |
| `FishingWatch.java`    | **钓鱼咬钩窗口**（读 `FishingHook.biting`）                        | 每 tick                       |
| `TimerCenter.java`     | **自定义定时器 / 倒计时**：数据模型、tick 推进、落盘、焦点槽归属                    | 每 tick                       |

### 2.2 渲染层

| 文件                                   | 负责                                                                      |
| ------------------------------------ | ----------------------------------------------------------------------- |
| `IslandRenderer.java`                | 顶层绘制：胶囊、信息条、通知、焦点、弹窗；所有动画状态住在这里                                         |
| `RenderUtil.java`                    | 纯顶点图元：圆角矩形、圆环、折线、UV 裁剪贴图、阴影                                             |
| `ExtrasRenderer.java`                | 胶囊下方附加层（死亡点箭头、冷却环、效果队列、经验）                                              |
| `FocusOrb.java`                      | 灵动焦点（左右圆形小窗）的仲裁与绘制                                                      |
| `CoverTint.java`                     | 从封面纹理取主色（`glGetTexImage`）                                               |
| `IslandStatus.java`                  | 单帧显示快照（kind / title / sub / progress / icon / accent / showBar / cover） |
| `Theme.java`                         | 四套皮肤枚举                                                                  |
| `Anim.java`                          | 弹簧 / 缓动数学                                                               |
| `DiSlider.java` / `ColorButton.java` | 自绘控件                                                                    |
| `IslandScreen.java`                  | 设置界面（多页）                                                                |
| `DragScreen.java`                    | 透明拖动定位界面                                                                |
| `FunctionCenter.java`                | **功能中心**：自定义功能的入口界面（分页标签 + 事件列表 + 操作按钮）                                     |
| `TimerEditScreen.java`               | **定时器添加 / 编辑表单**（名称 / 描述 / 类型 / 时长 / 颜色，右侧带实时预览）                          |

### 2.3 入口与事件

- `DynamicIsland.java`：构造函数注册**配置 + 按键 + 设置界面扩展点**。
- `ClientEvents.java`：**唯一的 forge bus 监听入口**，所有 `@SubscribeEvent` 都在这里；`onTick` 里按固定顺序调度所有 Watch 的 `tick()`（顺序有依赖，改动前看第六节）。
- `KeyBinds.java`：按键注册。
- `IslandCommand.java`：`/island` 子命令。

---

## 三、按键绑定（KeyBinds.java）

| 常量             | 注册名                            | 默认键 | 功能        |
| -------------- | ------------------------------ | --- | --------- |
| `TOGGLE`       | `key.dynamicisland.toggle`     | `O` | 显示 / 隐藏   |
| `EXPAND`       | `key.dynamicisland.expand`     | `I` | 手动展开仪表盘   |
| `THEME`        | `key.dynamicisland.theme`      | `U` | 循环切皮肤     |
| `PANEL`        | `key.dynamicisland.panel`      | `K` | 打开设置界面    |
| `DEATH_RECALL` | `key.dynamicisland.death`      | `N` | 死亡点回溯开关   |
| `POS`          | `key.dynamicisland.pos`        | `J` | 拖动定位界面    |
| `FOCUS`        | `key.dynamicisland.focus`      | `B` | 呼出最高优先级焦点 |
| `FOCUS_LEFT`   | `key.dynamicisland.focusLeft`  | `G` | 呼出左焦点     |
| `FOCUS_RIGHT`  | `key.dynamicisland.focusRight` | `H` | 呼出右焦点     |
| `CENTER`       | `key.dynamicisland.center`     | `M` | 打开功能中心    |

**约定**：全部走「边沿触发」——在 `handleKeys` 里比对 `isDown() && !prevXxx`，处理完把 `prevXxx = isDown()`。打开界面类操作加 `mc.screen == null` 守卫，避免在其它界面里误触。

---

## 四、核心机制：两段式展示模型（重点！）

这是整个模组最重要的设计，长驻信息（音乐、昼夜、耐久、药水、饥饿氧气、掉落物、被标记、治愈）全部遵守它：

```
第一段（上岛）：事件发生 → 在胶囊主体上详细显示 islandTimer 秒
                    │  islandTimer 归零
                    ▼
第二段（入焦点）：并入左右两个圆形焦点，用圆环持续呈现进度
                    │  用户按 B / G / H
                    ▼
            bringBack()：把该焦点重新拉回胶囊详细显示（钉住 pinned）
```

**每个"焦点源"类都必须实现这三个静态方法：**

| 方法            | 语义                                               |
| ------------- | ------------------------------------------------ |
| `onIsland()`  | 是否正在胶囊主体上显示（`islandTimer > 0` 或用户未启用焦点功能时恒 true） |
| `pinned()`    | 是否已被用户按 `bringBack()` 钉回胶囊                       |
| `bringBack()` | 切换到钉住状态                                          |

外加一个静态字段 `islandTimer`、`pinned`，以及本模块的业务状态。

**仲裁在 `FocusOrb` 里做：**

- `enum Kind { NONE, MUSIC, TRITIUM, DAY, DURABILITY, POTION, HUNGER, AIR, DROP, MARK, CURE, XPORB, CLOUD, ENDERMITE, TIMER_A, TIMER_B }`
- `priority(Kind)` 决定谁占左槽 / 右槽：**AIR(13) > DROP(12) > TIMER_A/B(11) > XPORB(10) > MARK(9) > CLOUD(8) > DURABILITY(7) > HUNGER(6) > POTION(5) > CURE(4) > ENDERMITE(3) > MUSIC/TRITIUM(2) > DAY(1)**
- **`TIMER_A` / `TIMER_B` 是「槽位」而不是「某个事件」**：焦点只有两个槽，`TimerCenter.assignSlots()` 每 tick 把最紧急的两条事件标成槽 1 / 槽 2，`collect()` 只按标记取数据。所以呼出 / 收回永远不会认错事件（`release(TIMER_A)` → `TimerCenter.bringBackSlot(1)`）。
- 最多 2 个槽位（`SLOT_LEFT` / `SLOT_RIGHT`），**槽位归属保持上一帧位置**以避免抖动。
- 渲染调用点：`IslandRenderer.render` 第 214–217 行，焦点随胶囊展开而整体淡出（`1f - clamp(grow)`）。

> 加一个新的长驻信息源 = 新建一个 Watch 类（照抄 `MarkWatch` 结构，它最干净）+ 在 `FocusOrb` 的 `Kind`/`priority`/`pinnedKind`/`bringBack` 各加一行 + 在 `ProgressTracker.tryFocusStage` 里登记 + 在 `ClientEvents.onTick` 里加 `tick()`。

---

## 五、Mixin 清单（mixin/）

**全部是只读 accessor（除 `ToastComponentMixin`），风险极低，字段名对不上时静默降级。**

| 文件                            | 目标                    | 访问内容                                                   | 用途                                               |
| ----------------------------- | --------------------- | ------------------------------------------------------ | ------------------------------------------------ |
| `MinecraftAccessor`           | `Minecraft`           | `fps`                                                  | 读真实帧率（`Metrics` 优先用，失败降级）                        |
| `ItemEntityAccessor`          | `ItemEntity`          | `age`                                                  | 掉落物消失计时；**比 `tickCount` 准**（物品合并后 `age` 会重置为较小值） |
| `ExperienceOrbAccessor`       | `ExperienceOrb`       | `age`                                                  | 经验球消失计时（同为 6000 tick）；读不到退回 `tickCount`           |
| `EndermiteAccessor`           | `Endermite`           | `life`                                                 | 末影螨存活时间（2400 tick）；读不到退回 `tickCount`               |
| `FishingHookAccessor`         | `FishingHook`         | `biting`                                               | 钓鱼咬钩判定（由同步数据 `DATA_BITING` 驱动，多人也准）                            |
| `MultiPlayerGameModeAccessor` | `MultiPlayerGameMode` | `destroyProgress` / `destroyBlockPos` / `isDestroying` | 挖掘进度                                             |
| `EntityAccessor`              | `Entity`              | `portalTime`                                           | 传送门状态感知                                          |
| `VillagerAccessor`            | `Villager`            | `lastRestockGameTime` / `numberOfRestocksToday`        | 村民补货倒计时                                          |
| `AdvancementToastAccessor`    | `AdvancementToast`    | `advancement`                                          | 取成就对象                                            |
| `ToastComponentMixin`         | `ToastComponent`      | `@Inject addToast` HEAD                                | 拦截成就 → 转灵动岛通知（**不 cancel**，原版弹窗仍显示）              |

注册在 `src/main/resources/dynamicisland.mixins.json`（`client` 数组）。**新增 accessor 后必须把类名加进这个数组**，否则不生效。命名前缀统一 `dynamicisland$`。

---

## 六、运行时序（ClientEvents.onTick）

顺序有依赖，改动需谨慎。每 tick 严格按此顺序：

```
1. Metrics.tick()                        ← 其它模块依赖它算出的 fps/坐标等
2. Notifier.tick(1/20f)                  ← 先推进旧通知的寿命
3. BuildTracker → Reticle → DeathMark → XpWatch
   → CooldownWheel → EffectQueue → ItemPop
4. MusicCard → TritiumLink → DayClock → DepthWatch
   → BlastWatch → FallWatch → RideWatch → TradeWatch
   → DurabilityWatch → PotionWatch → VitalsWatch
   → SurvivalWatch → CombatWatch → FishingWatch
5. 死亡上升沿检测：isDeadOrDying() 由 false→true 时调 DropWatch.markDeath(坐标)
6. DropWatch → MarkWatch → CureWatch → XpOrbWatch → CloudWatch → EndermiteWatch
   → TimerCenter.update(1/20f) → FocusOrb.update(1/20f) → PetWatch
7. 每 20 tick（1 秒）：Notifier.check(mc) + Notifier.checkSystem()
8. handleKeys(mc)
```

要点：

- 只在 `Phase.START` 执行（`END` 直接 return），避免一 tick 跑两次。
- `mc.player == null` 时（主菜单 / 加载中）**提前 return**，此时不推进任何模块，也不处理按键。
- `FocusOrb.update` 必须排在三大焦点源 `tick()` **之后**，否则拿到的是上一帧状态。

---

## 七、渲染机制（IslandRenderer）

顶层 `render(GuiGraphics gg, float partialTick)` 的分层绘制顺序（改布局时对照）：

1. **可见性与位移动画**：`visible && Config.enabled && !(hideOnDebug && renderDebug)`；收起时 `animY → -26f`；`alpha <= 0.01` 直接返回。
2. **取状态**：`ProgressTracker.update(dt)` → `IslandStatus st`；`st == null` 用 `idleStatus()`。kind/title 变化时 `contentFade = 0` 重播淡入。
3. **尺寸目标**：展开 `targetW=208`；收起 `targetW = idleWidth()`（按 showFps/showGraph/showMem/showTps 累加，最小 72）。
4. **定位坐标系**：`cx = 屏幕中心 + offsetX`，`cy = offsetY + animY`；`ps.scale(effScale)`，`effScale = scale * pulse`（心跳并入）。
5. **封面配色**：需 `modTritium && tritiumCover && TritiumLink.playing && tint >= 0`；`coverMix` 插值，主色与 accent `RenderUtil.mix`。
6. **胶囊本体**：阴影 → 0.8f 边框 → 主背景 → 模糊封面铺底（裁中心比例，压暗 0.42）。
7. **裁剪**：`gg.enableScissor(...)`（用未缩放 GUI 坐标手动换算）；`grow > 0.02` 画 `drawExpanded`，`grow < 0.98` 画 `drawIdle`（交叉淡入淡出）。
8. **信息条** → **灵动焦点** → **方块信息弹窗** → **ExtrasRenderer** → **通知**。

**关键实现陷阱**：

- 所有 `roundRect*` 图元必须保持**混合开启**（后续文字 / 物品渲染依赖）。
- **`drawDashboard` 的排版必须跟随实时高度 `cur` 自上而下分配**，每行 `if (cur + X <= bottom)` 才画。历史 bug：按最终高度写死 `y+42/y+53`，展开动画过程中内容溢出胶囊——**不要退回这种写法**。
- 帧率折线用细长 QUADS 逐段画，不要用 `GL_LINES`（线宽跨平台不一致）。
- 待机层每个元素都要 `if (px + need <= limitX)` 守卫，绝不越出胶囊右缘。

---

## 八、反射降级三处（跨版本 / 外部模组的兼容策略）

模组有 3 处主动使用反射，全部 try-catch 静默兜底，**失败不影响其它功能**：

| 位置                      | 反射什么                                                                                  | 降级行为                            |
| ----------------------- | ------------------------------------------------------------------------------------- | ------------------------------- |
| `TritiumLink`           | 外部模组 `tritium.music.core.CloudMusic` 的静态字段与方法（无正式 API，属"事实接口"）                        | `available() = false`，联动全关，其它照常 |
| `Metrics.structureName` | `Level#structureManager` → `getStructureAt` → `BuiltInRegistries.STRUCTURE_TYPES` 整条链 | 返回 `""`，信息条跳过结构项                |
| `ClientEvents.chatText` | 消息事件字段名（`getMessage()` / `getContent()`）                                              | 被 @ 检测与进出服识别失效                  |

**Tritium 联动的进度读取优先级**（源码里写死了这个顺序，别改）：  
`getCurrentTimeMillisInterpolated`（平滑插值，官方建议）→ `getCurrentTimeMillis`；总时长 `getTotalTimeMillis` → 歌曲 `getDuration`。  
**取封面主色必须等纹理上传完**（`AbstractTexture.getId() >= 0`），否则读到黑图。

---

## 九、配置系统（Config.java）

- `ForgeConfigSpec SPEC` 定义所有项；用户改配置后值先进 `SPEC`。
- **静态热缓存**：所有 `Config.xxx` 静态字段由 **`bake()`** 从 `SPEC` 刷新。渲染 / 采集层直接读静态字段（零开销）。
- 用户改动后必须调 **`Config.save()`** 落盘；重新读取调 **`Config.bake()`**。
- 设置界面与 `/island reload` 会自动触发 `bake()`。
- **6 个板块可各自覆盖强调色**：`colorIsland / colorInfo / colorNotice / colorBlock / colorFocus / colorMusic`，取值 `-1` 表示跟随皮肤 `accent`（`Config.accentOf()`）。
- 浅色皮肤使用独立的 `lightOpacity`（默认 0.55），深色用 `opacity`（默认 0.82），见 `Config.bgOpacity()`。

---

## 十、扩展指南

### 加一个新焦点源（长驻信息）

1. 复制 `MarkWatch.java` 为模板（它代码最干净），实现 `tick` / `onIsland` / `pinned` / `bringBack` 与 `islandTimer`、`pinned` 字段。
2. `FocusOrb.Kind` 加枚举；`priority()` 加权重；`pinnedKind()` / `bringBack()` 各加一行。
3. `ProgressTracker.tryFocusStage()` 里登记（按优先级排在合适位置）。
4. `ClientEvents.onTick` 的对应分组里加 `tick()`。
5. `Config` 加开关（`focusXxx`），`mods.toml` / `zh_cn.json` / `en_us.json` 加文案。

### 加一条通知

在 `Notifier.check()`（周期）或事件回调里调：

```java
Notifier.push(标题, 副标题, 图标ItemStack, 强调色, "去重键", 冷却秒);              // NORMAL
Notifier.push(标题, 副标题, 图标, 强调色, "去重键", 冷却秒, Notifier.Level.URGENT); // 带音效
```

去重键 + 冷却秒可防止刷屏。历史回看自动收录（`Config.noticeHistory`）。

### 给「功能中心」加一个新功能页

功能中心是为后续扩展留的壳，加一页只要三步：

1. `FunctionCenter.TAB_KEY` 加一行文案键（分页按钮自动排布）。
2. `render` 里那个 `switch (tab)` 加一个分支，画自己的列表；行命中测试复用 `rowAt()`。
3. 需要落盘就仿 `TimerCenter` 写一个独立的 json（`FMLPaths.CONFIGDIR`），
   **运行时状态一定标 `transient`**，否则重开游戏会自动"接着跑"，与预期不符。

### 加一套皮肤

`Theme` 枚举加一行（`id, bg, border, text, textDim, accent, good, warn, bad, dark`），设置界面与 `/island theme` 自动收录。

### 加一个按键


`KeyBinds` 加常量 + 在 `register()` 里 `e.register(...)` + 在 `ClientEvents.handleKeys` 加边沿触发分支。

---

## 十一、已知坑 / 常见问题排查

| 现象                      | 根因                                                                | 处理                                                                                         |
| ----------------------- | ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| 加/改 Mixin 后无效           | 忘记注册进 `dynamicisland.mixins.json`                                 | 把类名加进 `client` 数组                                                                          |
| Mixin 字段名报错 / 某形态不显示    | 映射表字段名对不上                                                         | 不影响编译与其它功能；日志搜 `Mixin apply failed`。可用 `srg_to_official_1.20.1.tsrg` 查 `f_xxx_/m_xxx_` 对照  |
| 编译期找不到方法 / 字段           | 参考了其它版本 API                                                       | 用 `javap -p -c` 反编译确认签名；Forge 原版逻辑查 `-sources.jar` 里的 `.patch`                             |
| 掉落物倒计时读不准               | 用了 `tickCount`                                                    | 用 `ItemEntityAccessor.age`（物品合并会重置 tickCount）                                              |
| 死亡掉落物不触发                | 时序竞态：扫描不到就立刻清 `deathAt`                                           | 保持 `captureTicks` 窗口（≈5 秒）内不清理，物品可能晚几 tick 才刷到客户端                                          |
| 僵尸村民治愈进度不动              | `getConversionProgress()` 是 **private** 且语义是"附近特殊方块加速倍率"，**不是进度** | 改读 `MobEffects.DAMAGE_BOOST` 时长；读不到时用 `totalTicks - elapsed` 本地兜底（`FALLBACK_TICKS = 3600`） |
| 治愈进度整块消失                | 读不到效果就直接 reset                                                    | 必须保留本地计时兜底，别让倒计时归零消失                                                                       |
| 展开动画中内容溢出胶囊             | `drawDashboard` 写死了最终高度坐标                                         | 按实时高度 `cur` 逐行分配                                                                           |
| 改了 lang / mods.toml 不生效 | 资源未重打包                                                            | `gradlew clean build`                                                                      |
| 中文注释乱码                  | 编码                                                                | `build.gradle` 已设 UTF-8，编辑器也选 UTF-8                                                        |
| `mods.toml` 中文变乱码 / 半个汉字    | `processResources` 过滤时用平台编码（中文 Windows = GBK）读写 UTF-8 文件            | `build.gradle` 里已设 `filteringCharset = 'UTF-8'`；**这行别删**                                        |
| 经验球倒计时一沾经验就刷屏          | 把所有附近经验球都算进来了                                                       | `XpOrbWatch` 只统计超出拾取跟随范围（>12 格）的球；并只在「从无到有」时上台一次                                        |
| 效果云剩余时间读成 0 或直接不显示     | 读的是 `duration` 而不是 `waitTime + duration`                          | 剩余 = `getWaitTime() + getDuration() − Entity.tickCount`（原版死亡判定同源），别只减 duration                 |
| 末影螨 / 经验球焦点源有时不显示      | Mixin accessor 字段名对不上，静默降级                                       | 两个 Watch 都有 `tickCount` 兜底，属于预期行为；日志搜 `Mixin apply failed` 确认                                |
| 钓鱼咬钩提示不出现                | 只用了 `Player.fishing`：该字段由服务端 `FishingHook` 构造时赋值，多人模式的客户端上是 null    | `FishingWatch.ownHook` 会退回扫描附近钩子并按 `getPlayerOwner()` 过滤，**别删这段兜底**                       |
| 细雪冻结进度一直不动               | `ticksFrozen` 是同步数据，只有服务端在算；客户端读到的有 1~2 tick 延迟                      | 正常现象；不要改用 `tickCount` 之类本地推算                                                                 |
| 窒息警告在创造模式乱弹              | `isInWall()` 与是否受伤无关                                           | `SurvivalWatch` 已过滤 `isCreative() / isSpectator()`，别删                                            |
| 定时器圆环中心的时间不是自定义颜色      | 中心文字默认用 `th.text`，颜色要一路透传                                        | `Cand.textColor` → `Slot.textColor` → `drawOrb` 判断 `>=0` 才覆盖；胶囊那边走 `IslandStatus.textColor`（别删）        |
| 呼出定时器焦点时认错事件             | `TIMER_A/B` 是槽位、不是事件 id                                          | 别在 `collect()` 里自己排事件顺序，一律用 `TimerCenter.bySlot(1/2)`（`assignSlots()` 已经算好）                  |
| 定时器点完「启用」不动              | 计时用 `ClientTickEvent` 累加，主菜单 / 无玩家时不推进                             | 预期行为（游戏内计时）；暂停游戏也会暂停                                                                    |

### 诊断三件套

```bash
# 1. 查映射：SRG ↔ 官方名
grep "f_xxxxx_" srg_to_official_1.20.1.tsrg

# 2. 反编译看真实签名
javap -p -c -cp <forge-merged-jar> net.minecraft.world.entity.item.ItemEntity

# 3. 查原版逻辑：解压 Forge 的 -sources.jar，读对应的 .patch
```

---

## 十二、编译

```bash
gradlew build         # 产物 build/libs/dynamicisland-1.0.3.jar
gradlew runClient     # 直接启动带模组的客户端测试
gradlew clean build   # 改了资源文件后
```

需要 **JDK 17**（硬性）。首次构建要下载并反编译 Minecraft，约 10–30 分钟。国内网络加速与完整报错对照表见 **`BUILD.md`**。

---

## 十三、提交到 Git 前的检查清单

- [ ] `build/`、`.gradle/`、`run/`、`out/`、`*.log` 已被忽略（见 `.gitignore`）
- [ ] jar 产物按需提交到 `outputs/` 或走 Release，不混进 `src/`
- [ ] 改了 Mixin 记得同步 `mixins.json`
- [ ] 新增配置项补 `bake()` 并把文案加进两个 lang 文件
- [ ] 新增按键检查是否与其它模组冲突
