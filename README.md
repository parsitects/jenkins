**Overview**
- This repository provides a Jenkins Shared Library pipeline (see `vars/build.groovy`) for building and testing the ICSNPP parsers across multiple Zeek versions and compilers in parallel.
- The pipeline dynamically discovers the latest Zeek LTS versions, pulls required Docker images, and runs build/test steps for each version-compiler combination in parallel.
- Supports three parser types: BINPAC, SPICY, and ZEEKONLY (Zeek-script-only parsers that require no build step).

**Key Concepts**
- Shared Library Entry: The pipeline logic is defined in `vars/build.groovy` and is consumed by a project Jenkinsfile via a `@Library('CISAGOV Jenkins@master')` reference, calling the `build()` step.
- Agent: Runs on nodes labeled `rhel9` and uses Docker to run containerized build/test steps.
- Dynamic Version Discovery: Queries the GitHub Container Registry API to discover the two most recent LTS versions of Zeek (e.g., 7.0.x and 8.0.x), plus 'latest'.
- Parallelization: For each Zeek version, builds and tests run in parallel across both clang and gcc compilers.
- Combined Build/Test: Each variant performs checkout, build, and test in a single stage without stashing artifacts between stages.

**Filesystem and Paths**
- Jenkins provides a job-specific workspace via `WORKSPACE`, e.g., `/home/build/jenkins/workspace/<job_name>`.
- Docker agent mounts only the job workspace paths into the container, typically:
  - `-v $WORKSPACE:$WORKSPACE`
  - `-v $WORKSPACE@tmp:$WORKSPACE@tmp`
- The pipeline uses `dir("<subdir>")` (not `ws()`) to ensure all work happens under `$WORKSPACE`. For example:
  - `dir("8.0.1-clang")` maps to `$WORKSPACE/8.0.1-clang` on the host and inside the container.
  - `dir("latest-gcc")` maps to `$WORKSPACE/latest-gcc` on the host and inside the container.
- Note for posterity: why not `ws()` here?
  - `ws("/abs/path")` switches to absolute node-level workspaces like `/home/build/jenkins/8.0.1-clang`, which are not mounted in the container by default. That causes shell steps inside containers to fail to start because the working directory does not exist inside the container. Keeping everything under `dir()` ensures host and container see the same files.
- Quick sanity checks inside a step (useful for debugging):
  - `sh 'pwd; echo WORKSPACE=$WORKSPACE; ls -la'`
  - Expect `pwd` to show `$WORKSPACE` or a subdirectory under it when inside containerized steps.

**Pipeline Flow**
- Options and Environment
  - `options { skipDefaultCheckout() }` – avoids an implicit checkout; we do explicit checkouts per variant.
  - `env.ZEEK_VERSIONS` – comma-separated list of versions to test (set dynamically in Discover Versions stage).

- Stage: Discover Versions
  - Uses GitHub API to query the ghcr.io/mmguero/zeek container registry for available versions.
  - Identifies the two most recent LTS versions (e.g., finds all x.0.y releases, groups by major version, takes the latest patch from the top two major versions).
  - Prepends 'latest' to the list, resulting in something like: `latest,8.0.1,7.0.10`.
  - Stores the result in `env.ZEEK_VERSIONS` for use in subsequent stages.

- Stage: Pull Docker Images
  - For each version in `env.ZEEK_VERSIONS`:
    - For each compiler (clang, gcc):
      - Pulls the corresponding image: `ghcr.io/mmguero/zeek:${tag}` where tag is `latest-clang`, `v8.0.1-clang`, etc.

- Stage: Remove Old/Dangling Docker Images
  - Runs cleanup BEFORE builds to free up space and remove stale images:
    - `docker container prune -f || true`
    - `docker volume prune -f || true`
    - `docker network prune -f || true`
  - Intelligently removes old Zeek images:
    - Builds a list of images to keep based on `env.ZEEK_VERSIONS`
    - Removes any `ghcr.io/mmguero/zeek` images not in the keep list
    - This prevents accumulation of old LTS versions as Zeek releases new versions
  - Finally removes any dangling images:
    - `docker image prune -f`

- Stage: Build and Test Matrix
  - Nested structure:
    - Outer loop: For each Zeek version (creates a named stage per version)
    - Inner loop: For each compiler (runs in parallel)
  - Each parallel branch:
    - Runs inside a Docker container: `docker.image("ghcr.io/mmguero/zeek:${tag}").inside('--user root --entrypoint=')`
    - Uses `dir(variant)` where variant is `${version}-${compiler}` (e.g., `latest-clang`, `8.0.1-gcc`, `7.0.10-clang`)
    - Executes `checkout scm` to get source code
    - Calls `buildAndTestProtocolParser()` which:
      - Cleans previous build artifacts and test results
      - Reads `.repotype` file to determine parser type (BINPAC, SPICY, or ZEEKONLY)
      - For BINPAC: runs `./configure` and `make` in build/
      - For SPICY: runs `cmake` and `cmake --build` with 2 parallel jobs
      - For ZEEKONLY: skips build step entirely
      - Runs `btest -d` in the testing/ directory

- Post: Declarative Post Actions
  - Always runs after the pipeline completes:
    - `deleteDir()` - Removes the Jenkins job workspace directory to free up disk space

**Reusable Functions**
- `buildAndTestProtocolParser()`
  - Combined function that handles both building and testing based on parser type.
  - Cleans up previous artifacts: `rm -rf build testing/.tmp testing/.btest.failed.dat`
  - Reads `.repotype` file to determine parser type.
  - Build steps (based on type):
    - BINPAC: `./configure && cd build && make`
    - SPICY: `mkdir -p build && cd build && cmake .. && cmake --build . -j 2`
    - ZEEKONLY: No build step needed
  - Test step (all types): `cd testing && btest -d`

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
