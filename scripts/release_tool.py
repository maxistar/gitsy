#!/usr/bin/env python3
"""Validate GitSy Android release identity and localized release notes."""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = ROOT / "release-config.json"
GRADLE_PATH = ROOT / "app" / "build.gradle"
SEMVER = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
CODE = re.compile(r"(?m)^\s*versionCode\s+(\d+)\s*$")
NAME = re.compile(r'(?m)^\s*versionName\s+["\']([^"\']+)["\']\s*$')


class ReleaseError(RuntimeError):
    pass


@dataclass(frozen=True)
class Version:
    name: str
    code: int


def run(*args: str, check: bool = True) -> str:
    result = subprocess.run(args, cwd=ROOT, text=True, capture_output=True)
    if check and result.returncode:
        raise ReleaseError(result.stderr.strip() or result.stdout.strip() or f"{' '.join(args)} failed")
    return result.stdout.strip()


def config() -> dict:
    data = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    required = {"branches", "tagPrefix", "metadataRoot", "requiredLocales", "englishLocale", "placeholderMarkers", "playTrack"}
    if missing := required - data.keys():
        raise ReleaseError(f"release-config.json is missing: {', '.join(sorted(missing))}")
    return data


def parse_version(text: str) -> Version:
    codes, names = CODE.findall(text), NAME.findall(text)
    if len(codes) != 1 or len(names) != 1:
        raise ReleaseError("Gradle must contain exactly one versionCode and versionName")
    if not SEMVER.fullmatch(names[0]):
        raise ReleaseError(f"versionName must be SemVer X.Y.Z, got {names[0]!r}")
    return Version(names[0], int(codes[0]))


def version(ref: str | None = None) -> Version:
    text = GRADLE_PATH.read_text(encoding="utf-8") if ref is None else run("git", "show", f"{ref}:app/build.gradle", check=False)
    if not text:
        raise ReleaseError(f"{ref} does not contain app/build.gradle")
    return parse_version(text)


def metadata(version_value: Version | None = None) -> dict:
    value = version_value or version()
    cfg = config()
    return {"versionName": value.name, "versionCode": value.code, "tag": f'{cfg["tagPrefix"]}{value.name}', "englishNotes": str(notes(value, cfg)[cfg["englishLocale"]]), "metadataRoot": cfg["metadataRoot"], "playTrack": cfg["playTrack"]}


def notes(value: Version, cfg: dict) -> dict[str, Path]:
    root = ROOT / cfg["metadataRoot"]
    return {locale: root / locale / "changelogs" / f"{value.code}.txt" for locale in cfg["requiredLocales"]}


def validate_notes(value: Version, cfg: dict) -> None:
    failures = []
    for locale, path in notes(value, cfg).items():
        content = path.read_text(encoding="utf-8").strip() if path.is_file() else ""
        if not content:
            failures.append(f"missing or empty {path.relative_to(ROOT)}")
        elif any(marker in content for marker in cfg["placeholderMarkers"]):
            failures.append(f"placeholder remains in {path.relative_to(ROOT)}")
    if failures:
        raise ReleaseError("Invalid localized release notes:\n- " + "\n- ".join(failures))


def ref_exists(ref: str) -> bool:
    return subprocess.run(["git", "show-ref", "--verify", "--quiet", ref], cwd=ROOT).returncode == 0


def historical_versions(cfg: dict, exclude: str | None = None) -> list[Version]:
    values = []
    for tag in run("git", "tag", "--list", f'{cfg["tagPrefix"]}*').splitlines():
        if tag == exclude:
            continue
        text = run("git", "show", f"{tag}:app/build.gradle", check=False)
        if text:
            try:
                values.append(parse_version(text))
            except ReleaseError:
                pass
    return values


