# Dynamic Island · Minecraft Forge 1.20.1

> 把 iPhone 灵动岛搬进 Minecraft：屏幕顶部常驻一枚胶囊，平时是性能看板，有事时自动"长"成任务卡片，事完缩回；需要长期盯着的信息会缩成两侧的圆形焦点，持续用圆环显示进度。

- **作者**：hlirqi
- **平台**：Minecraft **1.20.1** / Forge **47.1.3+**（纯客户端模组）
- **许可**：MIT
- **产物**：`build/libs/dynamicisland-1.0.0.jar`

模块介绍 → 本页　|　从零编译教程 → [BUILD.md](BUILD.md)　|　**开发要点与维护手册 → [DEVNOTES.md](DEVNOTES.md)**

---

## 功能一览

### 三种核心形态

| 形态 | 触发 | 内容 |
|---|---|---|
| 待机 IDLE | 默认 | FPS 数字 + 帧率折线 + 内存条 + TPS/延迟；下方小胶囊轮播坐标/群系/时间/速度等 |
| 挖掘 MINING | 长按左键破坏方块 | 方块名 + 物品图标 + 进度条 + **剩余耗时估算**（按进度变化率外推） |
| 使用 USE | 长按右键 | 食物/药水/弓/盾/钓鱼等统一进度 + 物品图标 |

### 信息条

胶囊下方可轮播或固定显示：**今日游戏时长、坐标与朝向、生态群系、维度、游戏内时间、天气与光照、移动速度(km/h)、实体数、粒子数、当前所处结构名**。

### 灵动焦点（左右两个圆形小窗）

需要长时间关注的信息，先在胶囊主体上提示数秒，随后自动并入焦点，用圆环持续显示进度；按住呼出键可随时把某个焦点收回来查看详情。当前支持的焦点源：

| 焦点 | 说明 |
|---|---|
| 唱片 / 背景音乐 | 曲名与播放进度；可选联动 Tritium Music |
| 昼夜更替 | 关键时间点（日出/日落/午夜）倒计时 |
| 装备耐久 | 低于阈值时持续显示 |
| 长效药水 | 剩余时间 |
| 饥饿 / 氧气 | 生存关键指标 |
| **死亡掉落物回收** | 死亡后掉落物的 5 分钟消失倒计时；**玩家跑回死亡点 8 格内自动取消** |
| **被标记** | 发光效果（被骷髅/光谱箭标记）暴露位置时的剩余时间 |
| **僵尸村民治愈** | 治愈进度环 |

### 通知中心

覆盖场景：背包已满、装备耐久告急、药水效果即将结束、聊天被 @、进度达成、方块即将用尽、**掉落物即将消失预警、系统告警、玩家进出服务器、宠物意外/拴绳断裂**。支持**紧急分级音效**与**历史回看**（保留最近 12 条）。

### 环境与状态感知

低血量（心跳动画）、氧气不足、燃烧、饥饿、攻击充能、鞘翅飞行速度、下界传送门、跳过夜晚、死亡重生、深层矿物带预警、**TNT/苦力怕引爆倒计时、坠落落地伤害预测、载具实时速度、村民交易补货倒计时、物品冷却环、药水效果队列、经验升级进度、准星生物/容器信息、方块信息弹窗、死亡点回溯**。

### 联动与外观

- **可选联动 Tritium Music**：展开时用专辑封面取主色并铺模糊背景；未安装时本模组照常运行。
- **四套皮肤**：纯黑毛玻璃 / 半透明白 / 原版风格 / 霓虹。
- **板块自定义配色**：岛屿、信息条、通知、方块弹窗、焦点、音乐六个板块可各自指定强调色（也可跟随皮肤）。
- 弹簧（带轻微过冲）驱动宽高与展合；内容切换淡入淡出；告警态心跳缩放；位置、缩放、不透明度、动画速度全部可调。

---

## 操作

| 键位 | 功能 |
|---|---|
| `O` | 显示 / 隐藏 |
| `I` | 手动展开完整仪表盘（FPS/TPS/延迟/折线/内存/坐标） |
| `U` | 循环切换皮肤 |
| `K` | 打开设置界面 |
| `J` | 打开拖动定位界面 |
| `N` | 死亡点回溯开关 |
| `B` | 呼出最高优先级的灵动焦点 |
| `G` / `H` | 呼出左 / 右焦点 |

