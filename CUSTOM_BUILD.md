# QFun 定制版 v5.2

基于 [oneQAQone/QFun](https://github.com/oneQAQone/QFun) 和官方 QFun 1.3.4 模块制作，保留上游作者信息和 GPL-3.0 许可。这是非官方定制分支。

## 功能

- QQ 9.2.27 单击分享卡片打开对应应用，失败时回退原点击流程；支持 Bilibili、PiliPlus、小红书、酷安、知乎及短链。
- QFun 右上角菜单增加「卡片跳转设置」「启动画面设置」。可编辑优先包名，Bilibili 默认包含 `com.example.piliplus`、`tv.danmaku.bili`、`com.bilibili.app.in`、`com.bstar.intl` 等。
- 默认 QQ 企鹅启动图，支持导入图片、图片配文字、纯文字和恢复默认。默认企鹅图片取自腾讯签名 QQ 9.3.75，仅复用日/夜图片，QQ 宿主仍为 9.2.27。
- 屏蔽模块自身的自动更新提醒及打赏界面。
- 缺少配置时默认启用：消息复读、防撤回、闪照破解、屏蔽艾特全体、记住上次查看位置、查看被封禁群聊、自动领取红包、伪装处于非多窗口模式、上传 APK 重命名、下载重定向；净化分类的 11 项；其他分类现有的屏蔽热更新、跳过扫码等待、移除二维码检查、液态玻璃底栏。
- 红包配置的默认自动回复为 `xxlb`，保留上游默认领取类型、延时和白名单。**已保存的开关及红包配置优先，升级不会强制改写。**

## Android 17 与加载入口

旧 LSPatch 在测试设备中发生 native 空指针崩溃。换用用户提供的 TIM 安装包中实际使用的 NPatch 1.0.5 / 639 运行库后，QQ 可启动，用户确认卡片跳转生效。不同来源的同版本 NPatch 二进制并不一定一致。

NPatch 优先选择 QFun 的 modern 入口，会跳过额外的 legacy 定制入口。本分支将 `META-INF/xposed/module.prop` 的 `targetApiVersion` 设为 93，使框架选择 legacy 管线，执行 QFun 原入口及 `CardClickEntry`。没有移除原版 QFun 代码。

v5.1 的启动与卡片跳转已在 Android 17 真机验证。v5.2 增加默认配置，已通过编译、DEX 生成、签名校验及 60 项规则检查；新增默认配置尚未完成真机验证。首次安装框架初始化可能出现启动超时，测试设备在重启应用后进入 QQ；不承诺所有系统版本均适配。

## 构建与发布方式

当前发布 APK 采用「固定官方模块 + Java 扩展 DEX」方式，**不是本仓库完整 Gradle 编译的产物**。公开源码快照与官方二进制存在差异，例如官方二进制包含红包功能；本分支只通过反射修改其默认配置，没有复制或重写该功能。完整 Gradle 工程保留为上游源码参考。

准备 Python 3.11+、JDK 17+、Android SDK（android-36 和 build-tools 36.0.0）、Xposed API 82 编译接口。

官方基底：[QFun_1.3.4.apk](https://github.com/Xposed-Modules-Repo/me.yxp.qfun/releases/download/26-1.3.4/QFun_1.3.4.apk)，SHA-256：`252dbcc75e5ea59abbb7a99258d0f4d2e6f56b945f6f121eabd126b2ff04a63c`。

```text
python custom-tools/build_module.py --base QFun_1.3.4.apk --sdk YOUR_ANDROID_SDK --java-home YOUR_JDK --xposed-api api-82.jar --out build/QFun-custom-unsigned.apk
```

脚本会编译 `app/src/main/java/me/yxp/qfun/custom/`、运行 D8、加入 classes2.dex、两条 legacy 入口、默认图片与 legacy 管线元数据。原有 classes.dex 保持逐字节一致。输出未签名模块，需要用自己的密钥通过 Android apksigner 签名（模块 minimum SDK 26）。

`custom-tools/tests` 包含链接路由、应用包名、启动图参数和默认功能规则检查。链接测试使用 org.json；其余测试可直接使用 JDK。

如需嵌入 QQ，应从未注入框架的宿主开始，使用相容 NPatch，再嵌入已签名模块。APK 的嵌套 ZIP 结构不能随意展开重写。覆盖安装宿主必须使用现有宿主的签名，先备份；不要卸载或清除应用数据。仓库不包含宿主安装包、用户数据、调试日志或签名私钥。
