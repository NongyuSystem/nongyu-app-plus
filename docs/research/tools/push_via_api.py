#!/usr/bin/env python3
"""GitHub Git Data API 推送工具（适用于 github.com 不可达、api.github.com 可达）。

用法：
    $env:PYTHONIOENCODING='utf-8'; python docs/research/tools/push_via_api.py

原理：递归拉取远端与本地的文件清单，比对 blob sha，把差异作为新的 tree
提交上去，最后移动分支指针。不依赖本地与远端共享 git 历史。
"""
import sys
import json
import base64
import subprocess

REPO = "NongyuSystem/nongyu-app-plus"
WORKDIR = r"D:\IT\program\web-program\nongyu-system\apps\nongyu-app-plus"
BRANCHES = ["master", "feat/skin-decoupling-and-perf"]


def run(cmd):
    r = subprocess.run(cmd, cwd=WORKDIR, capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    if r.returncode != 0:
        print("CMD FAILED:", cmd)
        print(r.stderr[-600:])
        raise SystemExit(1)
    return r.stdout.strip()


def gh(args, payload=None):
    cmd = ["gh", "api"] + args
    if payload is None:
        return json.loads(run(cmd))
    r = subprocess.run(cmd + ["--input", "-"], cwd=WORKDIR,
                       input=json.dumps(payload).encode("utf-8"),
                       capture_output=True)
    if r.returncode != 0:
        print("API FAILED:", args)
        print(r.stderr.decode("utf-8", "replace")[-800:])
        raise SystemExit(1)
    return json.loads(r.stdout.decode("utf-8"))


def collect_remote_files(tree_sha):
    files = {}
    stack = [(tree_sha, "")]
    while stack:
        sha, prefix = stack.pop()
        data = gh(["repos/%s/git/trees/%s" % (REPO, sha)])
        for item in data.get("tree", []):
            path = (prefix + "/" + item["path"]) if prefix else item["path"]
            if item["type"] == "tree":
                stack.append((item["sha"], path))
            elif item["type"] == "blob":
                files[path] = item["sha"]
    return files


def local_blobs():
    # core.quotepath=false：否则中文路径被八进制转义，open() 会 Errno 22
    out = run(["git", "-c", "core.quotepath=false", "ls-tree", "-r", "HEAD"])
    files = {}
    for line in out.splitlines():
        meta, path = line.split("\t", 1)
        mode, otype, sha = meta.split()
        if otype == "blob":
            files[path] = sha
    return files


def main():
    remote_head = gh(["repos/%s/branches/master" % REPO])["commit"]["sha"]
    remote_tree = gh(["repos/%s/git/commits/%s" % (REPO, remote_head)])["tree"]["sha"]
    local_head = run(["git", "rev-parse", "HEAD"])
    local_tree = run(["git", "rev-parse", "HEAD^{tree}"])
    print("remote commit=%s tree=%s" % (remote_head[:7], remote_tree[:7]))
    print("local  commit=%s tree=%s" % (local_head[:7], local_tree[:7]))

    if remote_tree == local_tree:
        print("tree 已一致，无需推送")
        return 0

    remote_files = collect_remote_files(remote_tree)
    local_files = local_blobs()
    to_upload = [p for p, s in local_files.items() if remote_files.get(p) != s]
    to_delete = [p for p in remote_files if p not in local_files]
    print("需上传=%d  需删除=%d" % (len(to_upload), len(to_delete)))

    items = []
    for path in to_upload:
        # 关键：从 git 对象库取，不读工作区（工作区是 CRLF，仓库是 LF）
        raw = subprocess.run(["git", "cat-file", "blob", local_files[path]],
                             cwd=WORKDIR, capture_output=True)
        if raw.returncode != 0:
            print("  取 blob 失败: %s" % path)
            raise SystemExit(1)
        content = base64.b64encode(raw.stdout).decode("ascii")
        blob = gh(["--method", "POST", "repos/%s/git/blobs" % REPO],
                  {"content": content, "encoding": "base64"})
        items.append({"path": path, "mode": "100644", "type": "blob", "sha": blob["sha"]})
        print("  up   %s" % path)
    for path in to_delete:
        items.append({"path": path, "mode": "100644", "type": "blob", "sha": None})
        print("  del  %s" % path)

    tree = gh(["--method", "POST", "repos/%s/git/trees" % REPO],
              {"base_tree": remote_tree, "tree": items})
    print("new tree=%s" % tree["sha"][:7])

    msg = run(["git", "log", "-1", "--format=%B", local_head]).rstrip()
    commit = gh(["--method", "POST", "repos/%s/git/commits" % REPO], {
        "message": msg,
        "tree": tree["sha"],
        "parents": [remote_head],
        "author": {
            "name": run(["git", "log", "-1", "--format=%an", local_head]),
            "email": run(["git", "log", "-1", "--format=%ae", local_head]),
            "date": run(["git", "log", "-1", "--format=%aI", local_head]),
        },
    })
    print("new commit=%s" % commit["sha"][:7])

    for br in BRANCHES:
        try:
            gh(["--method", "PATCH", "repos/%s/git/refs/heads/%s" % (REPO, br)],
               {"sha": commit["sha"], "force": False})
            print("%s -> %s" % (br, commit["sha"][:7]))
        except SystemExit:
            print("%s 更新失败（可忽略）" % br)

    check = gh(["repos/%s/git/commits/%s" % (REPO, commit["sha"])])["tree"]["sha"]
    print("校验 tree: 远端=%s 本地=%s -> %s" %
          (check[:7], local_tree[:7], "一致 OK" if check == local_tree else "不一致!!"))
    print("DONE")
    return 0


if __name__ == "__main__":
    sys.exit(main())