命令：`/island toggle | expand | theme [名称] | pos <x> <y> | reset | reload | info`

---

## 编译

**看 [BUILD.md](BUILD.md)，那是手把手教程**（装 JDK 17、IDEA 导入、国内加速、报错对照表都在里面）。

快速版：需要 **JDK 17**，然后

```
gradlew build         # 产物 build/libs/dynamicisland-1.0.0.jar
gradlew runClient     # 直接启动带模组的客户端
```

Windows 用户也可以直接双击 `build.bat`。首次构建约需 10~30 分钟（要下载并反编译 Minecraft）。

---

## 目录结构

```
src/main/java/com/dynamicisland/
├── DynamicIsland.java        主类：注册配置、按键、设置界面入口
├── Config.java               ForgeConfigSpec 配置 + 静态热缓存 + bake()/save()
├── client/
│   ├── IslandRenderer.java   HUD 绘制（胶囊 / 信息条 / 通知 / 焦点 / 弹窗）
│   ├── ProgressTracker.java  挖掘·使用·状态感知·焦点的状态仲裁中枢
│   ├── FocusOrb.java         灵动焦点的优先级仲裁与绘制
│   ├── IslandStatus.java     单帧显示快照
│   ├── Metrics.java          性能与环境数据采集
│   ├── Notifier.java         通知队列、分级音效与历史
│   ├── MusicCard.java        唱片 / BGM 识别
│   ├── TritiumLink.java      (反射) 联动 Tritium Music
│   ├── CoverTint.java        封面取主色
│   ├── ExtrasRenderer.java   胶囊下方附加层（死亡点/冷却/效果/经验）
│   ├── *Watch.java           各焦点源与状态源（Drop/Mark/Cure/Blast/Fall/Ride/…）
│   ├── RenderUtil.java       圆角矩形 / 圆环 / 折线 / UV 裁剪贴图
│   ├── Theme.java            四套皮肤
│   ├── Anim.java             弹簧与缓动
│   ├── IslandScreen.java     设置界面
│   ├── DragScreen.java       透明拖动定位
│   └── ClientEvents.java     事件总线与 tick 调度
├── command/IslandCommand.java
└── mixin/                    Mixin 只读访问器 + 成就拦截
```

---

## 二次开发

**完整维护手册见 [DEVNOTES.md](DEVNOTES.md)**，含总体架构、模块速查、两段式焦点模型、Mixin 清单、运行时序、渲染分层、反射降级策略、扩展指南与常见坑排查。

速览：

- **加一个新焦点**：复制 `MarkWatch` 为模板 → `FocusOrb.Kind/priority/bringBack` 各加一行 → `ProgressTracker.tryFocusStage` 登记 → `ClientEvents.onTick` 加 tick。
- **加一条通知**：`Notifier.push(标题, 副标题, 图标, 强调色, 去重键, 冷却秒 [, Level.URGENT])`。
- **加一套皮肤**：`Theme` 枚举加一行，配置界面与命令自动收录。
- **加一个状态**：`IslandStatus.Kind` 加枚举 → `ProgressTracker.update()` 按优先级插入 `tryXxx()` → 返回 title/sub/progress/icon/accent，渲染层无需改动。

---

## 兼容与降级

- 破坏进度、FPS、传送门 tick、村民补货、掉落物 age 通过 Mixin 读取原版私有字段；若字段名在所用映射表中不同，Mixin 会**静默降级**（不会崩游戏），对应形态不显示，其它功能照常。日志里搜索 `Mixin apply failed` 可确认。
- 结构名、Tritium 联动、聊天事件字段名三处使用反射，失败自动跳过。
- 通知的"成就"依赖 `ToastComponentMixin`；失效时仅少一类通知。
- F3 调试屏幕打开时默认隐藏（可关）。
- 圆角关闭后退化为矩形填充，极低端机可提帧。

##此模组由D老师协助开发