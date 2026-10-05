# 移植说明：1.20.1 Forge → 26.2 NeoForge

> 源工程：`dynamic-island-1.0.1`（Minecraft 1.20.1 / Forge 47.1.3 / JDK 17）
> 本工程：Minecraft **26.2** / **NeoForge 26.2.0.87** / **JDK 25**，modId 与全部功能保持不变。

## 一、构建系统

| 项 | 旧 | 新 |
|---|---|---|
| 插件 | `net.minecraftforge.gradle` + `org.spongepowered.mixin` | `net.neoforged.moddev` 2.0.148（ModDevGradle） |
| Gradle | 8.1.1 | 9.8.0（官方源不通时可用 `mirrors.cloud.tencent.com/gradle/` 镜像） |
| JDK | 17 | 25（MDG 的 `downloadAssets` 仍固定要 21，已在 `gradle.properties` 里指向本机 JDK 21） |
| 元数据 | `META-INF/mods.toml` | `META-INF/neoforge.mods.toml`（`type = "required"/"optional"`、依赖 `neoforge`、新增 `[[mixins]]`） |
| Mixin | `dynamicisland.mixins.json` + refmap | 同文件，但 `compatibilityLevel = JAVA_25`、去掉 refmap（NeoForge 运行时就是 Mojang 名，无需再映射） |

`gradlew build` 的产物：`build/libs/dynamicisland-1.0.1-neoforge-26.2.jar`。

## 二、加载器 API

| 1.20.1 Forge | 26.2 NeoForge |
|---|---|
| `net.minecraftforge.*` | `net.neoforged.neoforge.*` / `net.neoforged.fml.*` / `net.neoforged.bus.api.*` |
| `FMLJavaModLoadingContext.get().getModEventBus()` | 构造器注入：`public DynamicIsland(IEventBus modBus, ModContainer container)` |
| `ModLoadingContext.get().registerConfig(...)` | `container.registerConfig(ModConfig.Type.CLIENT, SPEC)` |
| `ConfigScreenHandler.ConfigScreenFactory` | `IConfigScreenFactory`（`registerExtensionPoint` 两个重载有歧义，要显式声明 `Supplier<IConfigScreenFactory>`） |
| `MinecraftForge.EVENT_BUS` | `NeoForge.EVENT_BUS` |
| `FMLEnvironment.dist` | `FMLEnvironment.getDist()` |
| `net.minecraftforge.common.ForgeConfigSpec` | `net.neoforged.neoforge.common.ModConfigSpec` |
| `ForgeRegistries.ITEMS` | `BuiltInRegistries.ITEM`（`get(id)` 返回 `Optional`，改用 `getValue(id)`） |
| `TickEvent.ClientTickEvent`（phase 判断） | `net.neoforged.neoforge.client.event.ClientTickEvent.Pre` |
| `RenderGuiEvent.Post#getPartialTick(): float` | `getPartialTick(): DeltaTracker` → `getGameTimeDeltaPartialTick(true)` |
| `PlaySoundEvent#getSound().getLocation()` | `getSound().getIdentifier()` |

## 三、渲染：从「立即绘制」改成「提取 + 提交状态」

26.x 的 GUI 已经改成 **extract → 提交 `GuiElementRenderState` → 统一渲染**：

1. `net.minecraft.client.gui.GuiGraphics` 被 `GuiGraphicsExtractor` 取代；没有 `PoseStack`，只有 `org.joml.Matrix3x2fStack`（`pushMatrix/popMatrix/translate(x,y)/scale(x,y)`）。
2. `drawString/drawCenteredString/renderItem/hLine` → `text/centeredText/item/horizontalLine`。
3. **不能在渲染回调里直接 `RenderSystem.setShader + BufferBuilder` 画东西**（那样会画在 HUD 之下）。自定义几何必须实现 `GuiElementRenderState` 并调用
   `gg.submitGuiElementRenderState(state)`。

因此 `RenderUtil` 整体重写：

- 圆角矩形 / 圆形贴图 / 箭头 → **三角形扇**（`gui_fan_color` / `gui_fan_tex`）；
- 圆环 / 帧率折线 → **三角形带**（`gui_strip_color`）。
  它们都不是 QUADS，所以在 `RegisterRenderPipelinesEvent` 里注册了三条自定义管线
  （`dynamicisland:pipeline/gui_fan_color`、`gui_fan_tex`、`gui_strip_color`）。
