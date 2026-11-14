# FM‑1 Flasher

启用 **设置 → 实验性功能** 后，将显示 MIDI 日志、闪存检测和恢复。此设置默认关闭并会保存。关闭后会停止日志并清除恢复计划；操作期间无法更改。恢复功能仍需实体硬件验证。

独立的实验性应用，用于浏览固件并通过 USB MIDI 更新 **M‑VAVE FM‑1**。

## 功能

- 搜索目录、收藏固件，离线使用已下载的固件包。
- 在目录或刷写页面选择已下载的固件。
- 安装前验证固件包的完整性与兼容性。
- 计划内重启后，仅有一台 USB 设备和一个符合条件的 MIDI 端口时自动重新连接。
- 实验性调试版本还包含只接收数据的 MIDI 记录器、闪存芯片检测和范围严格受限的 ROM 恢复；ROM 恢复尚未在实体设备上验证。
- 支持浅色、深色和系统主题，以及简体中文、英语和波兰语界面。

## 固件来源

在 **设置 → 固件来源** 中管理独立目录订阅。Firmware Hub 默认启用；[Awesome FM-1](https://github.com/ekim-pl/awesome-fm1) 默认关闭。添加 HTTPS 目录，检查内容并配置允许的固件包和图片网址。已关闭的来源不会自动请求任何数据，包括图片。立即检查可明确检查已关闭的来源，但仍遵守两次尝试至少间隔 24 小时的限制。

SQLite 固件库保留下载的固件包、收藏、选择和来源记录。删除订阅不会删除文件或已知撤回状态。每个来源拥有独立的时效、错误和下载规则；一个来源失败不会阻止其他来源更新。可按来源筛选列表。可选的后台目录检查默认关闭，不会下载或安装固件。

这些目录没有经过验证的发布者签名。SHA-256 与固件包验证仍是强制要求；兼容性未知的固件（包括初始 SHRUB 条目）无法刷写。相同产品标识不代表同一个项目。离线刷写仅使用本地文件，不访问网络，也无法发现上次检查之后发布的撤回。

目录署名：[The Firmware Hub](https://thefirmwarehub.com)，CC BY 4.0；描述和图片属于各自作者。其他目录声明各自的许可证。固件从作者处下载，不使用 Hub 的固件包镜像。

网络与后台任务：OkHttp/Okio 和 AndroidX WorkManager，[Apache 2.0](../third_party/Apache-2.0.txt)；参见[依赖项声明](../third_party/android-libraries-NOTICE.txt)。

## 更新前

Android 设备须支持 USB Host/OTG 和 MIDI。请备份设备数据，确保 USB 供电稳定，并确认目标设备和固件包。

## 隐私与许可

无需账户，无广告和分析服务。请查看[隐私政策](../PRIVACY_POLICY.md)。

联系方式：[ekim.pl@gmail.com](mailto:ekim.pl@gmail.com)。

应用采用 [MIT 许可证](../LICENSE.md)。Markdown 解析器采用 [CommonMark、BSD-2-Clause 许可证](../third_party/commonmark-LICENSE.txt)。
