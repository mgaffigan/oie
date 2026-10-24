"""Performs the split described by a Plan."""
import re
import shutil
import subprocess
from collections import defaultdict
from pathlib import Path

import buildedit as be
import gaps
import plan as plan_mod

HERE = Path(__file__).parent
TEMPLATES = HERE / 'templates'
TIERS = ('common', 'server', 'client')


def check_clean(repo):
    if (repo / 'plugins/build.gradle').exists():
        raise SystemExit('plugins/build.gradle exists; the split has been applied')
    out = subprocess.run(['git', 'status', '--porcelain', '--untracked-files=no'], cwd=repo,
                         capture_output=True, text=True, check=True).stdout
    dirty = [l for l in out.splitlines() if not l[3:].startswith('tools/split-plugins')]
    if dirty:
        raise SystemExit('working tree has uncommitted changes; commit or stash first:\n' + '\n'.join(dirty[:10]))


# ------------------------------------------------------------------ files

def move_files(repo, plan):
    for src, dst in sorted(plan.moves.items()):
        (repo / dst).parent.mkdir(parents=True, exist_ok=True)
        shutil.move(repo / src, repo / dst)
    for top in ('server/src', 'client/src', 'server/lib/extensions'):
        for d in sorted((p for p in (repo / top).rglob('*') if p.is_dir()), key=lambda p: -len(p.parts)):
            if not any(d.iterdir()):
                d.rmdir()
        if (repo / top).exists() and not any((repo / top).iterdir()):
            (repo / top).rmdir()


def delete_swagger(repo, plan):
    """The commit that removes core references to plugins already did this; trees without it get it here."""
    import plan as plan_mod
    for path in plan_mod.DELETED:
        if (repo / path).exists():
            (repo / path).unlink()
    p = repo / 'server/src/main/java/com/mirth/connect/server/MirthWebServer.java'
    t = be.read(p)
    if 'SwaggerExamplesServlet' not in t:
        return
    t = be.sub(t, r'        // Add Swagger examples servlet\n.*?addServlet\(swaggerExamplesServlet, "/\*"\);\n[ \t]*\n', '')
    t = be.replace(t, '        handlers.addHandler(swaggerExamplesServletContextHandler);\n', '')
    t = be.replace(t, 'import com.mirth.connect.server.servlets.SwaggerExamplesServlet;\n', '')
    be.write(p, t)


# ------------------------------------------------------------------ plugin builds

def write_plugin_builds(repo, plan):
    for t in TEMPLATES.glob('plugin-*.gradle'):
        shutil.copy(t, repo / 'gradle' / t.name)
    shutil.copy(TEMPLATES / 'plugins.build.gradle', repo / 'plugins/build.gradle')
    tiers = plan_mod.tiers_of(plan)
    for ext in plan.exts:
        have = tiers[ext.name]
        carrier = plan_mod.lib_tier(have)
        aliases = plan.libs.get(ext.name, [])
        if aliases and ext.lib != ext.name:
            raise SystemExit(f'{ext.name}: maven libs are placed under extensions/{ext.lib}')
        for tier in sorted(have, key=TIERS.index):
            body = []
            deps = plan.deps.get((ext.name, tier), {'main': set(), 'test': set()})
            for kind, conf in (('main', 'compileOnly'), ('test', 'testImplementation')):
                for p, t in sorted(deps[kind]):
                    body.append(f"{conf} project(':plugins:{p}:{t}')")
            for alias in aliases:
                conf = 'implementation' if tier == carrier else 'compileOnly'
                body.append(f'{conf} libs.{alias.replace("-", ".")}')
            foreign = plan.foreign_libs.get((ext.name, tier), {'maven': set(), 'vendored': set()})
            body += [f'compileOnly libs.{a.replace("-", ".")}' for a in sorted(foreign['maven'])]
            body += [f"compileOnly fileTree(dir: rootProject.file('plugins/{e}/lib'), include: '*.jar')" for e in sorted(foreign['vendored'])]
            text = f"apply from: rootProject.file('gradle/plugin-{tier}.gradle')\n"
            if body:
                text += '\ndependencies {\n' + ''.join(f'    {l}\n' for l in body) + '}\n'
            if tier == 'server':
                for e_name, root, pkgdir, own in plan.userutil:
                    if e_name == ext.name and own:
                        text += (f"\n// userutil sources are distributed next to the extension for the code-template editor\n"
                                 f"tasks.register('userutilSourcesJar', Jar) {{\n"
                                 f"    archiveFileName = '{ext.name}-userutil-sources.jar'\n"
                                 f"    from('src/main/java') {{ include '{pkgdir}/**' }}\n}}\n")
            if not {'common': ext.shared, 'server': ext.server, 'client': ext.client_pkg}[tier]:
                text += "\n// the extension has no " + tier + " jar; this tier only holds tests\ntasks.named('jar') { enabled = false }\n"
            text += _cycle_snippet(plan, ext, tier)
            if tier == carrier and ext.name in plan.kept_vendored:
                text += (f"\n// core's DICOM utilities compile against these jars, so they stay in server/lib\n"
                         f"tasks.named('extensionLibs') {{\n"
                         f"    from rootProject.file('server/lib/extensions/{plan.kept_vendored[ext.name]}')\n}}\n")
            be.write(repo / 'plugins' / ext.name / tier / 'build.gradle', text)
    return tiers


