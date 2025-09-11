**Overview**
- This repository provides a Jenkins Shared Library pipeline (see `vars/build.groovy`) for building and testing the ICSNPP parsers across numerous Docker images in parallel.
- The pipeline pulls required images, checks out four independent working copies of the source, builds each inside its corresponding container, then runs tests, and finally prunes unused Docker resources.

**Key Concepts**
- Shared Library Entry: The pipeline logic is defined in `vars/build.groovy` and is consumed by a project Jenkinsfile via a `@Library('CISAGOV Jenkins@master')` reference, calling the `build()` step.
- Agent: Runs on nodes labeled `rhel9` and uses Docker to run containerized build/test steps.
- Parallelization: The flow is organized into sequential stages, each with parallel branches:
  - Pull Docker Images (parallel branches for v8.0.0 and latest)
  - Checkout Source (parallel branches for four variants)
  - Build Matrix (parallel branches for four variants)
  - Test Matrix (parallel branches for four variants)
- Stash/Unstash: Moves source and build artifacts between stages while keeping steps isolated per variant.

**Filesystem and Paths**
- Jenkins provides a job-specific workspace via `WORKSPACE`, e.g., `/home/build/jenkins/workspace/<job_name>`.
- Docker agent mounts only the job workspace paths into the container, typically:
  - `-v $WORKSPACE:$WORKSPACE`
  - `-v $WORKSPACE@tmp:$WORKSPACE@tmp`
- The pipeline uses `dir("<subdir>")` (not `ws()`) to ensure all work happens under `$WORKSPACE`. For example:
  - `dir("v8-clang")` maps to `$WORKSPACE/v8-clang` on the host and inside the container.
- Note for posterity: why not `ws()` here? 
  - `ws("/abs/path")` switches to absolute node-level workspaces like `/home/build/jenkins/v8-clang`, which are not mounted in the container by default. That causes shell steps inside containers to fail to start because the working directory does not exist inside the container. Keeping everything under `dir()` ensures host and container see the same files.
- Quick sanity checks inside a step (useful for debugging):
  - `sh 'pwd; echo WORKSPACE=$WORKSPACE; ls -la'`
  - Expect `pwd` to show `$WORKSPACE` or a subdirectory under it when inside containerized steps.

**Pipeline Flow**
- Options and Environment
  - `options { skipDefaultCheckout() }` – avoids an implicit checkout; we do explicit checkouts per variant.
  - Environment flags record build success per variant: `BUILD_V8_CLANG_SUCCESS`, `BUILD_V8_GCC_SUCCESS`, `BUILD_LATEST_CLANG_SUCCESS`, `BUILD_LATEST_GCC_SUCCESS`.

- Stage: Pull Docker Images (parallel)
  - Pull v8.0.0 Images
    - `docker pull ghcr.io/mmguero/zeek:v8.0.0-clang`
    - `docker pull ghcr.io/mmguero/zeek:v8.0.0-gcc`
  - Pull Latest Images
    - `docker pull ghcr.io/mmguero/zeek:latest-clang`
    - `docker pull ghcr.io/mmguero/zeek:latest-gcc`

- Stage: Checkout Source (parallel)
  - Checkout v8-clang
    - `dir("v8-clang") { checkout scm; stash name: 'source-v8-clang', includes: '**' }`
  - Checkout v8-gcc
    - `dir("v8-gcc") { checkout scm; stash name: 'source-v8-gcc', includes: '**' }`
  - Checkout latest-clang
    - `dir("latest-clang") { checkout scm; stash name: 'source-latest-clang', includes: '**' }`
  - Checkout latest-gcc
    - `dir("latest-gcc") { checkout scm; stash name: 'source-latest-gcc', includes: '**' }`

- Stage: Build Matrix (parallel)
  - Common container settings
    - Each branch uses a `docker { image '<tag>'; args '--user root --entrypoint='; reuseNode true }` agent.
    - The `reuseNode true` keeps the same workspace and mounts `$WORKSPACE` inside the container.
  - Build v8.0.0-clang
    - `dir("v8-clang") { unstash 'source-v8-clang'; buildProtcolParser(); stash name: 'build-v8-clang', includes: 'build/**' }`
  - Build v8.0.0-gcc
    - `dir("v8-gcc") { unstash 'source-v8-gcc'; buildProtcolParser(); stash name: 'build-v8-gcc', includes: 'build/**' }`
  - Build latest-clang
    - `dir("latest-clang") { unstash 'source-latest-clang'; buildProtcolParser(); stash name: 'build-latest-clang', includes: 'build/**' }`
  - Build latest-gcc
    - `dir("latest-gcc") { unstash 'source-latest-gcc'; buildProtcolParser(); stash name: 'build-latest-gcc', includes: 'build/**' }`
  - Success flags
    - Each branch sets its `BUILD_*_SUCCESS` environment variable in `post { success { ... } failure { ... } }` for use by tests.

- Stage: Test Matrix (parallel)
  - Common container settings
    - Same images/args as the build stage, with `reuseNode true` for consistent mounts.
  - Test steps (always executed after builds)
    - `dir("<variant>") { unstash 'source-<variant>'; unstash 'build-<variant>'; runBtest() }`
    - If the build artifacts stash is missing, `unstash` fails and the pipeline fails. This enforces:
      - If build succeeds → tests run.
      - If tests do not run or fail → pipeline fails.
    - `runBtest()` executes:
      - `cd testing; btest`

- Post: Declarative Post Actions
  - Cleanup to free resources (non-fatal if nothing to prune):
    - `docker container prune -f`
    - `docker volume prune -f`
    - `docker network prune -f`
    - `docker image prune -f || true`

**Reusable Functions**
- `buildProtcolParser()`
  - Builds the project using CMake in a fresh `build/` directory:
    - `rm -rf build && mkdir build && cd build && cmake .. && cmake --build . -j $(nproc)`
- `runBtest()`
  - Runs tests via `btest` from the `testing/` directory.

**Workspace Rules of Thumb**
- Prefer `dir("subdir")` over `ws()` when running inside Docker agents. Keep all file operations under `$WORKSPACE` so the container can see them.
- If you must use absolute workspaces, update Docker args to mount those paths (e.g., `-v /home/build/jenkins:/home/build/jenkins`), but this is discouraged because it broadens the container’s view of the host and is easy to misconfigure.
- Use `echo WORKSPACE=$WORKSPACE` and `pwd` to verify where a step is executing.

**Troubleshooting**
- Error: `process apparently never started ... durable-<id>`
  - Often indicates the shell step’s working directory does not exist inside the container (e.g., using `ws()` to an unmapped path). Ensure the step runs under `$WORKSPACE` or mount the path into the container.
- Logs show withDockerContainer mounts
  - Look for `-v $WORKSPACE:$WORKSPACE` and ensure your `dir()` paths are subdirectories of `$WORKSPACE`.

**Where to Look**
- Pipeline definition: `vars/build.groovy`
- Consuming Jenkinsfile in the target repo should simply call this library step, e.g., `build()`.
