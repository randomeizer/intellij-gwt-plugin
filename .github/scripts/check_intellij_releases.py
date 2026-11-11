"""Open one compatibility issue per new stable IntelliJ IDEA major/minor release line."""

import argparse
import json
import os
import re
import sys
from pathlib import Path
from urllib.request import Request, urlopen

import release_monitor


RELEASES_URL = "https://data.services.jetbrains.com/products/releases?code=IIU&type=release"
DOWNLOADS_URL = "https://www.jetbrains.com/idea/download/other/"
PROPERTIES_PATH = Path(__file__).resolve().parents[2] / "gradle.properties"


def version_key(version):
    if not isinstance(version, str) or not re.fullmatch(r"[0-9]{4}\.[0-9]+(?:\.[0-9]+)*", version):
        raise ValueError(f"Expected a stable IntelliJ year.minor[.patch] version, got {version!r}")
    return tuple(int(part) for part in version.split("."))


def release_line(version):
    return ".".join(str(part) for part in version_key(version)[:2])


def read_baseline(properties):
    matches = re.findall(r"^\s*platformVersion\s*=\s*(\S+)\s*$", properties, re.MULTILINE)
    if len(matches) != 1:
        raise ValueError("Expected exactly one platformVersion in gradle.properties")
    return release_line(matches[0])


def parse_releases(feed):
    if not isinstance(feed, dict) or not isinstance(feed.get("IIU"), list):
        raise ValueError("Missing IntelliJ IDEA (IIU) release list; the release feed format may have changed")
    releases = {}
    for entry in feed["IIU"]:
        if not isinstance(entry, dict):
            raise ValueError("Invalid IntelliJ release entry")
        if entry.get("type") != "release":
            continue
        version = entry.get("version", "")
        # Old pre-year-based versions and preview/hotfix suffixes do not define a new release line.
        if not isinstance(version, str) or not re.fullmatch(r"[0-9]{4}\.[0-9]+(?:\.[0-9]+)*", version):
            continue
        line = release_line(version)
        # Prefer the original release's notes; still detect a line if only patches remain in the feed.
        if line not in releases or version_key(version) < version_key(releases[line]["version"]):
            releases[line] = entry
    if not releases:
        raise ValueError("No stable IntelliJ release lines found; the release feed format may have changed")
    return releases


def issue_title(line):
    return f"Add support for IntelliJ IDEA {line}"


def issue_marker(line):
    return f"<!-- intellij-release-monitor:{line} -->"


def pending_releases(releases, baseline, issues):
    baseline_key = version_key(release_line(baseline))
    return [line for line in sorted(releases, key=version_key)
            if version_key(line) > baseline_key
            and not any(issue_marker(line) in (issue.get("body") or "")
                        or issue.get("title", "").strip().casefold() == issue_title(line).casefold()
                        for issue in issues if "pull_request" not in issue)]


def create_issues(repository, token, releases, baseline):
    issues = release_monitor.list_issues(repository, token)
    pending = pending_releases(releases, baseline, issues)
    for line in pending:
        entry = releases[line]
        # Avoid inserting arbitrary feed content into the ticket; only link JetBrains HTTPS URLs.
        notes = entry.get("notesLink", "")
        if not isinstance(notes, str) or not re.fullmatch(
                r"https://(?:youtrack|www|blog)\.jetbrains\.com/[A-Za-z0-9_./#?=&%+-]+", notes):
            notes = DOWNLOADS_URL
        body = f"""{issue_marker(line)}
IntelliJ IDEA {line} has a stable release. The plugin currently targets IntelliJ IDEA {baseline}.

[Release notes]({notes}) | [IntelliJ IDEA release archive]({DOWNLOADS_URL})

This ticket tracks the {line} release line, including all patch releases. EAP, beta, and RC builds
do not trigger this monitor. Detection alone does not establish incompatibility.

- [ ] Review IntelliJ Platform API changes, bundled plugin changes, and Java requirements.
- [ ] Update `platformVersion` in `gradle.properties` after compatibility work and review
      any intervening stable release lines. Review `pluginSinceBuild` and plugin versioning as needed.
- [ ] Run `./gradlew check buildPlugin`, including free-mode and optional integration coverage.
- [ ] Review the Plugin Verifier results for the new platform and update compatibility documentation.

Created automatically by the daily IntelliJ release check. Open and closed issues prevent duplicate tickets.
"""
        issue = release_monitor.github_request(repository, token, "issues", {"title": issue_title(line), "body": body})
        print(f"Created {issue['html_url']}")
    if not pending:
        print("No new untracked stable IntelliJ IDEA release lines.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="List newer release lines without accessing GitHub")
    parser.add_argument("--releases-file", type=Path, help="Read a saved JSON feed instead of fetching it")
    args = parser.parse_args()
    baseline = read_baseline(PROPERTIES_PATH.read_text(encoding="utf-8"))
    if args.releases_file:
        feed = json.loads(args.releases_file.read_text(encoding="utf-8"))
    else:
        request = Request(RELEASES_URL, headers={"User-Agent": "intellij-gwt-release-monitor"})
        with urlopen(request, timeout=30) as response:
            feed = json.load(response)
    releases = parse_releases(feed)
    print(f"Found {len(releases)} stable IntelliJ IDEA release lines; current target: {baseline}.")
    if version_key(max(releases, key=version_key)) < version_key(baseline):
        raise ValueError("Release feed is older than the current target; refusing an incomplete result")
    if args.dry_run:
        pending = pending_releases(releases, baseline, [])
        print(f"Newer release lines (existing issues not checked): {', '.join(pending) or 'none'}")
        return
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    token = os.environ.get("GH_TOKEN", "")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) or not token:
        raise ValueError("GITHUB_REPOSITORY and GH_TOKEN are required unless using --dry-run")
    create_issues(repository, token, releases, baseline)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError) as error:
        print(f"IntelliJ release check failed: {error}", file=sys.stderr)
        sys.exit(1)