def validate_history(value: Version, cfg: dict, *, allow_tag: bool) -> None:
    tag = f'{cfg["tagPrefix"]}{value.name}'
    if ref_exists(f"refs/tags/{tag}") and not allow_tag:
        raise ReleaseError(f"tag already exists: {tag}")
    previous = historical_versions(cfg, tag if allow_tag else None)
    if previous and value.code <= max(item.code for item in previous):
        raise ReleaseError("versionCode must be greater than every prior release tag")


def branch() -> str:
    value = run("git", "branch", "--show-current")
    if not value:
        raise ReleaseError("a branch checkout is required")
    return value


def validate_branch(value: Version, cfg: dict) -> str:
    current, branches = branch(), cfg["branches"]
    for mode, base in (("release", branches["development"]), ("hotfix", branches["stable"])):
        expected = f'{branches[mode + "Prefix"]}{value.name}'
        if current == expected:
            if subprocess.run(["git", "merge-base", "--is-ancestor", f"origin/{base}", "HEAD"], cwd=ROOT).returncode:
                raise ReleaseError(f"{current} is not based on {base}")
            return mode
    raise ReleaseError(f"expected release/{value.name} or hotfix/{value.name}, got {current}")


def candidate(head: str, base: str, commit: str, merged: bool, cfg: dict) -> dict:
    if not merged:
        raise ReleaseError("pull request was not merged")
    if base != cfg["branches"]["stable"]:
        raise ReleaseError(f"release PR must target {cfg['branches']['stable']}")
    value = version(commit)
    valid = [f'{cfg["branches"][kind + "Prefix"]}{value.name}' for kind in ("release", "hotfix")]
    if head not in valid:
        raise ReleaseError(f"branch {head} does not match version {value.name}")
    parents = run("git", "rev-list", "--parents", "-n", "1", commit).split()
    if len(parents) < 3:
        raise ReleaseError("candidate must be a merge commit")
    validate_history(value, cfg, allow_tag=False)
    validate_notes(value, cfg)
    return {**metadata(value), "mergeCommit": commit, "headBranch": head}


def validate_tag(tag: str, cfg: dict) -> dict:
    if run("git", "cat-file", "-t", tag, check=False) != "tag":
        raise ReleaseError(f"{tag} must be an annotated tag")
    value = version(tag)
    if tag != f'{cfg["tagPrefix"]}{value.name}':
        raise ReleaseError(f"tag {tag} does not match version {value.name}")
    validate_history(value, cfg, allow_tag=True)
    validate_notes(value, cfg)
    return metadata(value)


def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("metadata")
    sub.add_parser("prepare")
    validate = sub.add_parser("validate"); validate.add_argument("--tag"); validate.add_argument("--skip-branch", action="store_true"); validate.add_argument("--allow-existing-tag", action="store_true")
    cand = sub.add_parser("candidate"); cand.add_argument("--head-branch", required=True); cand.add_argument("--base-branch", required=True); cand.add_argument("--merge-commit", required=True); cand.add_argument("--merged", required=True)
    args = parser.parse_args(); cfg = config()
    if args.command == "metadata": output = metadata()
    elif args.command == "prepare":
        value = version()
        for path in notes(value, cfg).values():
            path.parent.mkdir(parents=True, exist_ok=True)
            if not path.exists(): path.write_text(f'{cfg["placeholderMarkers"][0]} ({value.name})\n', encoding="utf-8")
        output = metadata(value)
    elif args.command == "candidate": output = candidate(args.head_branch, args.base_branch, args.merge_commit, args.merged.lower() == "true", cfg)
    elif args.tag: output = validate_tag(args.tag, cfg)
    else:
        value = version(); validate_history(value, cfg, allow_tag=args.allow_existing_tag); validate_notes(value, cfg)
        if not args.skip_branch: validate_branch(value, cfg)
        output = metadata(value)
    print(json.dumps(output))


if __name__ == "__main__":
    try: main()
    except ReleaseError as error:
        print(f"release validation failed: {error}", file=sys.stderr); sys.exit(2)
