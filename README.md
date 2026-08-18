# FileManager

一个使用 Kotlin + Jetpack Compose 编写的轻量级 Android 文件管理器
> 此项目需要使用 Canary 频道的 Android Studio 来打开

## 功能

- 文件浏览：面包屑导航、路径记忆、多种排序（名称/大小/日期/类型）
- 文件操作：复制、剪切、粘贴（含冲突处理）、删除、重命名、新建文件/文件夹
- 长按弹出操作菜单，支持多选批量操作
- 搜索当前目录及子目录
- 图片/视频缩略图（Coil）
- 收藏常用目录，侧边栏快捷入口（下载/相机/图片等）
- 显示/隐藏隐藏文件、文件属性详情
- 分享文件到其他应用
- 支持被其他 App 调起选取文件（`GET_CONTENT` / `OPEN_DOCUMENT` / `PICK`）
- APK 通过 `io.github.huidoudour.Installer` 安装

## 技术栈

- Kotlin + Jetpack Compose (Material 3)
- MVVM (ViewModel + StateFlow)
- Coil（缩略图加载）

## 构建

```bash
./gradlew :app:assembleDebug
```

## 参考项目

- [MaterialFiles](https://github.com/zhanghai/MaterialFiles) — 功能设计参考
- [MT 管理器](https://mt2.cn/) — UI 风格参考

> 若涉及到侵权，请联系我