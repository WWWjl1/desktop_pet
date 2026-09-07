# 桌面宠物 (DesktopPet)

一个基于你提供的角色图生成的 **安卓悬浮窗桌面宠物**：
透明、无边框、始终置顶的小家伙，可以拖动、点击互动、长按菜单，还能调整大小。

## 功能
- 透明无边框悬浮窗，默认始终置顶
- 用手指**拖动**即可四处移动；拖动时角色会朝移动方向"小跑"
- **点击**角色：轮流触发「跳跃」和「压扁回弹」等互动，并随机弹出简短的趣味中文气泡
- 气泡显示在角色上方，**不会遮挡角色**
- **长按**弹出菜单：
  - 陪我聊聊天（连着说几句俏皮话）
  - 摸摸头（摸摸+爱心气泡）
  - 喂吃的（咀嚼动画+干饭气泡）
  - 让她走路（自动来回散步）
  - 让她睡觉（打盹，点击可唤醒）
  - 调整大小（小→中→大→超大循环）
  - 置顶开关（关闭后收进状态栏通知，点击通知唤回）
  - 退出程序
- 位置与大小会记住，下次启动自动恢复

## 如何拿到 APK（GitHub 编译，推荐）
本项目用 GitHub Actions 在云端编译，你不需要在本机装任何 Android 工具。

### 方式一：把仓库推到 GitHub，让 Actions 自动出包
1. 在 GitHub 新建一个仓库（建议 **Private 私有**，避免公开你的角色照片），例如 `desktop-pet`。
2. 在本项目目录里执行：
   ```bash
   git init
   git add .
   git commit -m "desktop pet"
   git remote add origin https://github.com/<你的用户名>/desktop-pet.git
   git branch -M main
   git push -u origin main
   ```
3. 到 GitHub 仓库页面 → **Actions** 标签，等待 `Build APK` 跑完。
4. 打开该次运行的 **Artifacts**，下载 `deskpet-debug-apk`，解压得到 `app-debug.apk`。
5. 点右上角 **Settings → Actions → General → Workflow permissions**，确认允许读写，便于直接发布 Release。

> 也可以给仓库打标签 `git tag v1.0 && git push --tags`，Actions 会自动把 APK 挂到 GitHub Release。

### 方式二：本机直接编译（可选）
需要 JDK 17 与 Android SDK。配置好后运行：
```bash
./gradlew assembleDebug
```
生成的 APK 在 `app/build/outputs/apk/debug/app-debug.apk`。

## 安装到手机
1. 把 `app-debug.apk` 传到安卓手机（云盘/USB/微信文件均可）。
2. 点击 APK 安装（需先在系统里允许"安装未知来源应用"）。
3. 打开「桌面宠物」。
4. 按提示**授予悬浮窗权限**（不同手机路径不同：设置 → 应用 → 桌面宠物 → 悬浮窗/显示在其他应用上层）。
5. 点「开启桌宠」，回到桌面就能看到小家伙了。
   - 建议同时在系统设置里开启「自启动」并关闭电池优化，避免后台被杀。

## 目录说明
- `app/src/main/java/com/deskpet/app/` —— 主界面 + 悬浮窗服务代码
- `app/src/main/res/drawable-nodpi/character.png` —— 已抠图的角色透明 PNG
- `.github/workflows/android-build.yml` —— GitHub Actions 构建配置

## 备注
- 源码只提交了抠图后的 `character.png`；原始照片（`source.jpg`）已加入 `.gitignore`，不会上传。
- 若你不希望角色图进入公开仓库，请一定使用 **私有仓库**。
