import sys
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from ui_adb import result_summary

def report(codes,total):
    return '\n'.join([f'INSTRUMENTATION_STATUS: numtests={total}',*[f'INSTRUMENTATION_STATUS_CODE: {n}' for n in codes],f'OK ({total} tests)','INSTRUMENTATION_CODE: -1'])

class UiAdbTest(unittest.TestCase):
    def test_complete_success_is_a_general_android_pass(self):
        result=result_summary(report([1,0,1,0],2))
        self.assertEqual('PASS',result['status'])
        self.assertEqual(2,result['passed'])
        self.assertFalse(result['pleos_verified'])

    def test_failure_cannot_hide_behind_ok_footer(self):
        result=result_summary(report([1,0,1,-2],2))
        self.assertEqual('FAIL',result['status'])
        self.assertEqual(1,result['failed'])

    def test_skips_are_not_passes(self):
        result=result_summary(report([1,0,-3,-4],3))
        self.assertEqual('FAIL',result['status'])
        self.assertEqual(1,result['passed'])
        self.assertEqual(2,result['skipped'])

    def test_incomplete_disconnect_is_not_a_pass(self):
        result=result_summary(report([1,0],3))
        self.assertEqual('FAIL',result['status'])
        self.assertFalse(result['complete'])

    def test_empty_and_missing_terminal_fail_closed(self):
        self.assertEqual('FAIL',result_summary('')['status'])
        self.assertEqual('FAIL',result_summary(report([1,0],1).replace('INSTRUMENTATION_CODE: -1',''))['status'])

if __name__=='__main__':
    unittest.main()
