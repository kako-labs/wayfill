import unittest
from unittest import mock
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import pleos_probe


class PleosProbeTest(unittest.TestCase):
    def test_device_parser_and_single_selection(self):
        text = """List of devices attached
emulator-5554 device product:pleos model:Connect transport_id:1
offline-1 offline transport_id:2
"""
        devices = pleos_probe.parse_adb_devices(text)
        self.assertEqual("emulator-5554", pleos_probe.choose_serial(devices, None))
        self.assertEqual("pleos", devices[0]["product"])

    def test_selection_fails_closed_for_ambiguity(self):
        devices = [{"serial": "a", "state": "device"}, {"serial": "b", "state": "device"}]
        with self.assertRaisesRegex(ValueError, "set --serial"):
            pleos_probe.choose_serial(devices, None)

    def test_selection_rejects_offline_requested_target(self):
        with self.assertRaisesRegex(ValueError, "not ready"):
            pleos_probe.choose_serial([{"serial": "a", "state": "offline"}], "a")

    def test_bounded_lines_filters_and_caps(self):
        text = "ignore\nmonitoring one\nmonitoring two\nmonitoring three"
        self.assertEqual(
            ["monitoring one", "monitoring two"],
            pleos_probe.bounded_lines(text, patterns=("monitoring",), limit=2),
        )

    def test_default_and_apply_modes_both_remain_read_only(self):
        common = ["--timeout", "5"]
        with mock.patch.object(pleos_probe, "find_adb", return_value=(None, "not-found")):
            default, _ = pleos_probe.build_inventory(pleos_probe.parse_args(common))
            requested, _ = pleos_probe.build_inventory(pleos_probe.parse_args([*common, "--apply"]))
        self.assertEqual("read-only", default["probe_mode"])
        self.assertFalse(default["apply"]["requested"])
        self.assertFalse(default["apply"]["performed"])
        self.assertTrue(requested["apply"]["requested"])
        self.assertFalse(requested["apply"]["performed"])

    def test_no_mutating_adb_verbs_are_present_in_probe_source(self):
        source = Path(pleos_probe.__file__).read_text(encoding="utf-8")
        for forbidden in ("pm clear", "pm grant", "pm revoke", "am force-stop", "adb install", "am crash"):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
