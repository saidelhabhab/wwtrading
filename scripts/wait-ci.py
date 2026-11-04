import sys
import time
sys.path.append('scripts')
m = __import__('monitor-ci')

def wait_for_run():
    token = m.get_token()
    print("Waiting for latest workflow run to appear...")
    run_id = None
    for _ in range(10):
        try:
            runs = m.api_call('actions/runs?per_page=1', token)
            if runs.get('workflow_runs'):
                run_id = runs['workflow_runs'][0]['id']
                head_sha = runs['workflow_runs'][0]['head_sha'][:7]
                print(f"Tracking Workflow Run {run_id} for commit {head_sha}")
                break
        except Exception:
            pass
        time.sleep(2)
        
    if not run_id:
        print("No run found.")
        return

    start_time = time.time()
    while True:
        try:
            run = m.api_call(f'actions/runs/{run_id}', token)
            status = run.get('status')
            conclusion = run.get('conclusion')
            print(f"[{int(time.time() - start_time)}s] Overall Status: {status}, Conclusion: {conclusion}", flush=True)
            
            jobs = m.api_call(f'actions/runs/{run_id}/jobs', token)
            for job in jobs.get('jobs', []):
                print(f"   • {job['name']}: {job['status']} ({job.get('conclusion')})", flush=True)
                    
            if status == 'completed':
                print(f"\nFinal Workflow Conclusion: {conclusion}", flush=True)
                break
        except Exception as e:
            print(f"Error checking status: {e}", flush=True)
            
        time.sleep(15)

if __name__ == '__main__':
    wait_for_run()