- **三条管线都必须 `.withCull(false)`**：GUI 管线默认 `cull = true`，而扇形/带形在 y 轴朝下的
  屏幕坐标里绕序是逆时针，会被判成背面整片剔除 —— 症状是「文字（四边形）在、图形全没有」。
- 同一个图层（stratum）内元素会**先按管线排序**再画，跨管线想保证前后关系要显式
  `gg.nextStratum()`（`RenderUtil.layer(gg)`）。胶囊底 / 封面 / 内容 / 信息条 / 焦点 / 通知
  各占一层。

### 26.x 的两个渲染陷阱（实测踩到）

1. **`extractBackground()` 一帧只能调一次**：`Screen.extractRenderStateWithTooltipAndSubtitles()`
   已经替你调过了（内部做一次性高斯模糊），子类再调一次会崩
   `Can only blur once per frame` —— 打开模组配置界面就闪退就是这个原因。
2. **`enableScissor(x0,y0,x1,y1)` 的参数会在内部再乘一次当前 pose**
   （`ScreenRectangle.transformAxisAligned(this.pose)`），而 1.20.1 那版直接吃屏幕像素坐标。
   沿用旧写法（把局部坐标换算成屏幕坐标再传）会被二次变换，裁剪框整个飞到屏幕外，
   框内的文字/图标/进度条全部消失。**正确做法是传局部坐标**，让框架自己变换。

## 四、Minecraft API 变化（本模组用到的）

| 旧 | 新 |
|---|---|
| `ResourceLocation` | `Identifier`（`new Identifier(a,b)` 私有 → `Identifier.fromNamespaceAndPath(a,b)` / `withDefaultNamespace(p)`） |
| `ResourceKey#location()` | `identifier()` |
| `Level#getDayTime()` | 26.x 引入 Clock 体系，**客户端读 `getDefaultClockTime()` 恒为 0**；应走 `ClockManager`：`lv.registryAccess().get(WorldClocks.OVERWORLD)` → `lv.clockManager().getTotalTicks(clock)`，最后才兜底游戏刻（见 `Metrics#dayTicks`） |
| `Level#getMinBuildHeight()` | `getMinY()` |
| `MobEffectInstance#getEffect()` 返回 `MobEffect` | 返回 `Holder<MobEffect>`（用 `.value()`） |
| `MobEffects.DAMAGE_BOOST` | `MobEffects.STRENGTH` |
| `AreaEffectCloud#getColor()` | 颜色在 `getParticle()`（`ColorParticleOption`）里 |
| `AreaEffectCloud#getDuration()` | **客户端永远返回 -1**：`duration` 是纯服务端字段，同步的只有 `DATA_RADIUS`/`DATA_WAITING`/`DATA_PARTICLE`。剩余时间改用「半径反推」（见下） |
| `ItemCooldowns#isOnCooldown(Item)` / `getCooldownPercent(Item, f)` | 参数改为 `ItemStack` |
| `ItemStack#getUseDuration()` | `getUseDuration(LivingEntity)` |
| `Inventory#items` / `#armor` / `#selected` / `#getSelected()` | `getNonEquipmentItems()` / `getItemBySlot(slot)` / `getSelectedSlot()` / `getSelectedItem()` |
| `TamableAnimal#getOwnerUUID()` | `getOwner()`（可能为 null） |
| `RecordItem#getLengthInTicks()` | 唱片时长改到 `JukeboxPlayable` 组件：`JukeboxSong.fromStack(stack)` → `lengthInSeconds()` |
| `JukeboxBlockEntity#isRecordPlaying()` / `getItem(0)` | `getSongPlayer().isPlaying()` / `getTheItem()` |
| `Minecraft#screen` / `setScreen(...)` | `mc.gui.screen()` / `mc.gui.setScreen(...)` |
| `mc.options.renderDebug` | `mc.getDebugOverlay().showDebugScreen()` |
| `Screen#render(...)` / `renderBackground(gg)` | `extractRenderState(gg, mx, my, pt)` / `extractBackground(gg, mx, my, pt)` |
| `mouseClicked/dragged/released(x, y, button)` | `mouseClicked(MouseButtonEvent, boolean)` 等，坐标从 `event.x()/y()` 取 |
| `keyPressed(int, int, int)` | `keyPressed(KeyEvent)`，`event.input()` 取键码 |
| `AbstractWidget#renderWidget` | `extractContents(GuiGraphicsExtractor, mx, my, a)`（按钮底用 `extractDefaultSprite(gg)`） |
| `AbstractButton#onPress()` | `onPress(InputWithModifiers)`；`onClick(MouseButtonEvent, boolean)`、`onDrag(MouseButtonEvent, dx, dy)` |
| `KeyMapping(name, type, code, "字符串分类")` | 分类变成 `KeyMapping.Category` 对象，由 `RegisterKeyMappingsEvent#registerCategory` 注册（按键实例也随之改为在事件里创建） |
| `AbstractTexture#getId()` | GPU 抽象化后没有 id；就绪判断改用 `getTextureView() != null`（`RenderUtil.textureReady`） |
| 类迁移 | `Villager` → `world.entity.npc.villager`、`ZombieVillager` → `monster.zombie`、`Boat/Minecart` → `vehicle.boat` / `vehicle.minecart`、`ToastComponent` → `ToastManager` |

