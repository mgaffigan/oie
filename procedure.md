# Procedure

1. Collect all libs from the project (before build)
2. Build the project, collect all results except libs
3. Categorize into mine and theirs (+libs)
    - *-client, *-server, *-shared, *-sources -> theirs (`mv dump/*-{client,server,shared,sources}.jar theirs/`)
    - everything else -> mine (`mv dump/* mine/`)
4. Add as many plugins as you can find
5. Generate netlist with scan.ps1