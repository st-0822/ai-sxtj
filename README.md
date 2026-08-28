# 📱 AI 小手机 · Android 完整项目

> **一个运行在你自己手机上的 AI 伴侣 App。AI 扮演"他/她"，你能管控 TA、查岗、看 TA 的"手机"、语音/视频通话。**
> 架构：**单手机 + AI 模拟**，不需要第二台手机、不需要云端服务器。

---

## ✨ 功能清单

### 💬 聊天（AI 管家）
- **填了 API → 真大模型**（OpenAI / DeepSeek / 智谱 / 通义，任意 OpenAI 兼容接口），AI 扮演"他"有灵魂
- **不填 API → 本地 AI 脑兜底**（自定义规则 → 精确词 → 关键词 → 人设兜底，任何话都接得住）
- 多角色切换、人设驱动语气、自定义回复规则

### 🔍 查岗（核心）
- 📸 拍照查岗 → AI 按人设生成"他"当前状态
- 📍 定位查岗 → 真实 GPS（需授权）/ 未授权走模拟
- 🖥️ 屏幕状态 → 真实使用统计（UsageStatsManager）/ 模拟
- ⏰ 定时查岗 → AlarmManager 每日自动触发
- 📋 查岗记录存档

### 🖥️ 屏幕共享
- 实时显示"他"当前前台 App（真实/模拟）
- 今日使用时间排行（柱状图）

### 📱 管控（系统级，需授权后生效）
- 🔒 锁指定 App + 定时解锁
- ⏱️ 今日使用时间统计
- 📍 定位获取
- 🔇 禁言/勿扰模式
- 🔒 远程锁屏（DeviceAdmin）
- 👀 盯防模式（前台服务，防卸载/关权限）

### 🛒 购物 / 🥡 外卖 / 🎵 音乐 / 🎮 游戏 / 🖼️ 照片
- 全部**独立真实页面**，从桌面图标进入
- 音乐：Web Audio 合成旋律 + **支持导入本地 mp3**
- 游戏：贪吃蛇等可玩小游戏
- 照片：相册选图 / 粘贴链接，本地存储不上传

### 📞 语音/视频通话
- 语音：TTS 声线念 AI 回复（5 种声线）+ 语音条录音
- 视频：你导入的静态图当对方画面 + AI 语音

---

## 🚀 三步出 APK（全程手机可操作）

### 第 1 步：上传到 GitHub
1. 手机/电脑浏览器打开 **github.com** → 注册登录
2. **New Repository** → 名字随便（如 `ai-phone`）→ 公开 → Create
3. 把本项目的**所有文件**上传到仓库（可直接拖拽整个文件夹）

### 第 2 步：开启自动打包
1. 仓库 → **Settings** → Actions → General → 勾选 "Read and write permissions" + "Workflow permissions: Read and write"
2. 推送代码到 `main` 分支（或手动：Actions → Build APK → Run workflow）
3. 等待 5-10 分钟，看到绿色 ✓

### 第 3 步：下载安装
1. Actions → 最新 run → **Artifacts** → 下载 `AI小手机-APK`
2. 解压得到 `AI小手机-signed.apk`
3. 手机安装（首次需允许"安装未知来源应用"）
4. 打开 → 填 API（可选）→ 直接用

> ⚠️ **首次打开会请求权限**：存储、麦克风、相机、定位、通知。建议全部允许以获得完整体验。

---

## ⚙️ API 配置（强烈推荐填）

打开 App → ⚙️ 设置：

| 字段 | 填什么 |
|------|--------|
| API 地址 | `https://api.deepseek.com/v1` （DeepSeek）或 `https://api.openai.com/v1` 等 |
| API Key | `sk-xxx` （去对应平台注册获取，很便宜，DeepSeek 充值 10 块能用很久） |
| 模型 | `deepseek-chat` 或 `gpt-3.5-turbo` 等 |

