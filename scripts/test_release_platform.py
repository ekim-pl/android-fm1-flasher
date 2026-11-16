import base64
import copy
from pathlib import Path
import unittest
from unittest.mock import Mock

from release_platform import require_platform, pages, WORKFLOW


class PlatformTest(unittest.TestCase):
    def setUp(self):
        self.commit = 'a' * 40
        self.repo = 'owner/repo'
        self.repository = dict(full_name=self.repo, default_branch='main')
        self.workflow = dict(id=42, path=WORKFLOW, state='active')
        self.run = dict(id=100, workflow_id=42, path=WORKFLOW, head_sha=self.commit,
                        head_branch='main', head_repository=dict(full_name=self.repo),
                        event='push', status='completed', conclusion='success', run_attempt=1)
        self.runs = [self.run]
        self.jobs = [dict(name=f'platform ({api})', head_sha=self.commit, status='completed',
                         conclusion='success', run_attempt=1,
                         steps=[dict(name='Run platform tests', conclusion='success')]) for api in (34, 36)]
        self.source = dict(encoding='base64', content=base64.b64encode(Path(WORKFLOW).read_bytes()).decode())
        self.final = None

    def api(self, endpoint):
        if '/contents/' in endpoint:
            return self.source
        if '/jobs?' in endpoint:
            return {'jobs': self.jobs}
        if '/runs?' in endpoint:
            return {'workflow_runs': self.runs}
        if '/actions/runs/' in endpoint:
            return self.final or self.run
        if '/workflows/' in endpoint:
            return self.workflow
        return self.repository

    def check(self):
        require_platform(self.api, self.repo, self.commit)

    def test_complete_matrix(self):
        self.check()

    def test_missing_ci_and_wrong_identity(self):
        for field, value in [('head_sha', 'b' * 40), ('workflow_id', 99), ('path', 'fake.yml'),
                             ('head_repository', dict(full_name='fork/repo')), ('event', 'pull_request'),
                             ('head_branch', 'feature')]:
            with self.subTest(field=field):
                self.runs = [dict(self.run, **{field: value})]
                with self.assertRaises(ValueError):
                    self.check()
        self.runs = []
        with self.assertRaises(ValueError):
            self.check()

    def test_latest_run_blocks_fallback_to_older_success(self):
        for status, conclusion in [('completed', 'failure'), ('completed', 'cancelled'),
                                   ('in_progress', None), ('queued', None)]:
            self.runs = [self.run, dict(self.run, id=101, status=status, conclusion=conclusion)]
            with self.subTest(status=status, conclusion=conclusion), self.assertRaises(ValueError):
                self.check()

    def test_missing_failed_skipped_wrong_sha_and_duplicate_matrix_cells(self):
        original = copy.deepcopy(self.jobs)
        for field, value in [('head_sha', 'b' * 40), ('conclusion', 'failure'),
                             ('conclusion', 'skipped'), ('status', 'in_progress'),
                             ('steps', []), ('steps', [dict(name='Run platform tests', conclusion='skipped')]),
                             ('run_attempt', 2)]:
            self.jobs = [dict(original[0], **{field: value}), original[1]]
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                self.check()
        for jobs in ([], original[:1], original + original[:1]):
            self.jobs = jobs
            with self.assertRaises(ValueError):
                self.check()

    def test_partial_rerun_replaces_failure_but_not_other_success(self):
        self.run['run_attempt'] = 2
        self.jobs[0]['conclusion'] = 'failure'
        self.jobs.append(dict(self.jobs[0], conclusion='success', run_attempt=2))
        self.check()
        self.jobs[-1]['conclusion'] = 'failure'
        self.jobs[0]['conclusion'] = 'success'
        with self.assertRaises(ValueError):
            self.check()

    def test_workflow_bytes_and_active_identity_are_required(self):
        self.source['content'] = base64.b64encode(b'fake passing jobs').decode()
        with self.assertRaises(ValueError):
            self.check()
        self.workflow['state'] = 'disabled_manually'
        with self.assertRaises(ValueError):
            self.check()

    def test_rerun_starting_during_validation_blocks(self):
        self.final = dict(self.run, run_attempt=2, status='in_progress', conclusion=None)
        with self.assertRaises(ValueError):
            self.check()

    def test_pagination_is_complete_and_bounded(self):
        api = Mock(side_effect=[{'jobs': list(range(100))}, {'jobs': [100]}])
        self.assertEqual(pages(api, 'endpoint?filter=all', 'jobs'), list(range(101)))
        self.assertIn('page=2', api.call_args.args[0])
        with self.assertRaises(ValueError):
            pages(Mock(return_value={'jobs': list(range(100))}), 'endpoint?', 'jobs')

    def test_invalid_sha_rejected_before_api(self):
        api = Mock()
        with self.assertRaises(ValueError):
            require_platform(api, self.repo, 'main')
        api.assert_not_called()
