import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from urllib.error import URLError

import check_intellij_releases as monitor


def release(version, kind="release"):
    return {"version": version, "type": kind, "notesLink": "https://youtrack.jetbrains.com/articles/IDEA-A-123"}


class IntelliJReleaseMonitorTest(unittest.TestCase):
    def test_patch_releases_collapse_into_one_line_with_original_release_notes(self):
        original = release("2027.1")
        releases = monitor.parse_releases({"IIU": [release("2027.1.2"), original, release("2027.1.1")]})
        self.assertEqual(releases, {"2027.1": original})

    def test_patch_only_feed_still_detects_a_new_line(self):
        releases = monitor.parse_releases({"IIU": [release("2027.1.2"), release("2027.1.1")]})
        self.assertEqual(monitor.pending_releases(releases, "2026.3", []), ["2027.1"])
        self.assertEqual(releases["2027.1"]["version"], "2027.1.1")

    def test_patches_for_current_and_older_lines_never_create_tickets(self):
        releases = monitor.parse_releases({"IIU": [release("2026.3.99"), release("2026.2.10")]})
        self.assertEqual(monitor.pending_releases(releases, "2026.3", []), [])

    def test_previews_are_excluded_even_if_version_is_numeric(self):
        releases = monitor.parse_releases({"IIU": [release("2026.3"), release("2027.1", "eap"),
                                                   release("2027.2", "rc"), release("2027.3", "beta"),
                                                   release("2028.1 EAP"), release("2028.2-RC")]})
        self.assertEqual(list(releases), ["2026.3"])

    def test_all_new_major_and_minor_lines_are_ordered_numerically(self):
        releases = {line: release(line) for line in ("2028.1", "2027.10", "2027.2", "2026.4")}
        self.assertEqual(monitor.pending_releases(releases, "2026.3.1", []),
                         ["2026.4", "2027.2", "2027.10", "2028.1"])

    def test_baseline_uses_gradle_target_and_ignores_patch_component(self):
        self.assertEqual(monitor.read_baseline("# platformVersion=2020.1\nplatformVersion = 2026.3.2\n"), "2026.3")
        for properties in ("", "platformVersion=2027.1-EAP", "platformVersion=2026.3\nplatformVersion=2027.1"):
            with self.subTest(properties=properties), self.assertRaises(ValueError):
                monitor.read_baseline(properties)

    def test_empty_or_unrecognized_feed_fails(self):
        for feed in ([], {}, {"IIC": [release("2026.3")]}, {"IIU": []},
                     {"IIU": [None]}, {"IIU": [release("2027.1", "eap")]}):
            with self.subTest(feed=feed), self.assertRaises(ValueError):
                monitor.parse_releases(feed)

    def test_open_closed_and_manual_issues_prevent_duplicates_but_prs_do_not(self):
        releases = {line: release(line) for line in ("2027.1", "2027.2", "2027.3", "2028.1")}
        issues = [
            {"title": "Renamed", "body": monitor.issue_marker("2027.1"), "state": "open"},
            {"title": "Also renamed", "body": monitor.issue_marker("2027.2"), "state": "closed"},
            {"title": "Add support for IntelliJ IDEA 2027.3", "body": None},
            {"title": "Add support for IntelliJ IDEA 2028.1", "body": None, "pull_request": {}},
        ]
        self.assertEqual(monitor.pending_releases(releases, "2026.3", issues), ["2028.1"])

    @patch("builtins.print")
    @patch.object(monitor.release_monitor, "github_request")
    def test_repeat_runs_and_subsequent_patches_do_not_recreate_a_closed_ticket(self, request, _print):
        created = []

        def github(repository, token, endpoint, payload=None):
            if payload is None:
                return list(created)
            issue = {**payload, "html_url": "https://github.com/owner/repo/issues/1", "state": "closed"}
            created.append(issue)
            return issue

        request.side_effect = github
        for version in ("2027.1", "2027.1.1", "2027.1.2"):
            releases = monitor.parse_releases({"IIU": [release(version)]})
            monitor.create_issues("owner/repo", "test-token", releases, "2026.3")
        self.assertEqual(len(created), 1)
        self.assertEqual(created[0]["title"], "Add support for IntelliJ IDEA 2027.1")
        self.assertIn(monitor.issue_marker("2027.1"), created[0]["body"])
        self.assertIn(release("2027.1")["notesLink"], created[0]["body"])

    @patch.object(monitor.release_monitor, "github_request", side_effect=URLError("GitHub unavailable"))
    def test_lookup_failure_does_not_attempt_creation(self, request):
        with self.assertRaises(URLError):
            monitor.create_issues("owner/repo", "test-token", {"2027.1": release("2027.1")}, "2026.3")
        self.assertEqual(request.call_count, 1)

    @patch("builtins.print")
    @patch.object(monitor.release_monitor, "github_request")
    def test_dry_run_and_stale_feed_never_access_github(self, request, _print):
        with tempfile.TemporaryDirectory() as directory:
            properties = Path(directory) / "gradle.properties"
            properties.write_text("platformVersion=2026.3\n", encoding="utf-8")
            feed = Path(directory) / "releases.json"
            with patch.object(monitor, "PROPERTIES_PATH", properties), patch(
                    "sys.argv", ["monitor", "--dry-run", "--releases-file", str(feed)]):
                feed.write_text(json.dumps({"IIU": [release("2027.1")]}), encoding="utf-8")
                monitor.main()
                feed.write_text(json.dumps({"IIU": [release("2026.2")]}), encoding="utf-8")
                with self.assertRaises(ValueError):
                    monitor.main()
        request.assert_not_called()


if __name__ == "__main__":
    unittest.main()