def _cycle_snippet(plan, ext, tier):
    node = (ext.name, tier)
    for comp in plan.cycles:
        if node not in comp:
            continue
        host, guests = comp[0], comp[1:]
        pkgs = lambda n: [e.pkg for e in plan.exts if e.name == n[0]] + \
            [x for e in plan.exts if e.name == n[0] for x in e.extra_server_pkgs]
        if node == host:
            text = '\n// ' + ', '.join(f'{n[0]}:{n[1]}' for n in comp) + ' use each other, so they compile together here (see plugins/gaps.md)\n'
            for g in guests:
                text += f"sourceSets.main.java.srcDir rootProject.file('plugins/{g[0]}/{g[1]}/src/main/java')\n"
            text += "tasks.named('jar') {\n" + ''.join(f"    exclude '{p}/**'\n" for g in guests for p in pkgs(g)) + "}\n"
            return text
        return ('\n// compiled together with ' + f':plugins:{host[0]}:{host[1]}' + ' (see plugins/gaps.md); this project packages and tests its part\n'
                f"evaluationDependsOn(':plugins:{host[0]}:{host[1]}')\n"
                'sourceSets.main.java.srcDirs = []\n'
                'sourceSets.main.resources.srcDirs = []\n'
                f"dependencies {{\n    compileOnly project(':plugins:{host[0]}:{host[1]}').sourceSets.main.output\n}}\n"
                "tasks.named('jar') {\n"
                f"    from(project(':plugins:{host[0]}:{host[1]}').sourceSets.main.output) {{\n"
                + ''.join(f"        include '{p}/**'\n" for p in pkgs(node)) + "    }\n}\n")
    return ''


def write_settings(repo):
    p = repo / 'settings.gradle'
    t = be.read(p)
    t = be.replace(t, "include 'client'\n", "include 'client'\ninclude 'distribution'\n")
    t = t.rstrip('\n') + '''

// plugins/<name>/{common,server,client}: one project per tier that exists
file('plugins').listFiles().sort().findAll { it.directory }.each { plugin ->
    ['common', 'server', 'client'].each { tier ->
        if (new File(plugin, "${tier}/build.gradle").exists()) {
            include ":plugins:${plugin.name}:${tier}"
        }
    }
}
'''
    be.write(p, t)


# ------------------------------------------------------------------ server/client/command/root

def _jar_includes(plan, tier):
    lines = []
    for path, s in sorted((p, next(x for x in plan.sources if x.path == p)) for p in plan.kept):
        if s.tier != tier:
            continue
        cls = re.sub(r'^server/src/main/java/|\.java$', '', path)
        lines.append(f"        include '{cls}.class'\n        include '{cls}$*.class'\n")
    return ''.join(lines)


