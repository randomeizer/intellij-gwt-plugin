"""Open one compatibility issue per stable GWT release newer than our verified baseline."""

import argparse
import json
import os
import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.request import Request, urlopen

import release_monitor


RELEASE_NOTES_URL = "https://www.gwtproject.org/release-notes.html"
CONFIG_PATH = Path(__file__).resolve().parents[1] / "gwt-release-monitor.json"


def version_key(version):
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        raise ValueError(f"Expected a stable major.minor.patch version, got {version!r}")
    return tuple(int(part) for part in version.split("."))


class ReleaseParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.in_heading = False
        self.anchor = None
        self.text = []
        self.releases = {}

    def handle_starttag(self, tag, attrs):
        if tag == "h2":
            self.in_heading = True
            self.anchor = None
            self.text = []
        if self.in_heading and tag == "a":
            anchor = dict(attrs).get("id", "") or ""
            if re.fullmatch(r"Release_Notes_[0-9]+_[0-9]+_[0-9]+", anchor):
                self.anchor = anchor

    def handle_data(self, data):
        if self.in_heading:
            self.text.append(data)

    def handle_endtag(self, tag):
        if tag == "h2":
            match = re.fullmatch(r"Release Notes for ([0-9]+\.[0-9]+\.[0-9]+)",
                                 " ".join("".join(self.text).split()))
            if match and self.anchor == "Release_Notes_" + match[1].replace(".", "_"):
                self.releases[match[1]] = f"{RELEASE_NOTES_URL}#{self.anchor}"
            self.in_heading = False


def parse_releases(html):
    parser = ReleaseParser()
    parser.feed(html)
    if not parser.releases:
        raise ValueError("No stable GWT release headings found; the release notes format may have changed")
    return parser.releases


def issue_title(version):
    return f"Add support for GWT {version}"


def issue_marker(version):
    return f"<!-- gwt-release-monitor:{version} -->"


def pending_releases(releases, baseline, issues):
    baseline_key = version_key(baseline)
    return [version for version in sorted(releases, key=version_key)
            if version_key(version) > baseline_key
            and not any(issue_marker(version) in (issue.get("body") or "")
                        or issue.get("title", "").strip().casefold() == issue_title(version).casefold()
                        for issue in issues if "pull_request" not in issue)]


def create_issues(repository, token, releases, baseline):
    issues = release_monitor.list_issues(repository, token)
    pending = pending_releases(releases, baseline, issues)
    for version in pending:
        body = f"""{issue_marker(version)}
GWT {version} has been released. The plugin's latest explicitly verified GWT version is {baseline}.

[GWT {version} release notes]({releases[version]})

This release needs a compatibility review; detection alone does not establish incompatibility.

- [ ] Review compiler, DevMode/Super Dev Mode, Java requirements, SDK layout, and removed APIs.
- [ ] Validate this SDK with the plugin; update version profiles, templates, and integration code as needed.
- [ ] Add or update relevant compatibility coverage and documentation.
- [ ] Update `.github/gwt-release-monitor.json` once compatibility has been verified. Advance
      `latestVerifiedVersion` only after all intervening stable releases have also been reviewed.

Created automatically by the daily GWT release check. Open and closed issues prevent duplicate tickets.
"""
        issue = release_monitor.github_request(repository, token, "issues", {"title": issue_title(version), "body": body})
        print(f"Created {issue['html_url']}")
    if not pending:
        print("No new untracked stable GWT releases.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="List newer releases without accessing GitHub")
    parser.add_argument("--notes-file", type=Path, help="Read saved release notes instead of fetching the page")
    args = parser.parse_args()
    baseline = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))["latestVerifiedVersion"]
    version_key(baseline)
    if args.notes_file:
        html = args.notes_file.read_text(encoding="utf-8")
    else:
        request = Request(RELEASE_NOTES_URL, headers={"User-Agent": "intellij-gwt-release-monitor"})
        with urlopen(request, timeout=30) as response:
            html = response.read().decode("utf-8")
    releases = parse_releases(html)
    print(f"Found {len(releases)} stable releases; latest verified version: {baseline}.")
    if version_key(max(releases, key=version_key)) < version_key(baseline):
        raise ValueError("Release notes are older than the verified baseline; refusing an incomplete result")
    if args.dry_run:
        pending = pending_releases(releases, baseline, [])
        print(f"Newer releases (existing issues not checked): {', '.join(pending) or 'none'}")
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
        print(f"GWT release check failed: {error}", file=sys.stderr)
        sys.exit(1)
