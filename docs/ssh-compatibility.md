# SSH compatibility gate

## Selected client stack

- JGit `5.13.0.202109080827-r`
- JGit JSch transport `5.13.0.202109080827-r`
- maintained JSch `2.28.6` replacing the transitive unmaintained JSch artifact
- Bouncy Castle `1.85` for Ed25519 on Android runtimes without the required JCE implementation

The client session factory is attached to each JGit `SshTransport`; the JVM-global session factory is not changed.

## Verified behavior

The JVM compatibility fixture runs a real SSH Git server backed by JGit upload-pack and receive-pack. It verifies:

- RSA 3072 OpenSSH identity: unknown-host rejection, explicit trust, clone, push, pull, and changed-host rejection
- Ed25519 OpenSSH identity: unknown-host rejection, explicit trust, clone, push, and pull
- encrypted OpenSSH RSA identity: correct passphrase accepted and incorrect passphrase rejected
- host-key rotation: a previously accepted host repository returns `CHANGED`

Android instrumentation verifies RSA 3072, Ed25519, and encrypted OpenSSH identities can be generated, parsed, and decrypted on the supported phone emulator runtime. An opt-in connected test against the private GitLab fixture at its configured non-default SSH port verifies unknown-host rejection, explicit trust, clone, unique-branch push, a second clone/push, pull, and remote branch cleanup with the RSA fixture identity. The private key is injected into debug app-private storage for the test and removed from both app storage and `/data/local/tmp` immediately afterward; it is never committed or packaged in the APK.

## Security constraints

- `StrictHostKeyChecking` remains enabled.
- Unknown and changed host keys are never accepted by the compatibility factory.
- Key bytes and passphrases are byte arrays and are overwritten after tests.
- RSA uses SHA-2 capable maintained JSch algorithms; obsolete `ssh-rsa` is not enabled as a compatibility override.
- Apache MINA sshd is a test-server dependency only. It was not selected as the production client because the preferred JSch backend passed dependency, build, D8, RSA, Ed25519, encrypted-key, and host-verification checks.