def rewrite_server(repo, plan):
    """Splits server/build.gradle into the (smaller) server script and the distribution script."""
    text = be.read(repo / 'server/build.gradle')
    sec = be.Sections(text)
    i_ext = sec.title('// Extensions (connectors, datatypes, plugins)')
    i_javadoc = sec.title('// Javadocs (user API)')
    i_setup = sec.title('// Setup assembly')
    i_serverjar = sec.anchor("def serverJar = tasks.register('serverJar', Jar)")
    i_content = sec.anchor('def setupContent = [')
    i_tests = sec.title('// Tests')
    i_dev = sec.title('// Development helpers')
    i_derby = sec.anchor("tasks.register('createDerbyDb'")
    i_flags = sec.anchor('// JDK module flags for running the server')
    i_launcher = sec.anchor("tasks.register('devLauncher'")
    i_devrun = sec.anchor("tasks.register('devRun'")
    i_clean = sec.anchor('clean {')
    n = len(sec.lines)
    assert i_ext < i_javadoc < i_setup < i_serverjar < i_content < i_tests < i_dev < i_derby < i_flags < i_launcher < i_devrun < i_clean

    header = sec.cut(0, i_ext)
    server_jars = sec.cut(i_serverjar, i_content)
    javadoc = sec.cut(i_javadoc, i_setup)
    stage = sec.cut(i_setup, i_serverjar)
    content_to_tests = sec.cut(i_content, i_tests)
    tests = sec.cut(i_tests, i_dev)
    dev_banner = sec.cut(i_dev, i_derby)
    derby = sec.cut(i_derby, i_flags)
    flags = sec.cut(i_flags, i_launcher)
    launcher = sec.cut(i_launcher, i_devrun)
    devrun = sec.cut(i_devrun, i_clean)
    clean = sec.cut(i_clean, n)

    # ---- server
    header = be.replace(header, '    implementation libs.bundles.server.extensions\n', '')
    header = be.replace(header, "// builds every connector/plugin extension into build/extensions,\n"
                                "// assembles the distribution in setup/, and zips extensions into dist/\n"
                                "// (mirrors server/build.xml).\n",
                        "// and runs the server from the development tree. Plugins live in plugins/ and\n"
                        "// the distribution is assembled by the distribution project; nothing here\n"
                        "// knows about either.\n")
    header = be.replace(header, "destinationDirectory = file('setup/server-lib')", "destinationDirectory = layout.buildDirectory.dir('libs')", count=2)
    header = be.replace(header, "        include 'net/lingala/zip4j/unzip/**'\n",
                        "        include 'net/lingala/zip4j/unzip/**'\n"
                        "        // plugin classes core compiles against, see plugins/gaps.md\n"
                        + _jar_includes(plan, 'common'))
    server_jars = be.replace(server_jars, "destinationDirectory = file('setup/server-lib')", "destinationDirectory = layout.buildDirectory.dir('libs')", count=2)
    server_jars = be.replace(server_jars, "destinationDirectory = file('setup')", "destinationDirectory = layout.buildDirectory.dir('libs')")
    server_jars = be.replace(server_jars, "destinationDirectory = file('setup/client-lib')", "destinationDirectory = layout.buildDirectory.dir('libs')")
    server_jars = be.replace(server_jars, "        include 'net/sourceforge/jtds/ssl/**'\n",
                             "        include 'net/sourceforge/jtds/ssl/**'\n"
                             "        // plugin classes core compiles against, see plugins/gaps.md\n"
                             + _jar_includes(plan, 'server'))
    server_jars = be.replace(server_jars, "        exclude 'org/dcm4che2/**'\n", '')

    m = re.search(r"def userutilSourcesJar = tasks\.register\('userutilSourcesJar', Jar\) \{\n.*?\n\}\n", server_jars, re.S)
    if not m:
        raise SystemExit('userutilSourcesJar not found in server/build.gradle')
    userutil_jar = m.group(0)
    server_jars = server_jars.replace(userutil_jar, '').rstrip('\n') + '\n'

    core_deps = _core_test_deps(plan)
    server = (header + '\n' + core_deps + '\n' if core_deps else header + '\n') + server_jars + '\n' \
        + 'assemble.dependsOn serverJar, dbconfJar, launcherJar, cryptoJar, clientCoreJar\n\n' \
        + tests + '\n' + dev_banner + '\n' + flags + '\n' + devrun + '\n'
    server += be.sub(clean, r"delete 'classes', 'test_classes', 'setup', 'dist', 'testdist', 'docs/javadocs', 'logs'",
                     "delete 'classes', 'test_classes', 'testdist', 'logs'")
    # the extension sources, the extension tasks and setup staging are gone from the server script
    for gone in ('createServerExtensions', 'setupContent', 'stageSetup', 'serverExtensions'):
        if re.search(r'\b' + gone + r'\b', server):
            raise SystemExit(f'server/build.gradle still mentions {gone} after the split')
    be.write(repo / 'server/build.gradle', server)

    # ---- distribution
    dist = _distribution(repo, plan, javadoc, stage, content_to_tests, derby, launcher, userutil_jar)
    be.write(repo / 'distribution/build.gradle', dist)


