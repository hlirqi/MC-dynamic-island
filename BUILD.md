# 编译教程 · 从零到能进游戏

> 这份文档假设你**从没用过 Gradle**。按步骤走即可，全程只需点鼠标 + 复制粘贴几条命令。  
> 目标产物：`build/libs/dynamicisland-1.0.0.jar`，丢进 `.minecraft/mods` 就能用。

---

## 零、先确认你的清单

| 需要  | 版本                              | 说明                                    |
| --- | ------------------------------- | ------------------------------------- |
| JDK | **17**（硬性要求）                    | 1.20.1 的 Forge 只认 17，高了低了都会报错         |
| 构建器 | IntelliJ IDEA（推荐）或 Gradle 8.1.1 | IDEA 社区版免费，够用                         |
| 网络  | 能访问外网                           | 首次构建要下载约 1.5GB（Forge + Minecraft 反编译） |
| 磁盘  | 留 10GB                          | 构建缓存不小                                |

**关于游戏本体**：你不必事先装 Forge。用 `runClient` 启动的话，Gradle 会自动准备一套含模组的测试环境。想直接玩就自己装好 Forge 1.20.1-47.1.3，再用 `build` 出 jar。

---

## 一、安装 JDK 17

### Windows

1. 打开 <https://adoptium.net/temurin/releases/?version=17>
2. Operating System 选 **Windows**，Architecture 选 **x64**，Package 选 **JDK**
3. 下载 `.msi` 安装包，双击运行
4. **关键几步**（安装向导里）：
   - 勾选 ☑ `Set JAVA_HOME variable`
   - 勾选 ☑ `JavaSoft (Oracle) registry keys`
   - 其他一路 Next
5. 装完**重启一次命令行窗口**（已打开的窗口不认新的环境变量）

验证：打开 PowerShell 或 CMD，输入

```powershell
java -version
```

看到类似下面的输出就对了（小版本号 17.0.x 任意）：

```
openjdk version "17.0.9" 2023-10-17
OpenJDK Runtime Environment Temurin-17.0.9+9
```

如果显示 `1.8` 或 `11`，说明你装过旧版 Java 且优先级更高：

```powershell
# 临时指定（仅当前窗口有效）
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
java -version
```

永久修：Win 搜索「编辑系统环境变量」→ 环境变量 → 系统变量里新建/修改 `JAVA_HOME` 指向 JDK 17 目录，并把 `%JAVA_HOME%\bin` 移到 `Path` 的**最上面**。

### macOS / Linux

```bash
# macOS（Homebrew）
brew install temurin17

# Ubuntu / Debian
sudo apt install openjdk-17-jdk

# 验证
java -version
```

---

## 二、方式 A：用 IntelliJ IDEA（推荐，坑最少）

### 1. 装 IDEA

<https://www.jetbrains.com/idea/download/> 下载 **Community（社区版）**，免费。

### 2. 打开工程

- 启动 IDEA → `Open` → 选中 `dynamic-island` 这个**文件夹本身**（不要选子目录）
- Trust Project 选 **Trust**

### 3. 设置 JDK 17

`File → Project Structure → Project`：

- SDK：下拉 → `Add SDK → Download JDK...` → Version 选 **17**，Vendor 选 **Eclipse Temurin** → 自动下载
- Language level：`17`

点 OK。

### 4. 等 Gradle 同步

IDEA 右下角会开始转圈，状态栏显示 `Gradle sync`。**第一次要 10~30 分钟**，它在下载 Forge、Mojang 映射表、反编译 Minecraft。

这段时间：

- 保持联网，不要关 IDEA
- 下方 `Build` 面板可以看到进度日志
- 卡住不动超过 10 分钟再看「第五节 常见报错」

同步完成后，左侧项目树里的 `com.dynamicisland` 包名应该是正常黑色（不是红色）。

### 5. 编译

右侧边栏点 **Gradle** 图标（大象头），展开：

```
dynamicisland → Tasks → build → build
```

双击 `build`。

或者直接在 IDEA 底部 `Terminal` 里输：

```bash
gradlew build
```

成功的话，`Build` 面板最后几行会出现 `BUILD SUCCESSFUL`。

### 6. 生成 gradlew（做一次就行）

同步完成后，在 Gradle 面板展开：

```
dynamicisland → Tasks → build → wrapper
```

双击 `wrapper`。工程根目录会生成 `gradlew.bat`、`gradlew` 和 `gradle/wrapper/gradle-wrapper.jar`。

**有了它，以后不用开 IDEA，双击 `build.bat` 就能编译。**

### 7. 直接启动游戏（可选）

想边改边测，运行：

```
dynamicisland → Tasks → forgegradle runs → runClient
```

会弹出一个独立的 Minecraft 客户端，模组已装好。进去按 `O`/`I`/`U`/`J` 就能看到效果。

> 如果 Gradle 面板里找不到 `runClient`，先在 Terminal 里执行一次 `gradlew genIntellijRuns`，  
> 再回 Gradle 面板刷新（左上角 ⟳）就能看到了。

---

## 三、方式 B：纯命令行

适合不想装 IDEA 的人。

### 1. 装 Gradle 8.1.1

<https://services.gradle.org/distributions/gradle-8.1.1-bin.zip> 下载，解压到比如 `C:\Gradle\gradle-8.1.1`，把 `C:\Gradle\gradle-8.1.1\bin` 加进 `Path`。

