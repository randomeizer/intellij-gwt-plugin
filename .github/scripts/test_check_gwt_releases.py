import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from urllib.error import URLError

import check_gwt_releases as monitor


def heading(version):
    anchor = "Release_Notes_" + version.replace(".", "_")
    return f'<h2><a id="{anchor}"></a> Release Notes for {version}</h2>'


class ReleaseMonitorTest(unittest.TestCase):
    def test_only_stable_release_headings_are_detected(self):
        html = ('<a href="#Release_Notes_9_0_0">9.0.0</a>'
                + heading("2.13.1") + heading("2.14.0")
                + '<h2><a id="Release_Notes_3_0_0_RC1"></a> Release Notes for 3.0.0 (RC1)</h2>'
                + '<h2><a id="Release_Notes_3_0_0_Beta1"></a> Release Notes for 3.0.0 (Beta1)</h2>'
                + '<h2><a id="Release_Notes_3_0_0"></a> Release Notes for 3.0.0 (snapshot)</h2>')
        self.assertEqual(monitor.parse_releases(html), {
            "2.13.1": monitor.RELEASE_NOTES_URL + "#Release_Notes_2_13_1",
            "2.14.0": monitor.RELEASE_NOTES_URL + "#Release_Notes_2_14_0",
        })

    def test_changed_or_error_page_fails_instead_of_silently_succeeding(self):
        for html in ("", "<h1>Service unavailable</h1>", '<a id="Release_Notes_2_13_1"></a>'):
            with self.subTest(html=html), self.assertRaises(ValueError):
                monitor.parse_releases(html)

    def test_mismatched_heading_and_anchor_are_rejected(self):
        with self.assertRaises(ValueError):
            monitor.parse_releases('<h2><a id="Release_Notes_2_14_0"></a> Release Notes for 2.13.1</h2>')

    def test_versions_are_compared_numerically_and_all_new_releases_are_kept(self):
        releases = {version: "url" for version in ("2.14.0", "2.13.10", "2.9.0", "2.13.2", "2.13.1")}
        self.assertEqual(monitor.pending_releases(releases, "2.13.1", []),
                         ["2.13.2", "2.13.10", "2.14.0"])

    def test_open_and_closed_issues_prevent_duplicates_even_after_renaming(self):
        releases = {version: "url" for version in ("2.13.2", "2.14.0", "2.15.0")}
        issues = [
            {"title": "Renamed ticket", "state": "open", "body": monitor.issue_marker("2.13.2")},
            {"title": "Also renamed", "state": "closed", "body": monitor.issue_marker("2.14.0")},
        ]
        self.assertEqual(monitor.pending_releases(releases, "2.13.1", issues), ["2.15.0"])

    def test_exact_manual_issue_title_prevents_duplicate_without_marker(self):
        issues = [{"title": " Add support for GWT 2.14.0 ", "body": None}]
        self.assertEqual(monitor.pending_releases({"2.14.0": "url"}, "2.13.1", issues), [])

    def test_pull_requests_do_not_count_as_tracking_issues(self):
        issues = [{"title": monitor.issue_title("2.14.0"), "pull_request": {}, "body": None}]
        self.assertEqual(monitor.pending_releases({"2.14.0": "url"}, "2.13.1", issues), ["2.14.0"])

    def test_prerelease_or_invalid_baselines_are_rejected(self):
        for version in ("2.13", "2.14.0-rc1", "2.14.0.snapshot", "latest"):
            with self.subTest(version=version), self.assertRaises(ValueError):
                monitor.version_key(version)

    @patch.object(monitor.release_monitor, "github_request")
    def test_issue_lookup_paginates_through_open_and_closed_issues(self, request):
        last_issue = {"title": "Renamed", "body": monitor.issue_marker("2.14.0"), "state": "closed"}
        request.side_effect = [[{"title": "Unrelated"}] * 100, [last_issue]]
        issues = monitor.release_monitor.list_issues("owner/repo", "test-token")
        self.assertEqual(len(issues), 101)
        self.assertEqual(request.call_args_list[0].args[2], "issues?state=all&per_page=100&page=1")
        self.assertEqual(request.call_args_list[1].args[2], "issues?state=all&per_page=100&page=2")
        self.assertEqual(monitor.pending_releases({"2.14.0": "url"}, "2.13.1", issues), [])

    @patch("builtins.print")
    @patch.object(monitor.release_monitor, "github_request")
    def test_creation_and_subsequent_run_create_only_one_issue(self, request, _print):
        created = []

        def github(repository, token, endpoint, payload=None):
            if payload is None:
                return list(created)
            issue = {**payload, "html_url": "https://github.com/owner/repo/issues/1", "state": "closed"}
            created.append(issue)
            return issue

        request.side_effect = github
        releases = monitor.parse_releases(heading("2.13.1") + heading("2.14.0"))
        monitor.create_issues("owner/repo", "test-token", releases, "2.13.1")
        monitor.create_issues("owner/repo", "test-token", releases, "2.13.1")
        self.assertEqual(len(created), 1)
        self.assertEqual(created[0]["title"], "Add support for GWT 2.14.0")
        self.assertIn(monitor.issue_marker("2.14.0"), created[0]["body"])
        self.assertIn(releases["2.14.0"], created[0]["body"])
        self.assertIn("2.13.1", created[0]["body"])

    @patch.object(monitor.release_monitor, "github_request", side_effect=URLError("GitHub unavailable"))
    def test_issue_lookup_failure_does_not_attempt_creation(self, request):
        with self.assertRaises(URLError):
            monitor.create_issues("owner/repo", "test-token", {"2.14.0": "url"}, "2.13.1")
        self.assertEqual(request.call_count, 1)

    @patch("builtins.print")
    @patch.object(monitor.release_monitor, "github_request")
    def test_verified_release_never_creates_an_issue(self, request, _print):
        request.return_value = []
        monitor.create_issues("owner/repo", "test-token", {"2.13.1": "url"}, "2.13.1")
        self.assertEqual(request.call_count, 1)

    @patch("builtins.print")
    @patch.object(monitor.release_monitor, "github_request")
    def test_dry_run_never_accesses_github(self, request, _print):
        with tempfile.TemporaryDirectory() as directory:
            notes = Path(directory) / "notes.html"
            notes.write_text(heading("2.13.1") + heading("2.14.0"), encoding="utf-8")
            config = Path(directory) / "config.json"
            config.write_text('{"latestVerifiedVersion": "2.13.1"}', encoding="utf-8")
            with patch.object(monitor, "CONFIG_PATH", config), patch(
                    "sys.argv", ["monitor", "--dry-run", "--notes-file", str(notes)]):
                monitor.main()
        request.assert_not_called()

    @patch("builtins.print")
    @patch.object(monitor.release_monitor, "github_request")
    def test_stale_release_page_fails_before_accessing_github(self, request, _print):
        with tempfile.TemporaryDirectory() as directory:
            notes = Path(directory) / "notes.html"
            notes.write_text(heading("2.12.0"), encoding="utf-8")
            config = Path(directory) / "config.json"
            config.write_text('{"latestVerifiedVersion": "2.13.1"}', encoding="utf-8")
            with patch.object(monitor, "CONFIG_PATH", config), patch(
                    "sys.argv", ["monitor", "--notes-file", str(notes)]), self.assertRaises(ValueError):
                monitor.main()
        request.assert_not_called()


if __name__ == "__main__":
    unittest.main()
