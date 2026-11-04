import urllib.request
import subprocess
import json
import sys
import time

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

def get_token():
    try:
        proc = subprocess.Popen(['git', 'credential', 'fill'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
        out, _ = proc.communicate('protocol=https\nhost=github.com\n')
        for line in out.splitlines():
            if line.startswith('password='):
                return line.split('=', 1)[1].strip()
    except Exception:
        pass
    return ''

class NoAuthRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new_req = super().redirect_request(req, fp, code, msg, headers, newurl)
        if new_req and 'Authorization' in new_req.headers:
            del new_req.headers['Authorization']
        return new_req

def api_call(endpoint, token):
    url = f'https://api.github.com/repos/saidelhabhab/wwtrading/{endpoint}'
    req = urllib.request.Request(url)
    if token:
        req.add_header('Authorization', f'Bearer {token}')
    req.add_header('User-Agent', 'WWTRADING-CI-Check')
    opener = urllib.request.build_opener(NoAuthRedirectHandler)
    with opener.open(req) as resp:
        return json.loads(resp.read().decode('utf-8'))

def monitor_latest_run():
    token = get_token()
    runs = api_call('actions/runs?per_page=1', token)
    if not runs.get('workflow_runs'):
        print("No workflow runs found.")
        return
    run = runs['workflow_runs'][0]
    run_id = run['id']
    commit_sha = run['head_sha'][:7]
    commit_msg = run.get('head_commit', {}).get('message', '').split('\n')[0]
    print(f"Tracking Workflow Run: {run_id} (Commit: {commit_sha} - '{commit_msg}')")
    print(f"Status: {run['status']}, Conclusion: {run['conclusion']}")
    
    jobs = api_call(f'actions/runs/{run_id}/jobs', token)
    for job in jobs.get('jobs', []):
        print(f" - Job '{job['name']}': {job['status']} ({job['conclusion']})")

if __name__ == '__main__':
    monitor_latest_run()
