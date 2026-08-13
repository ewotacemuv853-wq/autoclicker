# 无障碍自动连点器（Android）

基于 **AccessibilityService + dispatchGesture** 的自动连点 App，**无需 Root**。支持：

- 多点序列循环点击（按顺序依次点击，跑完一轮再循环；可设轮数或无限）
- 多套方案保存 / 加载（如「抢购A」「游戏B」）
- 随机抖动防检测（每个点击点坐标加随机偏移）
- 摇一摇 / 音量键启停（免悬浮窗也能控制）
- 可拖动悬浮控制条（开始 / 停止 / 添加点）

包名：`com.example.autoclicker`
最低系统：Android 7.0（API 24，dispatchGesture 所需）

---

## 一、在电脑上编译（只需做一次）

### 方式 A：Android Studio（最简单，推荐）
1. 安装 **Android Studio**（官网下载，含 JDK 与 SDK）。
2. 打开本工程目录 `AutoClicker/`（首次会提示 Trust，确认即可；Gradle 会自动配置）。
3. 连上你的安卓手机（见下文「二、手机准备」），或新建一个模拟器。
4. 点顶部绿色 ▶ **Run**，会直接编译并安装到手机并启动。
   - 想要一个 APK 文件：菜单 **Build → Build Bundle(s) / APK(s) → Build APK(s)**，
     产物在 `app/build/outputs/apk/release/app-release.apk`。

### 方式 B：命令行（已装好 Android SDK 与 Gradle）
```bash
cd AutoClicker
./gradlew assembleDebug        # debug 版 APK
# 或出正式 AAB（需先配置签名，见 keystore.properties.example）
./gradlew bundleRelease
```

> 注意：本机（生成此工程的机器）没有 Android SDK / Gradle，无法在这里直接出 APK。
> 请在装有 Android Studio 的电脑上按上面步骤编译，或用下面的「方式 C」云端编译。

### 方式 C：GitHub Actions 零安装（推荐给没有 Android Studio 的环境）
已配好工作流 `.github/workflows/build.yml`，推到 GitHub 后自动云端编译出 debug APK：
1. 在 github.com 新建一个空仓库（如 `autoclicker`）。
2. 在工程目录执行：
   ```bash
   git remote add origin https://github.com/<你的用户名>/autoclicker.git
   git branch -M main
   git push -u origin main
   ```
   （推送时密码处用 **Personal Access Token**，GitHub 已不支持账号密码直推）
3. 打开仓库 **Actions** 标签 → 看到 `Build Debug APK` 跑绿后，点该次运行 → **Artifacts → app-debug** 下载压缩包，解压得到 `app-debug.apk`。
4. 把 APK 传到手机安装即可（无需本机任何 Android 工具）。

---

## 二、手机准备（每台手机做一次）

1. **开启开发者选项**：设置 → 关于手机 → 连续点「版本号」7 次。
2. **开启 USB 调试**：设置 → 系统 → 开发者选项 → USB 调试（用 Android Studio Run 方式时需要）。
3. **允许安装未知来源应用**：设置 → 安全 → 安装未知应用 → 允许来自「文件管理 / 浏览器」的安装（用 APK 文件方式时需要）。
4. 用数据线连电脑；首次会弹「是否允许 USB 调试」，勾选始终允许并确定。

---

## 三、使用步骤

1. 打开 App → 点「开启无障碍服务」→ 在系统设置里找到 **无障碍自动连点器** 并启用 → 返回。
2. 点「授予悬浮窗权限」→ 允许。
3. 点「添加点（点屏选位）」→ 依次在屏幕上点选目标位置（顺序即点击顺序）。
   - 列表里**单击某点**改延迟（如抢购时在「确认」前多等 800ms），**长按删除**。
4. 设循环轮数（0 = 无限）、默认延迟。
5. （可选）点「保存为方案」存成「抢购A」等，下拉可随时切换；勾选「防检测」抖动、「摇一摇/音量键」启停。
6. 屏幕上出现悬浮条：**开始** = 按序列循环连点；**添加点** = 追加点击点。
   - 已开启「摇一摇/音量键」时，无需悬浮窗也能启停。

---

## 四、发布（AAB）

`app/build.gradle` 已配好签名与 `bundle {}`，复制 `keystore.properties.example`
为 `keystore.properties` 填好密码后 `./gradlew bundleRelease`，产物为
`app/build/outputs/bundle/release/app-release.aab`，可上传 Google Play / 各渠道。

> 合规提醒：以「自动点击」为核心能力的 App 在 Google Play / 国内商店可能触及
> 无障碍 API 滥用政策被拒。建议用**企业分发 / 侧载 / 第三方商店**，并在说明中明确
> 「无障碍辅助工具」定位，降低下架风险。

---

## 项目结构

```
AutoClicker/
├─ app/
│  ├─ build.gradle                      # 模块配置（含签名 + bundle）
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/example/autoclicker/
│     │  ├─ MainActivity.java           # 权限引导 / 设置 / 方案
│     │  ├─ AutoClickService.java       # 连点核心（手势 / 抖动 / 摇一摇 / 音量键）
│     │  ├─ Prefs.java                  # 配置存储（点序列 / 方案 / 防检测）
│     │  ├─ ClickPoint.java             # 点击点模型
│     │  └─ Scheme.java                 # 方案模型
│     └─ res/...                        # 布局、字符串、无障碍配置
├─ keystore.properties.example          # 签名配置模板
└─ README.md
```