def _core_test_deps(plan):
    out = ''
    libs = [a for a in sorted(plan.core_libs) if not a.startswith('server/')]
    if libs:
        out += ("// Extension libraries that core code compiles against, see plugins/gaps.md\n"
                "dependencies {\n" + ''.join(f"    compileOnly libs.{a.replace('-', '.')}\n" for a in libs) + "}\n")
    if plan.core_test_deps:
        lines = ''.join(f"    testImplementation project(':plugins:{e}:{t}')\n" for e, t in sorted(plan.core_test_deps))
        out += ("\n// Core tests that exercise plugin classes. Test scope only: nothing that ships can see a plugin.\n"
                "dependencies {\n" + lines + "}\n")
    return out


def _distribution_userutil_jar(plan, block):
    """userutil-sources.jar feeds the code template editor: core's userutil packages plus the
    classes that live in plugins under the same package names."""
    block = be.replace(block, "destinationDirectory = file('setup/client-lib')", "destinationDirectory = layout.buildDirectory.dir('libs')") \
        if "destinationDirectory = file('setup/client-lib')" in block else block
    block = be.replace(block, "    from('src/main/java') {", "    from(serverFile('src/main/java')) {")
    extra = ''.join(f"    from(rootProject.file('{root}')) {{ include '{pkgdir}/**.java' }}\n"
                    for _, root, pkgdir, own in plan.userutil if not own)
    block = block.rstrip('\n')
    assert block.endswith('}')
    return ("// Sources of the user API, for the Administrator's code template editor\n"
            + block[:-1] + ("    // classes that moved to plugins but keep their package\n" + extra if extra else '') + '}\n')


def _javadoc_libs(plan):
    aliases = [a for a in sorted(plan.core_libs) if not a.startswith('server/')]
    if not aliases:
        return ''
    deps = ', '.join(f"dependencies.create(libs.{a.replace('-', '.')}.get())" for a in aliases)
    return ("// the extension libraries server compiles against (compileOnly, so not in its runtime inventory)\n"
            f"def javadocLibs = configurations.detachedConfiguration({deps})\n"
            "javadocLibs.transitive = false\n")


