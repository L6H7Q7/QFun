# QFun 定制版 v5.3

## v5.4 红包试验分支

此分支用于验证同一领取流程处理非群聊红包，尚未确认私聊实际领取成功。稳定版本 v5.3 继续保留。

- 替换自动领取入口，只判断红包消息，不再要求群聊；保留实际消息、联系人和会话类型，不伪造群聊消息。
- 移除白名单群聊判断和设置项。关键词过滤、延迟、最低平均金额及自动回复配置保留。
- 开发阶段保留群聊、私聊／其他非群聊两个独立范围开关，默认开启，受原有总开关控制。稳定后再评估合并。
- 默认勾选普通／拼手气红包和口令红包。移除语音选项，运行时也拒绝旧配置中的语音类型。
- 仍调用官方模块原有 `RedPacketHelper`，预领取和领取请求构造不变，包括写死的群聊参数。这是本次需要验证的假设，不代表已适配私聊协议。
- 非群聊的结果通知不查询群名及群成员资料，避免通知逻辑阻断流程。成功后的自动回复仍使用原会话和原配置，设置页明确展示这一行为。
- 增加进程内重复消息去重；不自动重试领取。

入口仍为 **QQ 设置 → QFun → 红包 → 自动领取红包**，点击项目打开试验版配置；点击「查看日志」可查看并复制最近日志。

独立日志通常位于：`/sdcard/Android/data/com.tencent.mobileqq/files/QFun/log/redpacket-trial.log`。同时输出 `QFunRedPacket` logcat 标签及 NPatch 模块日志。单文件约 512 KB 后轮转一份备份。日志仅包含本地跟踪序号、会话类型、过滤原因、流程阶段、数值状态码和异常类型，不记录红包标识、认证密钥、消息标题或回复内容。

主要事件：`RECEIVED`、`SKIP`、`DISPATCH`、`TRIAL_PARAMETERS`、`STAGE_ENTER`、`STAGE_WAIT`、`STAGE_RETURN`、`STAGE_THROWN`、`SUCCESS`、`FINISHED_WITHOUT_SUCCESS`、`NO_SUCCESS_WITHIN_90S`。`STAGE_WAIT` 表示协程等待，不代表失败；只有领取助手执行成功回调时记录 `SUCCESS`。90 秒提示只表示期间未观察到成功，不等于服务器判定失败。

扩展在 QFun 接入宿主真实 ClassLoader 后安装，避免过早反射红包助手依赖类。规则检查合计 96 项通过。实际红包收发结果需要另外验证。

基于 [oneQAQone/QFun](https://github.com/oneQAQone/QFun) 和官方 QFun 1.3.4 模块制作，保留上游作者信息和 GPL-3.0 许可。这是非官方定制分支。

## 功能

- QQ 9.2.27 单击分享卡片打开对应应用，失败时回退原点击流程；支持 Bilibili、PiliPlus、小红书、酷安、知乎及短链。
- QFun 右上角菜单增加「卡片跳转设置」「启动画面设置」。可编辑优先包名，Bilibili 默认包含 `com.example.piliplus`、`tv.danmaku.bili`、`com.bilibili.app.in`、`com.bstar.intl` 等。
- 默认 QQ 企鹅启动图，支持导入图片、图片配文字、纯文字和恢复默认。默认企鹅图片取自腾讯签名 QQ 9.3.75，仅复用日/夜图片，QQ 宿主仍为 9.2.27。
- 屏蔽模块自身的自动更新提醒及打赏界面。
- 缺少配置时默认启用：消息复读、防撤回、闪照破解、屏蔽艾特全体、记住上次查看位置、查看被封禁群聊、自动领取红包、伪装处于非多窗口模式、上传 APK 重命名、下载重定向；净化分类的 11 项；其他分类现有的屏蔽热更新、跳过扫码等待、移除二维码检查、液态玻璃底栏。
- 红包配置的默认自动回复为 `xxlb`，保留上游默认领取类型、延时和白名单。**已保存的开关及红包配置优先，升级不会强制改写。**

## Android 17 与加载入口

### v5.3 界面精简

- 点击聊天表情按钮，直接显示收藏表情；隐藏底部分类栏。按照面板类型 `4` 识别收藏分类，过滤其他分类页并禁止左右切换分类，收藏数据和收藏页的编辑入口保留。
- 侧边栏业务列表仅保留收藏、钱包、相册，按这一顺序排列；从列表数据中过滤，移除无用分隔行。顶部切换为紧凑资料卡，去掉大资料卡中的 QQ 秀与附加区域。底部设置和夜间模式保留；其他布局中唯一的设置入口也保留。
- 默认启用。入口：**QQ 设置 → QFun → 右上角菜单 → 界面精简设置**，可分别关闭表情和侧边栏精简，保存后重启 QQ。
- 仅针对 QQ 9.2.27。业务 ID：`d_favorite`、`d_qqwallet`、`d_album`。目标类包含 `QQSettingMeMenuPanelPartV3`、`QQSettingMeProfileBigCardPart`、`EmoticonPanelTabSortHelper`、`BasePanelView`。不使用全局 View/TextView 拦截。
- 无收藏分类或未识别出所需业务 ID 时保留原面板，避免空白界面。用户确认 Android 17 真机的两项效果正常；启动连续采样进程保持一致。新增 15 项界面数据规则检查，总计 75 项规则检查通过。

旧 LSPatch 在测试设备中发生 native 空指针崩溃。换用用户提供的 TIM 安装包中实际使用的 NPatch 1.0.5 / 639 运行库后，QQ 可启动，用户确认卡片跳转生效。不同来源的同版本 NPatch 二进制并不一定一致。

NPatch 优先选择 QFun 的 modern 入口，会跳过额外的 legacy 定制入口。本分支将 `META-INF/xposed/module.prop` 的 `targetApiVersion` 设为 93，使框架选择 legacy 管线，执行 QFun 原入口及 `CardClickEntry`。没有移除原版 QFun 代码。

v5.1 的启动与卡片跳转已在 Android 17 真机验证。v5.2 增加默认配置，规则检查通过；红包自动领取和回复未进行实际收发验证。v5.3 在真机通过启动检查，用户确认表情和侧边栏精简正常。首次安装框架初始化可能出现启动超时，测试设备在重启应用后进入 QQ；不承诺所有系统版本均适配。

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