> 💡 **不填也能用**（走本地 AI 脑），但填了之后 AI 的"扮演"和"查岗/管控对话"会**聪明 10 倍**。

---

## 🔐 权限说明（哪些是真的，哪些是模拟）

| 功能 | 真/模拟 | 说明 |
|------|---------|------|
| 聊天 AI 回复 | ✅ 真（填 API）/ 模拟（不填） | API 是真实大模型 |
| 锁 App / 使用统计 | ✅ 真（需 UsageStats 授权） | 授权后 Settings → 安全 → 有权限管理 |
| 定位 | ✅ 真（需定位权限） | GPS/网络定位 |
| 禁言/勿扰 | ✅ 真（需通知策略访问） | 设置 → 声音 → 勿扰 |
| 锁屏 | ✅ 真（需 DeviceAdmin 激活） | 设置 → 安全 → 设备管理员 |
| 拍照（查岗） | ⚠️ 框架已搭，需补全 Camera2 | 或用 AI 模拟照片 |
| "他的手机"数据 | 🎭 模拟（AI 按人设生成） | **这是设计如此**——"他"是 AI，数据由人设驱动 |
| 屏幕共享内容 | ⚠️ 授权后用真实使用数据，否则模拟 | UsageStatsManager |

> **设计哲学**：所有"管控/查岗"对"他"的功能，数据是 AI 按人设生成的合理内容（因为"他"是虚拟角色）。  
> 而**你手机本地的功能**（存储、真实定位、真实使用统计、真实锁屏）都是系统级的真能力。

---

## 📂 项目结构

```
AI小手机_Android/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml        # 权限声明
│       ├── assets/index.html          # ⭐ 前端 UI（HTML/CSS/JS，所有界面逻辑）
│       └── java/com/ai/phone/
│           ├── MainActivity.kt        # 主 Activity + JS↔Native 桥接
│           ├── ai/
│           │   ├── ApiClient.kt       # OpenAI 兼容 API 调用 + 本地兜底
│           │   └── PersonaEngine.kt   # 人设 + 查岗报告生成
│           ├── control/
│           │   ├── ControlEngine.kt   # 管控核心（锁App/定位/拍照/禁言/锁屏）
│           │   ├── WatchService.kt    # 盯防前台服务
│           │   └── AdminReceiver.kt   # DeviceAdmin 锁屏
│           └── call/
│               └── VoiceEngine.kt     # TTS 语音合成 + 音频播放
├── .github/workflows/build.yml        # GitHub Actions 自动打包
├── build_apk.sh                       # 兜底编译脚本
├── build.gradle / settings.gradle     # Gradle 配置
└── README.md
```

---

## ⚠️ 法律与伦理提醒

- 本 App 设计为**个人娱乐/情侣双方知情同意**使用
- "管控/查岗"功能应在**对方知晓并同意**的前提下使用
- 不要用于偷拍、偷录、未经同意的监控——这违法且伤害关系
- 本项目**不收集任何用户数据**，所有数据存本地

---

## ❓ 常见问题

**Q：GitHub Actions 构建失败？**  
A：检查仓库 Settings → Actions → Workflow permissions 是否设为 Read/Write。

**Q：装了 APK 打开白屏？**  
A：确保 `assets/index.html` 已正确打包。用 `adb logcat` 看报错。

**Q：API 填了没反应？**  
A：确认 API 地址以 `/v1` 结尾（或会自动补 `/chat/completions`），Key 正确，模型名对得上。点 ⚙️ 里的"🧪 测试 API"。

**Q：锁 App 没效果？**  
A：需激活设备管理员 + 开启无障碍服务。这是 Android 安全限制，无法绕过。

**Q：能改成情侣双方互管吗？**  
A：可以，但需要加云端（Firebase），详见进阶文档。当前版本是"你 ↔ AI"单机模式。

---

Made with ❤️ · 仅供娱乐与学习