验证：`gradle -version` 能显示版本。

### 2. 编译

在 `dynamic-island` 文件夹里打开命令行：

```powershell
# 打包
gradle build

# 或者启动客户端
gradle runClient
```

**如果上面第「二-6」步已经生成了 gradlew，就直接用它**（它会自动下载匹配版本的 Gradle，省事）：

```powershell
gradlew.bat build
```

### 3. 一键脚本（更省事）

工程里已经给你写好：

- Windows：双击 **`build.bat`**
- macOS/Linux：`bash build.sh`

脚本会自动检查 JDK 版本、找不到 17 会直接提示你，然后让你选 `build` / `runClient` / `clean` / `genSources`。

---

## 四、国内网络加速（重要）

Forge 构建要连 `maven.minecraftforge.net`、`libraries.minecraft.net`、`piston-data.mojang.com` 等，国内直连经常超时或极慢。按推荐顺序试：

### 方案 1：挂代理（最省事，强烈推荐）

开着代理工具时，把 Gradle 也走代理。在 `C:\Users\<你的用户名>\.gradle\gradle.properties`（没有就新建）里加：

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

端口改成你代理工具的实际端口（Clash 默认 7890）。**开 TUN / 全局模式效果最好。**

### 方案 2：Maven 仓库镜像

把工程里 `tools/gradle-mirror.init.gradle` 复制到：

```
C:\Users\<你的用户名>\.gradle\init.d\
```

（Linux/Mac 是 `~/.gradle/init.d/`）。它把 Maven 依赖走阿里云镜像，能显著提速。**Forge 和 Minecraft 的资源包下载不走 Maven，这个方案对那部分无效。**

### 方案 3：加大 Gradle 内存

在 `gradle.properties` 里已经写好了 `-Xmx3G`。如果构建中途报 `OutOfMemoryError`，改成 `-Xmx4G`。

---

## 五、常见报错对照表

| 报错关键信息                                                                  | 原因                      | 解决                                               |
| ----------------------------------------------------------------------- | ----------------------- | ------------------------------------------------ |
| `Unsupported class file major version 61` / `Invalid source release 17` | JDK 不是 17               | 按第一节重装或指定 JAVA_HOME                              |
| `Could not resolve net.minecraftforge:forge:1.20.1-47.1.3`              | 仓库拉不到                   | 看第四节；或删掉 `~/.gradle/caches` 重试                   |
| `Could not download ... Connection timed out`                           | 网络                      | 挂代理重试，Gradle 支持断点续传                              |
| `Java heap space` / `OutOfMemoryError`                                  | 内存不足                    | `gradle.properties` 里改成 `-Xmx4G`                 |
| 卡在 `Downloading MCP mappings` 超过 20 分钟                                  | 网络慢                     | 换代理，或换个时段                                        |
| `Mixin apply failed`                                                    | 映射表字段名对不上               | **不影响编译**，只是挖掘进度等形态不显示，别的照常                      |
| `A problem occurred evaluating settings file`                           | settings.gradle 解析失败    | 通常 Gradle 版本不对，确认是 8.1.1                         |
| `Gradle wrapper jar not found` / IDEA 提示 wrapper 缺失                     | 工程里没带 wrapper jar（体积原因） | 忽略即可，IDEA 会用自己的 Gradle；同步完成后跑一次 `wrapper` 任务就补齐了 |
| 中文注释变乱码                                                                 | 编码                      | build.gradle 已设 UTF-8；用 IDEA 打开时应选 UTF-8         |
| `BUILD FAILED` 但看不出原因                                                   | 日志太长                    | 往上翻找 **`Caused by:`** 那一行，才是真凶                   |

**把报错发给我时，请复制 `Caused by:` 开始的 10 行，我一眼就能定位。**

---

## 六、拿到 jar 之后

1. 产物在 `build/libs/dynamicisland-1.0.0.jar`
2. 确认你的游戏装了 **Forge 1.20.1-47.1.3**（或 47.x 任意版本）
3. 把 jar 复制进 `.minecraft\mods\`
4. 启动游戏

进游戏后：

| 按键  | 效果        |
| --- | --------- |
| `O` | 显示 / 隐藏   |
| `I` | 手动展开完整仪表盘 |
| `U` | 循环切皮肤     |
| `J` | 打开拖动定位界面  |

也可以输命令 `/island info` 看是否正常工作。

也可以在游戏里 `Mods` 列表找到 Dynamic Island，点 `Config` 打开设置界面。

---

## 七、改了代码怎么重新编译

**用 IDEA**：改完直接重新双击 `build`，或者运行 `runClient` 实时看效果。

**用命令行**：

```powershell
gradlew.bat build      # 重新打包
gradlew.bat runClient  # 启动游戏看效果
```

如果改了资源文件（lang、mods.toml、mixins.json）而没生效，先执行 `clean` 再 `build`。

---

## 八、需要提前说明的一点

我写代码的环境只有 JDK 11 且外网受限，所以**没能替你先跑通一次真实编译**。逻辑和结构是按 Forge 1.20.1 官方 MDK 规范写的，但第一次 `build` 仍有可能冒出个别 API 签名或字段名的小偏差。

遇到报错别慌，把 `Caused by:` 那段复制给我，我直接帮你改代码。绝大多数问题一两次就能收敛。
