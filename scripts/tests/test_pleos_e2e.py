import argparse
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import pleos_e2e


class PleosE2eTest(unittest.TestCase):
    def test_status_is_read_only_default(self):
        args = pleos_e2e.parse_args([])
        self.assertEqual("status", args.scenario)
        self.assertFalse(args.apply)

    def test_mutation_requires_apply(self):
        with self.assertRaisesRegex(PermissionError, "--apply"):
            pleos_e2e.require_apply(argparse.Namespace(apply=False, ack_debug_synthetic=False))

    def test_debug_requires_separate_acknowledgment(self):
        with self.assertRaisesRegex(PermissionError, "--ack-debug-synthetic"):
            pleos_e2e.require_apply(argparse.Namespace(apply=True, ack_debug_synthetic=False), debug=True)

    def test_all_requested_scenarios_are_declared(self):
        self.assertEqual(
            {
                "motion-p-d-unknown", "app-visible-nav-foreground", "hun-two-actions",
                "no-action-timeout-dismissal", "permission-revocation", "car-service-restart",
                "route-invalidation", "refuel-event",
            },
            set(pleos_e2e.SCENARIOS),
        )

    def test_unsupported_real_flows_fail_closed(self):
        self.assertEqual("source-only", pleos_e2e.SCENARIOS["hun-two-actions"][0])
        self.assertEqual("source-only", pleos_e2e.SCENARIOS["route-invalidation"][0])
        self.assertEqual("blocked", pleos_e2e.SCENARIOS["car-service-restart"][0])


if __name__ == "__main__":
    unittest.main()
