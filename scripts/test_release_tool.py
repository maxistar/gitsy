import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import release_tool as tool


class ReleaseToolTest(unittest.TestCase):
    def test_parse_groovy_version(self):
        value = tool.parse_version('versionCode 7\nversionName "1.2.3"\n')
        self.assertEqual(value, tool.Version("1.2.3", 7))

    def test_parse_requires_one_semver_version(self):
        with self.assertRaises(tool.ReleaseError):
            tool.parse_version('versionCode 1\nversionName "1.0"\n')
        with self.assertRaises(tool.ReleaseError):
            tool.parse_version('versionCode 1\nversionCode 2\nversionName "1.0.0"\n')

    def test_changelog_validation_rejects_placeholder_then_accepts_notes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            cfg = {"metadataRoot": "metadata", "requiredLocales": ["en-US", "ru-RU"], "placeholderMarkers": ["TODO"]}
            value = tool.Version("1.0.0", 1)
            with patch.object(tool, "ROOT", root):
                for path in tool.notes(value, cfg).values():
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text("TODO", encoding="utf-8")
                with self.assertRaises(tool.ReleaseError):
                    tool.validate_notes(value, cfg)
                for path in tool.notes(value, cfg).values():
                    path.write_text("Notes", encoding="utf-8")
                tool.validate_notes(value, cfg)

    @patch.object(tool, "historical_versions", return_value=[tool.Version("0.9.0", 4)])
    @patch.object(tool, "ref_exists", return_value=False)
    def test_version_code_must_increase(self, _exists, _history):
        with self.assertRaises(tool.ReleaseError):
            tool.validate_history(tool.Version("1.0.0", 4), {"tagPrefix": "v"}, allow_tag=False)

    @patch.object(tool, "validate_notes")
    @patch.object(tool, "validate_history")
    @patch.object(tool, "run")
    @patch.object(tool, "version", return_value=tool.Version("1.2.3", 6))
    def test_candidate_requires_matching_branch_and_merge_commit(self, _version, run, _history, _notes):
        run.side_effect = lambda *args, **kwargs: "merge deadbeef parent" if args[:3] == ("git", "rev-list", "--parents") else ""
        cfg = {"branches": {"stable": "master", "releasePrefix": "release/", "hotfixPrefix": "hotfix/"}, "tagPrefix": "v", "englishLocale": "en-US", "metadataRoot": "m", "requiredLocales": [], "placeholderMarkers": []}
        result = tool.candidate("release/1.2.3", "master", "deadbeef", True, cfg)
        self.assertEqual(result["tag"], "v1.2.3")
        with self.assertRaises(tool.ReleaseError):
            tool.candidate("release/1.2.2", "master", "deadbeef", True, cfg)


if __name__ == "__main__":
    unittest.main()
