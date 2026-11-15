# 固件开发者指南

通过自己控制的来源向 FM-1 Flasher 用户提供固件。可以公开发布，也可以通过自己的服务只允许已获许可的用户下载。

[准备来源](#_2) · [提交集成提议](https://github.com/ekim-pl/android-fm1-flasher/issues/new?template=firmware-source.md)

用户无需向项目提交申请即可自行添加来源。加入内置来源需要单独审核，并不保证被接受。

## 加入 Awesome FM-1

要将固件提交到 [Awesome FM-1 社区目录](https://github.com/ekim-pl/awesome-fm1)，请先 Fork 仓库，添加 `submissions/project-name.md`，再提交拉取请求（PR）。请填写项目名称、简介、作者、公开的 HTTPS 固件包或仓库地址、已知版本，以及硬件兼容性证据；未知时填写“unknown”。维护者会审核提案，并协助补全目录条目后再发布。

固件文件仍由发布者托管；目录只保存元数据和链接。私有来源无需提交到 Awesome FM-1。

[向 Awesome FM-1 提交项目](https://github.com/ekim-pl/awesome-fm1#contributing)

## 准备来源

Android 应用支持三种方式：

1. **GitHub 仓库：**将 `.fwsc` 文件作为发行版附件发布，并提供 `https://github.com/OWNER/REPO`。应用包含已发布的正式版和预发行版，忽略草稿及仅提交在分支中的文件。此适配器不支持 GitHub Enterprise 或其他 Git 服务。
2. **直接包地址：**提供一个 `.fwsc` 文件的 HTTPS 地址。检查来源时应用会读取并验证该文件。
3. **JSON 目录：**通过 HTTPS 提供 Hub 格式的目录和包文件，可发布多个包及其描述和兼容性信息。

将以下占位值替换为实际信息。`sha256` 是完整 `.fwsc` 文件的小写 SHA-256；`product` 必须匹配包中的标识。请核实实际硬件和文件字节对应的芯片；`unknown` 会阻止安装，直到确认兼容性。

```json
{
  "updated": "2026-01-01",
  "firmwares": [{
    "id": "example-1", "name": "Example firmware", "author": "Example author",
    "version": "1.0", "what": "Example release",
    "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
    "product": "FM-1_001", "chip": "unknown",
    "pkg": "https://firmware.example.com/releases/example.fwsc"
  }]
}
```

示例散列只是占位符，不是可用的固件校验值。ID 应保持唯一且稳定。发布者负责提供准确的兼容性和许可条款。散列可以检测字节不匹配，但无法验证同时控制目录和散列的发布者身份。

用户打开**设置 → 固件来源**，添加类型和地址，按需配置认证，保存，选择**立即检查**并启用来源。JSON 来源需要批准包及图片的 URL 范围。GitHub 和直接包来源检查后，在**编辑 → 包兼容性**中按包散列确认兼容性；未知兼容性会阻止安装。检查间隔为 24–168 小时；更换凭据或完成 OAuth 授权后可以再次检查。

## 私有 GitHub 仓库

在私有仓库中，将 `.fwsc` 文件作为已发布的 GitHub Release 附件提供。如果不希望接收者读取项目源码，请使用不含源码的独立私有分发仓库。

1. 打开**设置 → 固件来源**，添加 **GitHub 仓库**类型的来源，并输入 `https://github.com/OWNER/REPO`。
2. 选择 **Bearer** 认证，输入自己拥有仓库读取权限的令牌，不加 `Bearer` 前缀。
3. 保存来源，选择**立即检查**，然后启用来源。
4. 安装前打开**编辑 → 包兼容性**，确认每个包与设备存储芯片类型的兼容性。

每位接收者都需要自己的 GitHub 账户、仓库访问权和令牌。请使用可用的最小访问权限。不要共享所有者令牌，也不要在 issue 中公开凭据。仓库读取权限允许访问的不只是发行版二进制文件。添加私有来源无需提交到 Awesome FM-1。

私有仓库不可访问时，GitHub 可能返回 HTTP 404。请检查仓库地址、账户访问权、令牌权限及有效期，以及组织可能要求的批准。撤销仓库访问权后，已经下载的固件仍可离线使用。

## 认证与许可访问

来源表单支持 HTTP Basic、Bearer、API 密钥请求头和 OAuth 2.0。对于个人账户，如服务支持，使用带 PKCE S256 的 OAuth Authorization Code 和公共客户端。配置客户端 ID、scope、授权地址和令牌地址，并准确注册以下回调地址：

以下回调地址适用于 Android 0.9.4-experimental.27 及更高版本。0.9.4-experimental.26 及更早版本使用来源设置中显示的地址。请在用户升级或重新授权前注册新地址。升级到构建 28 后，必须重新开始在构建 26 或更早版本中发起的登录。

```text
pl.ekim.fm1-flasher://oauth/callback
```

保存来源并选择 **Authorize OAuth 2.0**。如果服务提供刷新令牌，应用会刷新过期令牌。也支持需要客户端密钥的 Client Credentials；不要在分发的应用中嵌入共享密钥。应用不内置通用 OAuth 客户端。输入的密钥通过 Android Keystore 加密。

你的服务器负责账户、付款、许可验证和访问决定，可以只向有权用户提供目录和包。凭据只发往配置来源的 origin，跨 origin 重定向时会移除，包括到 CDN 的重定向。请从同一 origin 提供受保护的包，或使用范围适当且短期有效的下载地址。令牌 POST 重定向会被拒绝。URL 必须使用 HTTPS，并符合应用的 URL 和包范围限制。

**这只能控制下载，不能控制已下载文件的使用。** FM-1 Flasher 支持离线安装，不提供 DRM、设备绑定许可或撤销已下载文件。来源检查失败后可能保留缓存目录；离线时无法得知包已被撤回。

## 集成清单

- 在支持的硬件上验证 `.fwsc`，记录芯片兼容性、版本和许可条款。
- 提供散列正确的实际包，并用目标凭据测试来源配置、下载和安装。
- 测试过期和已撤销凭据、HTTP 401/403/404、重定向及离线行为。不要在文档或 issue 中发布真实凭据。
- [提交集成 issue](https://github.com/ekim-pl/android-fm1-flasher/issues/new?template=firmware-source.md)以获得帮助或提议内置来源；公开 issue 不得包含私有地址或密钥。

GitHub Pages 只托管静态文档；认证和许可服务需单独运行。请参阅[完整技术指南](documentation.md)。
