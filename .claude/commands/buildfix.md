---
description: Monitor GitHub Actions build and fix any errors
---

Monitor the latest GitHub Actions build for this repository and fix any build errors.

**Your task:**

1. Use `gh run list --limit 5` to get recent workflow runs
2. Identify the most recent run and check its status
3. If the build is:
   - **In progress**: Wait 30 seconds and check again (repeat up to 3 times)
   - **Completed successfully**: Report success and provide APK download link
   - **Failed**: Use `gh run view <run-id> --log-failed` to get error logs
4. If there are errors:
   - Analyze the error messages
   - Identify the root cause (dependency issues, syntax errors, etc.)
   - Fix the code
   - Commit and push the fix
   - Monitor the new build
5. Repeat until the build succeeds

**Important:**
- Use the `gh` CLI tool for all GitHub API interactions
- Be patient with builds - they can take 3-5 minutes
- If builds keep failing after 2 fix attempts, ask the user for guidance
- Always provide the APK download URL when build succeeds

**Output format:**
- Clear status updates ("Build in progress...", "Build failed: <reason>", "Build succeeded!")
- Link to workflow run: https://github.com/gwodu/Verdure/actions/runs/<run-id>
- APK download link when successful