def _distribution(repo, plan, javadoc, stage, content_to_tests, derby, launcher, userutil_jar):
    ext_snippet = (TEMPLATES / 'distribution-extensions.gradle').read_text()

    # javadoc: sources/classpath come from the server project, plugin userutil from plugins/
    # a userutil class may use the rest of its plugin (DICOMUtil -> DICOMConverter) and the plugin's jars
    userutil_exts = list(dict.fromkeys(e for e, _, _, _ in plan.userutil))
    roots = [f"rootProject.file('plugins/{e}/{t}/src/main/java')" for e in userutil_exts for t in ('common', 'server')
             if any(v.startswith(f'plugins/{e}/{t}/src/main/java/') for v in plan.moves.values())]
    plugin_libs = [f"rootProject.file('plugins/{e}/lib')" for e in userutil_exts if e in plan.vendored_libs]
    pkgs = [pkgdir for _, _, pkgdir, _ in plan.userutil]
    pkg_roots = [f"rootProject.file('{root}')" for _, root, _, _ in plan.userutil]
    javadoc = be.replace(javadoc, "def outDir = file('docs/javadocs/user-api')", "def outDir = file('build/javadocs/user-api')")
    javadoc = be.sub(javadoc, r"    inputs\.files\(fileTree\('src/main/java'\) \{\n(.*?)        include 'com/mirth/connect/plugins/httpauth/userutil/\*\*'\n    \}\)",
                     lambda m: "    inputs.files(fileTree(serverDir.file('src/main/java')) {\n" + m.group(1) + "    })\n"
                     + ''.join(f"    inputs.files(fileTree({r}) {{ include '{p}/**' }})\n" for r, p in zip(pkg_roots, pkgs)))
    javadoc = be.replace(javadoc, '    dependsOn syncDonkeyLibs\n', "    dependsOn ':server:syncDonkeyLibs', ':server:writeArtifactInventory'\n")
    javadoc = be.replace(javadoc, "(configurations.compileClasspath + fileTree(dir: 'lib', include: '**/*.jar')).asPath", 'javadocClasspath().asPath')
    javadoc = be.replace(javadoc, "file('src/main/java').absolutePath,",
                         "([serverDir.file('src/main/java')] + javadocSourceRoots)*.absolutePath.join(File.pathSeparator),")
    javadoc = be.replace(javadoc, "def userApiJavadoc = tasks.register('userApiJavadoc') {",
                         "// userutil sources shipped by plugins\n"
                         f"def javadocSourceRoots = [{', '.join(dict.fromkeys(roots))}]\n"
                         + _javadoc_libs(plan) +
                         "// the server's resolved runtime artifacts, from the inventory its own project wrote\n"
                         "def javadocClasspath = {\n"
                         "    files(new groovy.json.JsonSlurper().parse(rootProject.file('server/build/placement/artifacts.json'))*.path) +\n"
                         "            fileTree(dir: serverDir.file('lib'), include: '**/*.jar')"
                         + (' +\n            files(javadocLibs)' if _javadoc_libs(plan) else '')
                         + ''.join(f" +\n            fileTree(dir: {l}, include: '*.jar')" for l in plugin_libs) + "\n"
                         "}\n\n"
                         "def userApiJavadoc = tasks.register('userApiJavadoc') {")

    # setup staging
    stage = be.replace(stage, "def serverPlacements = rootProject.stagedArtifacts(configurations.runtimeClasspath, 'server')",
                       "def serverPlacements = rootProject.stagedFromInventory(\n"
                       "                rootProject.file('server/build/placement/artifacts.json'), 'server')")
    stage = be.replace(stage, "            s.from('lib') {", "            s.from(serverDir.file('lib')) {")
    stage = be.sub(stage, r"            s\.preserve \{ p ->\n                p\.include 'mirth-crypto\.jar'\n.*?            \}\n",
                   "            s.from(serverJars)\n")
    stage = be.replace(stage, "            s.from 'conf'\n", "            s.from serverDir.file('conf')\n")
    stage = be.replace(stage, "s.from('public_html') {", "s.from(serverDir.file('public_html')) {")
    stage = be.replace(stage, "s.from('public_api_html') {", "s.from(serverDir.file('public_api_html')) {")
    stage = be.sub(stage, r"            // build/extensions may still hold client jars.*?\n            s\.duplicatesStrategy = DuplicatesStrategy\.INCLUDE\n", '')
    stage = be.replace(stage, "            s.from rootProject.file('client/dist/extensions')\n", '')
    stage = be.replace(stage, "            s.from 'docs'\n", "            s.from serverDir.file('docs')\n"
                       "            s.from(userApiJavadoc) { into 'javadocs/user-api' }\n")
    stage = be.replace(stage, "from('basedir-includes') {", "from(serverDir.file('basedir-includes')) {", count=2)
    stage = be.sub(stage, r"    dependsOn syncDonkeyLibs, createServerExtensions, userApiJavadoc,\n              ':client:assemble',",
                   "    dependsOn ':server:syncDonkeyLibs', ':server:writeArtifactInventory', createExtensions, userApiJavadoc,\n"
                   "              serverJars, ':client:assemble',")
    # the Ant task wrote the launcher straight into setup/; the other core jars are staged above
    stage = stage.rstrip('\n') + '\n'

    content = be.replace(content_to_tests, 'def setupContent = [stageSetup, serverJar, dbconfJar, launcherJar,\n'
                         '                    userutilSourcesJar, cryptoJar, clientCoreJar]\n',
                         'def setupContent = [stageSetup]\n')

    derby = be.replace(derby, "files('setup/server-lib/mirth-server.jar', sourceSets.main.output, 'setup/conf')",
                       "files('setup/server-lib/mirth-server.jar', project(':server').sourceSets.main.output, 'setup/conf')")
    derby = be.replace(derby, "    workingDir = projectDir\n", "    workingDir = serverDir\n")
    derby = be.replace(derby, "    dependsOn serverJar, stageSetup\n    doFirst { delete 'mirthdb' }",
                       "    dependsOn stageSetup\n    doFirst { delete serverDir.file('mirthdb') }")
    launcher = be.replace(launcher, "    dependsOn 'createSetup'\n", "    dependsOn createSetup\n")
    flags = ("// JDK module flags for running the server come from the same file the\n"
             "// production launcher uses, one source of truth.\n"
             "def serverModuleFlags = {\n"
             "    serverDir.file('conf/default_modules.vmoptions').readLines()\n"
             "            .collect { it.trim() }\n"
             "            .findAll { it.startsWith('-') }\n"
             "}\n")

    head = '''// The OIE distribution. Assembles the core jars, the plugins, the client and the CLI
// into setup/, signs the Java Web Start jars and zips the extensions into dist/
// (mirrors server/mirth-build.xml create-setup). Output stays out of the projects
// it is assembled from: server and the plugins only build jars.

def serverDir = project(':server').projectDir
def serverFile = { String path -> project(':server').file(path) }
def projectVersion = version

evaluationDependsOn(':server')
rootProject.subprojects.findAll { it.path.startsWith(':plugins:') && it.childProjects.isEmpty() }
        .each { evaluationDependsOn(it.path) }

// The core jars the setup tree ships in server-lib/.
def serverJars = ['serverJar', 'dbconfJar', 'cryptoJar', 'clientCoreJar'].collect {
    project(':server').tasks.named(it)
}

'''
    # the launcher and userutil sources have their own destinations in the tree
    out = head + _distribution_userutil_jar(plan, userutil_jar) + '\n' + ext_snippet + javadoc + '\n' + stage
    out = be.replace(out, "        copy {\n            from rootProject.file('command/build/mirth-cli-launcher.jar')\n            into 'setup'\n        }\n",
                     "        copy {\n            from rootProject.file('command/build/mirth-cli-launcher.jar')\n            into 'setup'\n        }\n"
                     "        copy {\n            from project(':server').tasks.named('launcherJar')\n            into 'setup'\n        }\n")
    out = be.replace(out, "            s.preserve { p ->\n                p.include 'userutil-sources.jar'\n            }\n",
                     "            s.from(userutilSourcesJar)\n")
    out = be.replace(out, "              serverJars, ':client:assemble',",
                     "              serverJars, project(':server').tasks.named('launcherJar'),\n"
                     "              userutilSourcesJar, ':client:assemble',")
    out += '\n' + content + '\n' + '// ---------------------------------------------------------------------\n' \
        '// Development helpers (create-derby-db, dev-launcher)\n' \
        '// ---------------------------------------------------------------------\n\n' + derby + '\n' + flags + '\n' + launcher
    out += "\nclean {\n    delete 'setup', 'dist', 'build'\n}\n"
    return out.replace('serverDir.file(', 'serverFile(')


