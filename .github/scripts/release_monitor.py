"""GitHub issue access shared by the release monitors."""

import json
from urllib.request import Request, urlopen


def github_request(repository, token, endpoint, payload=None):
    request = Request(
        f"https://api.github.com/repos/{repository}/{endpoint}",
        data=json.dumps(payload).encode("utf-8") if payload is not None else None,
        headers={"Authorization": f"Bearer {token}",
                 "Accept": "application/vnd.github+json",
                 "Content-Type": "application/json",
                 "User-Agent": "intellij-gwt-release-monitor"},
    )
    with urlopen(request, timeout=30) as response:
        return json.load(response)


def list_issues(repository, token):
    issues = []
    page = 1
    while True:
        batch = github_request(repository, token, f"issues?state=all&per_page=100&page={page}")
        issues.extend(batch)
        if len(batch) < 100:
            return issues
        page += 1
