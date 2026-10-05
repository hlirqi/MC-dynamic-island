# Git 仓库描述参考

创建仓库时按需复制以下内容。

---

## 1. 仓库名（Repository name）

```
dynamic-island
```

（若已被占用，可用 `dynamicisland` 或 `mc-dynamic-island`）

---

## 2. About · 简介（一句话，建议 120 字符内，GitHub 会直接展示在仓库标题下方）

**中文版**

```
把 iPhone 灵动岛搬进 Minecraft 的 Forge 1.20.1 客户端模组：常驻胶囊看板，事件自动展开，长驻信息缩为灵动焦点。
```

**英文版（推荐，GitHub 上更通用）**

```
An iPhone Dynamic Island-style HUD for Minecraft Forge 1.20.1 — a live capsule at the top of the screen that expands on events, with focus orbs for long-running info.
```

**超短版（字符受限时用）**

```
iPhone 灵动岛风格的 Minecraft Forge 1.20.1 HUD 模组
```

---

## 3. Website / 主页（可选，填产物下载页或留空）

```
（留空，或填你的 GitHub Releases 页面）
```

---

## 4. Topics · 标签（GitHub 仓库右上角齿轮里添加，建议以下 6~10 个）

```
minecraft
forge
minecraft-mod
minecraft-forge
hud
dynamic-island
1-20-1
java
mod
gui
```

---

## 5. README 顶部简介（已内置于项目 README.md 第 1 行，供参考）

```markdown
# Dynamic Island · Minecraft Forge 1.20.1

把 iPhone 灵动岛搬进 Minecraft：屏幕顶部常驻一枚胶囊，平时是性能看板，有事时自动"长"成任务卡片，事完缩回；
需要长期盯着的信息会缩成两侧的圆形焦点，持续用圆环显示进度。
```

---

## 6. Release 说明模板（首次发版用）

```
标题：Dynamic Island v1.0.0

首个公开版本。

## 功能
- 胶囊三形态：待机看板 / 挖掘进度 / 使用进度
- 信息条：游戏时长、坐标朝向、群系维度、时间天气、速度、实体粒子数、当前结构
- 灵动焦点：音乐、昼夜、耐久、药水、饥饿氧气、死亡掉落回收、被标记、僵尸村民治愈
- 通知中心：背包已满、耐久告急、效果结束、掉落物消失预警、系统告警、玩家进出、宠物意外
  支持紧急分级音效与历史回看
- 环境感知：爆炸倒计时、坠落预测、载具速度、村民补货、死亡点回溯等
- 四套皮肤 + 全板块自定义配色
- 可选联动 Tritium Music（封面取色 + 模糊背景）

## 环境
- Minecraft 1.20.1
- Forge 47.1.3+（47.x 均可）
- 纯客户端模组，服务端无需安装

## 安装
把 dynamicisland-1.0.0.jar 放入 .minecraft/mods/

## 许可
MIT
```

---

## 7. 首次推送命令（参考）

```bash
git init
git add .
git commit -m "release: v1.0.0"
git branch -M main
git remote add origin https://github.com/<你的用户名>/dynamic-island.git
git push -u origin main
```