def rewrite_client(repo, plan):
    p = repo / 'client/build.gradle'
    t = be.read(p)
    t = be.replace(t, '    // Extension libs from the server tree may carry client-side deps.\n    implementation libs.bundles.server.extensions\n'
                      "    implementation fileTree(dir: rootProject.file('server/lib/extensions'), include: '**/*.jar')\n", '')
    t = be.replace(t, "rootProject.file('server/setup/server-lib/mirth-client-core.jar')", "rootProject.file('server/build/libs/mirth-client-core.jar')")
    t = be.replace(t, "rootProject.file('server/setup/server-lib/mirth-crypto.jar')", "rootProject.file('server/build/libs/mirth-crypto.jar')")
    t = be.sub(t, r"    from\(rootProject\.file\('server/build/extensions'\)\) \{\n.*?\n    \}\n", '')
    t = be.replace(t, "    dependsOn ':donkey:donkeyModelJar', ':server:clientCoreJar', ':server:cryptoJar',\n              ':server:createServerExtensions'\n",
                   "    dependsOn ':donkey:donkeyModelJar', ':server:clientCoreJar', ':server:cryptoJar'\n")
    t = be.sub(t, r"// extension name -> package directory under the classes tree\ndef clientExtensions = \[.*?\n\]\n\ndef clientExtensionJars = .*?\n\}\n\n", '')
    t = be.replace(t, 'assemble.dependsOn clientExtensionJars\n', '')
    t = be.replace(t, "// The Swing administrator client. Produces dist/mirth-client.jar and the\n// per-extension client jars in dist/extensions (mirrors client/ant-build.xml).\n",
                   "// The Swing administrator client. Produces dist/mirth-client.jar; the per-extension\n// client jars are built by plugins/<name>/client.\n")
    be.write(p, t)


