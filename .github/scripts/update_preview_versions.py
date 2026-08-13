#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查并更新项目中的预览版本依赖。

覆盖的组件与数据源：
- Gradle 发行版（nightly 快照）        -> gradle/wrapper/gradle-wrapper.properties
- Compose BOM（compose-bom-alpha 线）    -> gradle/libs.versions.toml 的 composeBom
- compose-ui-test（alpha/beta/rc）      -> gradle/libs.versions.toml 的 compose-ui-test

注：AGP 不自动检查，随 Android Studio Canary 升级时手动更新。

仅使用 Python 标准库，无第三方依赖。
有更新时修改本地文件并通过 GITHUB_OUTPUT 输出 PR 信息，由调用方提交变更。
"""

import json
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
TOML_PATH = os.path.join(REPO_ROOT, "gradle", "libs.versions.toml")
WRAPPER_PATH = os.path.join(REPO_ROOT, "gradle", "wrapper", "gradle-wrapper.properties")

GRADLE_NIGHTLY_API = "https://services.gradle.org/versions/nightly"
COMPOSE_BOM_ALPHA_METADATA_URL = (
    "https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom-alpha/maven-metadata.xml"
)
COMPOSE_UI_TEST_METADATA_URL = (
    "https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-test-junit4/maven-metadata.xml"
)

# 预览后缀排序：alpha < beta < rc < 正式版
SUFFIX_RANK = {"alpha": 0, "beta": 1, "rc": 2}
PREVIEW_SUFFIX_RE = re.compile(r"-(alpha|beta|rc)\d*$")


def fetch_text(url):
    """GET 请求并返回 UTF-8 文本。"""
    request = urllib.request.Request(url, headers={"User-Agent": "update-preview-versions-bot"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read().decode("utf-8")


def safe_fetch(url):
    """GET 请求，失败时打印警告并返回 None。"""
    try:
        return fetch_text(url)
    except Exception as error:
        print(f"Failed to fetch {url}: {error}", file=sys.stderr)
        return None


def version_key(version):
    """将版本号解析为可比较的元组：数字段 + 后缀序（alpha < beta < rc < 正式）。"""
    base, _, suffix = version.partition("-")
    parts = [int(part) for part in re.findall(r"\d+", base)]
    match = re.match(r"(alpha|beta|rc)(\d*)", suffix)
    if match:
        return tuple(parts + [SUFFIX_RANK[match.group(1)], int(match.group(2) or 0)])
    return tuple(parts + [3, 0])


def gradle_version_key(version):
    """解析 Gradle nightly 版本（如 9.8.0-20260813012148+0000）为可比较的元组。"""
    base, _, build_time = version.partition("-")
    return tuple(int(part) for part in re.findall(r"\d+", base)) + (build_time,)


def fetch_maven_versions(url):
    """从 Google Maven 的 maven-metadata.xml 中提取全部版本号。"""
    text = safe_fetch(url)
    if text is None:
        return []
    root = ET.fromstring(text)
    return [node.text for node in root.findall("./versioning/versions/version")]


def latest_preview(versions):
    """返回最新预览版本（alpha/beta/rc），无预览版本时返回 None。"""
    previews = [v for v in versions if PREVIEW_SUFFIX_RE.search(v)]
    return max(previews, key=version_key) if previews else None


def fetch_latest_gradle_nightly():
    """返回最新可用的 Gradle nightly 版本，构建损坏或请求失败时返回 None。"""
    text = safe_fetch(GRADLE_NIGHTLY_API)
    if text is None:
        return None
    data = json.loads(text)
    if data.get("broken"):
        print("Gradle nightly build is marked broken, skipped.", file=sys.stderr)
        return None
    return data.get("version")


def read_file_text(path):
    """读取文件文本并检测行尾，返回 (text, newline)。"""
    with open(path, "rb") as handle:
        raw = handle.read()
    return raw.decode("utf-8"), "\r\n" if b"\r\n" in raw else "\n"


def write_file_text(path, text, newline):
    """以指定行尾写回文件。"""
    with open(path, "w", encoding="utf-8", newline=newline) as handle:
        handle.write(text)


def current_version(toml_text, key):
    """从 toml 文本中提取指定键的版本值。"""
    match = re.search(rf'^{key}\s*=\s*"([^"]+)"', toml_text, re.M)
    return match.group(1) if match else None


def current_gradle_version(wrapper_text):
    """从 gradle-wrapper.properties 中提取当前 Gradle 快照版本。"""
    match = re.search(
        r"distributionUrl=https\\://services\.gradle\.org/distributions-snapshots/gradle-([^/\\]+)-all\.zip",
        wrapper_text,
    )
    return match.group(1) if match else None


def check_updates(toml_text, wrapper_text):
    """对比各组件当前版本与最新预览版本，返回更新列表。"""
    updates = []

    nightly = fetch_latest_gradle_nightly()
    current_gradle = current_gradle_version(wrapper_text)
    if nightly and current_gradle and gradle_version_key(nightly) > gradle_version_key(current_gradle):
        updates.append({"component": "Gradle (nightly)", "old": current_gradle, "new": nightly})

    bom_versions = fetch_maven_versions(COMPOSE_BOM_ALPHA_METADATA_URL)
    latest_bom = max(bom_versions, key=version_key) if bom_versions else None
    current_bom = current_version(toml_text, "composeBom")
    if latest_bom and current_bom and version_key(latest_bom) > version_key(current_bom):
        updates.append({"component": "Compose BOM", "old": current_bom, "new": latest_bom})

    latest_ui_test = latest_preview(fetch_maven_versions(COMPOSE_UI_TEST_METADATA_URL))
    current_ui_test = current_version(toml_text, "compose-ui-test")
    if latest_ui_test and current_ui_test and version_key(latest_ui_test) > version_key(current_ui_test):
        updates.append({"component": "compose-ui-test", "old": current_ui_test, "new": latest_ui_test})

    return updates


def apply_updates(toml_text, wrapper_text, updates):
    """将更新写回 toml 与 wrapper 文本，返回修改后的文本。"""
    for update in updates:
        component = update["component"]
        new_version = update["new"]
        if component == "Gradle (nightly)":
            wrapper_text = re.sub(
                r"(distributionUrl=https\\://services\.gradle\.org/distributions-snapshots/gradle-)[^/\\]+(-all\.zip)",
                rf"\g<1>{new_version}\g<2>",
                wrapper_text,
                count=1,
            )
        elif component == "Compose BOM":
            toml_text = re.sub(
                r'^(composeBom\s*=\s*)"[^"]*"', rf'\g<1>"{new_version}"', toml_text, count=1, flags=re.M
            )
            # 切换 BOM 到 alpha 线时需同步修改 artifact 名称
            toml_text = re.sub(
                r'(androidx-compose-bom\s*=\s*\{[^}]*?name\s*=\s*)"[^"]*"',
                r'\g<1>"compose-bom-alpha"',
                toml_text,
                count=1,
            )
        elif component == "compose-ui-test":
            toml_text = re.sub(
                r'^(compose-ui-test\s*=\s*)"[^"]*"', rf'\g<1>"{new_version}"', toml_text, count=1, flags=re.M
            )
    return toml_text, wrapper_text


def set_github_output(name, value):
    """向 GITHUB_OUTPUT 写入输出变量（GitHub Actions 环境）。"""
    path = os.environ.get("GITHUB_OUTPUT")
    if not path:
        return
    with open(path, "a", encoding="utf-8") as handle:
        handle.write(f"{name}<<EOF\n{value}\nEOF\n")


def main():
    toml_text, toml_newline = read_file_text(TOML_PATH)
    wrapper_text, wrapper_newline = read_file_text(WRAPPER_PATH)

    updates = check_updates(toml_text, wrapper_text)
    if not updates:
        print("All preview dependencies are up to date.")
        set_github_output("updated", "false")
        return

    toml_text, wrapper_text = apply_updates(toml_text, wrapper_text, updates)
    write_file_text(TOML_PATH, toml_text, toml_newline)
    write_file_text(WRAPPER_PATH, wrapper_text, wrapper_newline)

    body_lines = [f"- {u['component']}: {u['old']} -> {u['new']}" for u in updates]
    summary = ", ".join(f"{u['component']} {u['new']}" for u in updates)

    print("Updates applied:")
    print("\n".join(body_lines))

    set_github_output("updated", "true")
    set_github_output("commit_message", f"chore(deps): update preview versions ({summary})")
    set_github_output("pr_title", f"chore(deps): update preview versions ({summary})")
    set_github_output("pr_body", "\n".join(body_lines))


if __name__ == "__main__":
    main()
