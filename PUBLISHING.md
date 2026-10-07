# Publishing to Maven Central

Notes for maintainers. Everything here is done once, except the release itself.

## Read this first

**A published version is permanent.** Maven Central never lets you replace or delete
a release. If `0.1.0` ships with a mistake, the fix is `0.1.1` — you cannot take
`0.1.0` back. Run the release with that in mind, and never publish from a branch that
has not been merged and tagged.

## Before the first release

The project is already wired for Central: `pom.xml` carries the required metadata
(`name`, `description`, `url`, `licenses`, `developers`, `scm`) and a `release`
profile that attaches sources, javadoc, signatures and the upload plugin. What
follows is the account setup, which only you can do.

### 1. Claim the namespace

Sign in at <https://central.sonatype.com> — use GitHub, so the account is tied to the
same identity as the `io.github.navidzaare` group ID.

Add the namespace `io.github.navidzaare`. The portal verifies ownership of a GitHub
namespace either automatically from your sign-in or by asking you to create a public
repository with a name it gives you. Either way, wait until the namespace shows as
**Verified** before continuing.

### 2. Create a user token

In the portal: **Account → User Token → Generate**.

Save both halves. This is a generated username/password pair, *not* your portal login.
An old OSSRH token will not work — the legacy service was shut down in June 2025.

### 3. Create a signing key

Central requires every artifact to be GPG-signed, and the public key to be on a
keyserver so it can verify them.

```bash
gpg --full-generate-key          # RSA, 4096, sign-only is fine
gpg --list-secret-keys --keyid-format LONG   # note the key id
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
```

Export the private key for GitHub, in ASCII armor:

```bash
gpg --armor --export-secret-keys YOUR_KEY_ID > flux-signing-key.asc
```

That file is a secret. Delete it once it is in GitHub.

### 4. Add the repository secrets

**Settings → Secrets and variables → Actions → New repository secret:**

| Secret | Value |
|---|---|
| `CENTRAL_TOKEN_USERNAME` | token username from step 2 |
| `CENTRAL_TOKEN_PASSWORD` | token password from step 2 |
| `GPG_PRIVATE_KEY` | full contents of `flux-signing-key.asc`, including the BEGIN/END lines |
| `GPG_PASSPHRASE` | the passphrase you gave the key |

## Releasing

1. Make sure `main` is green and the version in `pom.xml` is the one you intend to
   publish, with no `-SNAPSHOT` suffix.
2. Tag the exact commit — `git tag -a v0.1.0 -m "..."` — and push the tag.
3. **Actions → release → Run workflow**.
4. Watch it in the portal at <https://central.sonatype.com/publishing/deployments>.

The workflow runs the tests, builds sources and javadoc, signs everything, and uploads
the whole reactor as one bundle. It uses `AUTOMATIC` publishing, so a clean run lands
the artifacts without a further click.

Once it finishes, confirm the coordinates resolve:

```
https://repo.maven.apache.org/maven2/io/github/navidzaare/flux-spring-boot-starter/0.1.0/
```

Sync to Maven Central proper can lag the portal by a few minutes. That is normal.

## For a later release

- Bump `<version>` in the root `pom.xml` (all modules inherit it) and update
  `CHANGELOG.md`.
- Update `<scm><tag>` in the root pom to the new tag, so the published POM points at
  the right source.
- Re-run the workflow. The secrets do not change.

## Notes for this machine

The Maven settings at `~/.m2/settings.xml` point at a corporate mirror
(`art.behsacorp.com`) that is unreachable off the office network. Local builds should
use the Central-only settings instead:

```bash
./mvnw -s ~/.m2/settings-personal.xml clean verify
```

A backup of the corporate file sits at `~/.m2/settings.xml.behsa-backup`. GitHub
runners have none of this — they resolve Maven Central directly.

## Troubleshooting

| Symptom | Cause |
|---|---|
| `401` on deploy | using an OSSRH token, or the account password instead of a user token |
| `Invalid signature` | the public key was never sent to a keyserver, or the wrong key signed |
| `Missing sources/javadoc` | a module with no `src/main/java`; see `flux-spring-boot-starter` for the `package-info.java` fix |
| Namespace not verified | the verification repository the portal asked for was not created, or was made private |

### Two lines in the release log that are safe to ignore

```
[ERROR] MavenReportException: Error while generating Javadoc:
[ERROR] Error fetching link: ...\flux-spring-boot-starter\target\apidocs. Ignored it.
```

`flux-spring-boot-starter` has no classes, only a package description, so javadoc has
nothing to document and reports an error. It is tolerated for that module alone, and
the build still ends in `BUILD SUCCESS` with all three jars attached. Spring Boot's own
source-less starters ship empty sources and javadoc jars for the same reason.

A real javadoc error anywhere else still fails the release — only this module is
configured to tolerate it.