## 五、Mixin 的调整

- 删除 `ToastComponentMixin`：NeoForge 有 `ToastAddEvent`，直接在 `ClientEvents` 里监听即可（更稳，不依赖内部类名）。
- 删除 `EntityAccessor`：传送门计时搬到公开的 `Entity#portalProcess`（`PortalProcessor#getPortalTime()`），不再需要 mixin。
- `AdvancementToastAccessor` 的返回类型改为 `AdvancementHolder`（`advancement.value().display()` 是 `Optional`）。
- 其余 accessor 字段名在 26.2 上已核对无误：`Minecraft.fps`、`MultiPlayerGameMode.destroyProgress/destroyBlockPos/isDestroying`、`ItemEntity.age`、`ExperienceOrb.age`、`Endermite.life`、`Villager.lastRestockGameTime/numberOfRestocksToday`、`AdvancementToast.advancement`。

## 六、效果云剩余时间：改用「半径反推」

云的 `duration` 没有同步到客户端（见上表），直接读只会拿到 -1，于是「不限时」和
「30 秒的滞留药水」在客户端长得一模一样。解决办法是换个可用的量：

原版滞留药水自己就是 `radiusPerTick = -radius / duration`（`ThrownLingeringPotion`），
也就是**让半径线性缩到 0**；而半径是同步字段。所以在客户端就地测量：

```java
decay = (上一刻半径 − 当前半径) / 间隔 tick 数     // 实测 0.005 = 3.0 / 600
剩余 tick = 当前半径 / decay                        // 实测反推 599 ≈ 真实 600
进度      = 当前半径 / 生效时的起始半径
```

- 半径在缩 → 正常倒计时；
- 半径不缩（`/summon` 出来的、`RadiusPerTick = 0`）→ 才真的是不限时，显示 `∞`；
- `waitTime` 同样没同步（只有布尔 `isWaiting()`），用「上一次实测到的等待时长」预测下一次，
  首次见面按原版药水的 10 tick 兜底。

观测状态放在 `IdentityHashMap<AreaEffectCloud, Sample>` 里，每 tick 清掉已死亡的云。
本机跑下来反推值 599 / 真实 600，误差 1 tick。

## 七、功能降级（唯一一处）

- `CoverTint`（从专辑封面取主色给胶囊染色）：26.x 起纹理完全由 GPU 管理，旧版 `glGetTexImage` 回读不存在。
  现在只对 CPU 端仍留有像素的 `DynamicTexture` 生效；取不到就返回 -1，胶囊回退到皮肤强调色。
  Tritium Music 联动的曲名/进度/封面显示不受影响。

## 八、实测情况

已在 `./gradlew runClient` 里反复启动客户端验证：

- mod 正常加载，6 个 Mixin accessor 全部 applied，无 shader 编译错误、无运行时报错；
- 灵动岛胶囊底/封面/进度条/圆环/折线/文字均正常渲染；
- 配置界面可正常打开（滑块、颜色按钮、拖动排序都正常）；
- 昼夜更替焦点能按 Overworld 时钟倒计时（实测 `clock=14244` 读数正常）；
- 效果云倒计时正常递减，`/summon` 出来的不限时云显示 `∞`。

另外：MDG 的 `downloadAssets` 固定需要 JDK 21（编译/运行本身用 JDK 25），
`gradle.properties` 里的 `org.gradle.java.installations.paths` 指的就是本机那份 JDK 21；换机器删掉这行即可。
