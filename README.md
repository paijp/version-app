# VersionApp

バージョン情報を表示するシンプルなAndroidテストアプリです。
GitHub Actionsで自動ビルドし、Releasesにて配布します。

## リリース方法

```bash
git tag v1.0.0
git push origin v1.0.0
```

タグをプッシュすると自動でビルド＆Releaseが作成されます。
