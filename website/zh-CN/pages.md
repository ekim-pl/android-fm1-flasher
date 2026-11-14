# GitHub Pages 项目网站

网站、文档和隐私政策从本仓库的源文件构建。每个页面均提供英语、波兰语和简体中文版本，包括导航、发布指南和许可证译文。**Polski**、**English** 和 **简体中文** 链接会切换到同一页面的其他语言版本。

## 首次部署

1. 将项目推送到 GitHub，包括 `.github/workflows/pages.yml`、`website/` 和 `scripts/build_site.py`。
2. 在 **Settings → Pages → Build and deployment → Source** 中选择 **GitHub Actions**。
3. 在 **Actions → GitHub Pages → Run workflow** 中选择默认分支。此后对该分支的更改会自动部署。
4. 已发布网站为 [ekim-pl.github.io/android-fm1-flasher](https://ekim-pl.github.io/android-fm1-flasher/zh-CN/)。分叉仓库通常使用 `https://LOGIN.github.io/REPOSITORY/`；请查看部署摘要或 **Settings → Pages**。

如果 `github-pages` 环境限制可部署的分支，请允许项目默认分支。工作流也构建拉取请求和其他分支，但只从默认分支部署。它无需应用 secrets 或 Google Play 访问权限。

## 语言与稳定地址

- `en/`：英语网站；为保留既有链接，网站根地址也提供英语版本。
- `pl/`：完整的波兰语网站。
- `zh-CN/`：完整的简体中文网站。

每种语言均包含首页、技术指南、应用简介、隐私政策、许可证、发布材料、Google Play 设置指南及网站部署指南。语言切换保留正在阅读的页面。导航、无障碍标签、标题、描述和页脚均使用所选语言。

现有链接（例如 `documentation.html`、`about/pl.html` 和 `privacy/en/`）继续有效。公开政策地址 `privacy/pl/`、`privacy/en/` 和 `privacy/zh-CN/` 仍可用于 Play Console。

## 本地构建

在项目根目录运行：

```sh
python3 -m venv .venv-pages
.venv-pages/bin/pip install -r website/requirements.txt
.venv-pages/bin/python scripts/build_site.py
.venv-pages/bin/python scripts/check_site.py
```

打开 `build/site/index.html`。相对路径支持 Pages 上的仓库路径前缀。构建结果仅包含公开文档和网站资源。

CI 从 `GITHUB_SERVER_URL`、`GITHUB_REPOSITORY` 和 `GITHUB_REF_NAME` 读取源代码链接。本地设置相同变量即可加入 GitHub 源代码和发布版本链接。

## 编辑内容

- `README.md`、`website/index.md` 和 `website/pages.md`：英语技术指南、首页和部署指南。
- `publication/README.txt` 和 `publication/github-actions.txt`：英语发布与 Google Play 指南。
- `website/pl/` 和 `website/zh-CN/`：完整网站译本，包括许可证和隐私政策。
- `about/README*.md`：现有的应用简介译本，也用于网站。
- `PRIVACY_POLICY.md`：英语隐私政策。网站译本须与 `publication/privacy/` 中的静态副本和应用资源保持一致。
- `LICENSE.md`：原始英语许可证。网站译文便于理解，以原文为准。
- `website/style.css`：共用样式。

更改共用内容时，请更新全部三种语言。缺少页面译本会导致构建失败，不会静默回退到英语。链接检查器会验证页面语言、语言切换目标、文件及锚点。

仓库文档使用英语。应用简介、应用资源和商店资料有意保留全部受支持语言。

## 与 F-Droid 仓库共同部署

每个 GitHub 仓库在 Pages 上只有一个活动网站部署。如果加入 F-Droid 发布，应在上传 Pages 构建产物前，将 `fdroid/repo/` 加入 `build/site/`。网站和 APK 仓库必须一同部署：仅部署 APK 的工作流会替换网站，仅部署网站又会删除 APK 仓库。目前工作流只发布网站和文档。
