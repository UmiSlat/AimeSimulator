# AimeSimulator 发布流程

本文供仓库维护者使用，记录 GitHub Actions、APK 固定签名与版本发布步骤。普通安装和使用说明见项目根目录的 [`README.md`](../README.md)。

## 工作流

- `Android CI`：在 `main` push 或手动触发时运行单元测试、lint、debug APK 构建、KernelSU 模块打包与产物检查，并保留测试 APK Artifact。
- `Android Release`：在推送与 `versionName` 一致的 `v版本号` 标签时构建 release APK，验证 APK 签名与 SHA-256，并创建或更新对应的 GitHub Release。

实验分支和实验 APK 不进入托管构建或 GitHub Release。Actions 中的 debug Artifact 只用于安装测试；正式分发使用 `Android Release` 生成的固定签名 APK。

## Repository Secrets

在仓库 `Settings > Secrets and variables > Actions` 中配置：

| Secret | 内容 |
| --- | --- |
| `ANDROID_SIGNING_KEY` | JKS/PKCS12 签名库文件的 Base64 内容 |
| `ANDROID_KEY_STORE_PASSWORD` | 签名库密码 |
| `ANDROID_KEY_ALIAS` | 签名密钥别名 |
| `ANDROID_KEY_PASSWORD` | 签名密钥密码 |

签名库必须在 GitHub 之外安全备份。丢失签名密钥或密码后，将无法为现有安装生成可直接覆盖升级的 APK。不要将签名库、密码或 Secrets 写入仓库、构建日志和问题报告。

PowerShell 可使用以下命令编码签名库：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("aimesimulator-release.p12")) | Set-Clipboard
```

## 创建版本

1. 更新 `app/build.gradle` 中的 `versionCode` 与 `versionName`。
2. 在本地完成单元测试、lint、APK 构建、模块打包和产物检查。
3. 提交并推送 `main`，确认 `Android CI` 成功。
4. 创建与 `versionName` 完全一致的标签，例如：

```bash
git tag v2.2.5
git push origin v2.2.5
```

工作流会拒绝版本标签不匹配、签名 Secrets 缺失或 APK 签名校验失败的发布。发布完成后，应从 GitHub Release 重新下载 APK，并核对工作流生成的 SHA-256 与签名证书。