def rewrite_command(repo):
    p = repo / 'command/build.gradle'
    t = be.read(p)
    t = be.replace(t, "rootProject.file('server/setup/server-lib/mirth-client-core.jar')", "rootProject.file('server/build/libs/mirth-client-core.jar')")
    t = be.replace(t, "rootProject.file('server/setup/server-lib/mirth-crypto.jar')", "rootProject.file('server/build/libs/mirth-crypto.jar')")
    be.write(p, t)


def rewrite_root(repo):
    p = repo / 'build.gradle'
    t = be.read(p)
    t = be.replace(t, "subprojects {\n    apply plugin: 'java'\n",
                   "subprojects {\n"
                   "    // plugins/ and plugins/<name> only group the tier projects\n"
                   "    if (!childProjects.isEmpty()) {\n"
                   "        apply plugin: 'base'\n"
                   "        return\n"
                   "    }\n\n"
                   "    apply plugin: 'java'\n")
    t = be.replace(t, "if (project.name in ['donkey', 'server', 'client', 'command', 'generator']) {",
                   "if (project.path in [':donkey', ':server', ':client', ':command', ':generator', ':distribution']) {")
    t = be.replace(t, "def sections = (project.name in ['server', 'client']) ? [project.name, 'server-extensions'] : [project.name]",
                   "def sections = project.path.startsWith(':plugins:') ? ['server-extensions'] : [project.name]")
    t = be.replace(t, "s.from 'server/setup/extensions'\n            s.into 'server/build/extensions'", "s.from 'distribution/setup/extensions'\n            s.into 'distribution/build/extensions'")
    t = be.replace(t, "s.from 'server/setup/client-lib'\n            s.into 'server/build/client-lib'", "s.from 'distribution/setup/client-lib'\n            s.into 'distribution/build/client-lib'")
    t = be.replace(t, "dependsOn ':donkey:test', ':server:test', ':client:test', ':command:test'",
                   "dependsOn ':donkey:test', ':server:test', ':client:test', ':command:test', ':plugins:test'", count=2)
    t = be.replace(t, "dependsOn ':donkey:clean', ':server:clean', ':client:clean', ':command:clean', ':generator:clean'",
                   "dependsOn ':donkey:clean', ':server:clean', ':client:clean', ':command:clean', ':generator:clean',\n            ':distribution:clean', ':plugins:clean'")
    t = be.replace(t, 'dependsOn subprojects.collect { ":${it.name}:verifyDistributionPlacement" }',
                   'dependsOn subprojects.findAll { it.childProjects.isEmpty() }.collect { "${it.path}:verifyDistributionPlacement" }')
    be.write(p, t)


_MOVED_TASKS = re.compile(r':server:(createSetup|createExtensionZips|stageSetup|createDerbyDb|devLauncher|userApiJavadoc|signSetupJars)\b')


def sweep_moved_references(repo):
    """Everything that names the moved tasks or output directories: Dockerfile, ignore files, installer,
    docs and any other build script. Binary files and plugins/ (which describes the move) are left alone."""
    tracked = subprocess.run(['git', 'ls-files', '-co', '--exclude-standard'], cwd=repo,
                             capture_output=True, text=True, check=True).stdout.splitlines()
    for name in tracked:
        if name.startswith(('plugins/', 'tools/split-plugins/', 'gradle/')) or not (repo / name).is_file():
            continue
        p = repo / name
        try:
            t = be.read(p)
        except (UnicodeDecodeError, OSError):
            continue
        new = _MOVED_TASKS.sub(r':distribution:\1', t)
        new = re.sub(r'server/setup\b', 'distribution/setup', new)
        new = re.sub(r'server/dist\b', 'distribution/dist', new)
        if new != t:
            be.write(p, new)


def move_signing_files(repo):
    for name in ('keystore.properties', 'keystore.jks', 'custom_manifest.mf'):
        shutil.move(repo / 'server' / name, repo / 'distribution' / name)


def apply(repo, plan):
    check_clean(repo)
    move_files(repo, plan)
    delete_swagger(repo, plan)
    write_plugin_builds(repo, plan)
    write_settings(repo)
    rewrite_server(repo, plan)
    rewrite_client(repo, plan)
    rewrite_command(repo)
    rewrite_root(repo)
    move_signing_files(repo)
    sweep_moved_references(repo)
    be.write(repo / 'plugins/gaps.md', gaps.render(plan, plan_mod.tiers_of(plan)))
