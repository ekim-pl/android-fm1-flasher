"""Require trusted Android CI platform results for the exact released commit."""
import base64
from pathlib import Path
import re

WORKFLOW = '.github/workflows/android-ci.yml'
JOBS = {'platform (34)', 'platform (36)'}


def pages(api, endpoint, key):
    result = []
    for page in range(1, 11):
        batch = api(f'{endpoint}&per_page=100&page={page}')[key]
        result.extend(batch)
        if len(batch) < 100:
            return result
    raise ValueError('Too many CI records; refusing incomplete platform evidence')


def require_platform(api, repo, commit):
    if not re.fullmatch('[0-9a-f]{40}', commit):
        raise ValueError('Platform gate requires a resolved commit SHA')
    prefix = f'repos/{repo}'
    repository = api(prefix)
    workflow = api(f'{prefix}/actions/workflows/android-ci.yml')
    if repository['full_name'] != repo or workflow['path'] != WORKFLOW or workflow['state'] != 'active':
        raise ValueError('Untrusted platform workflow identity')
    runs = pages(api, f'{prefix}/actions/workflows/{workflow["id"]}/runs?head_sha={commit}', 'workflow_runs')
    trusted = [run for run in runs if
               run['workflow_id'] == workflow['id'] and run['path'] == WORKFLOW and
               run['head_repository']['full_name'] == repo and run['head_sha'] == commit and
               run['head_branch'] == repository['default_branch'] and
               run['event'] in ('push', 'workflow_dispatch')]
    if not trusted:
        raise ValueError('Missing trusted default-branch platform CI for released SHA')
    run = max(trusted, key=lambda item: item['id'])
    if run['status'] != 'completed' or run['conclusion'] != 'success':
        raise ValueError('Latest trusted platform CI must have succeeded')
    # A familiar name/ID alone does not prove which workflow code ran.
    source = api(f'{prefix}/contents/{WORKFLOW}?ref={commit}')
    if (source.get('encoding') != 'base64' or
            base64.b64decode(source['content']) != Path(WORKFLOW).read_bytes()):
        raise ValueError('Platform workflow differs from trusted publication tooling')
    jobs = pages(api, f'{prefix}/actions/runs/{run["id"]}/jobs?filter=all', 'jobs')
    for name in JOBS:
        matches = [job for job in jobs if job['name'] == name]
        if not matches:
            raise ValueError(f'Missing platform matrix job: {name}')
        # Partial reruns retain successful jobs from previous attempts. For each
        # matrix cell only its newest execution counts, never an older success.
        attempt = max(job['run_attempt'] for job in matches)
        latest = [job for job in matches if job['run_attempt'] == attempt]
        if len(latest) != 1:
            raise ValueError(f'Ambiguous platform matrix job: {name}')
        job = latest[0]
        steps = [step for step in job.get('steps', []) if step['name'] == 'Run platform tests']
        if (not 1 <= attempt <= run['run_attempt'] or job['head_sha'] != commit or
                job['status'] != 'completed' or job['conclusion'] != 'success' or
                len(steps) != 1 or steps[0]['conclusion'] != 'success'):
            raise ValueError(f'Platform matrix job did not pass: {name}')
    # Detect a rerun started while we were reading jobs.
    current = api(f'{prefix}/actions/runs/{run["id"]}')
    if any(current[key] != run[key] for key in ('run_attempt', 'status', 'conclusion', 'head_sha')):
        raise ValueError('Platform run changed during validation; retry after CI completes')
    print(f'Platform API34/36 passed for {commit}: run {run["id"]}, attempt {run["run_attempt"]}')
