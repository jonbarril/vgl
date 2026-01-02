package com.vgl.cli.test.utils;

import com.vgl.cli.commands.CreateCommand;
import com.vgl.cli.utils.VglConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.RefSpec;

public final class RepoTestUtils {
    private RepoTestUtils() {}

    public static void createVglRepo(Path repoDir) throws Exception {
        try (StdIoCapture ignored = new StdIoCapture()) {
            new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"));
        }
    }

    public static void seedEmptyCommit(Path repoDir, String message) throws Exception {
        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setAllowEmpty(true).setMessage(message).setAuthor(ident).setCommitter(ident).call();
        }
    }

    public static void writeFile(Path repoDir, String relativePath, String content) throws Exception {
        Path p = repoDir.resolve(relativePath);
        Path parent = p.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    public static String readFile(Path repoDir, String relativePath) throws Exception {
        return Files.readString(repoDir.resolve(relativePath), StandardCharsets.UTF_8);
    }

    public static Path initBareRemote(Path remoteDir) throws Exception {
        Files.createDirectories(remoteDir);
        // Ensure the bare repo has a valid default branch so clones/fetches work reliably.
        try (Git ignored = Git.init().setBare(true).setInitialBranch("main").setDirectory(remoteDir.toFile()).call()) {
            // initialized
        }
        return remoteDir;
    }

    /**
     * Creates a bare repo at {@code remoteDir} and seeds it with one commit on {@code branch}.
     */
    public static void initBareRemoteWithSeedCommit(Path tmpRoot, Path remoteDir, String branch) throws Exception {
        initBareRemote(remoteDir);

        Path seed = tmpRoot.resolve("seed");
        Files.createDirectories(seed);
        try (Git git = Git.init().setInitialBranch(branch).setDirectory(seed.toFile()).call()) {
            writeFile(seed, "seed.txt", "seed\n");
            git.add().addFilepattern("seed.txt").call();
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setMessage("seed").setAuthor(ident).setCommitter(ident).call();

            git.remoteAdd().setName("origin").setUri(new org.eclipse.jgit.transport.URIish(remoteDir.toUri().toString())).call();
            git.push()
                .setRemote("origin")
                .setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/heads/" + branch))
                .call();
        }
    }

    public static void setVglRemote(Path repoDir, Path remoteDir, String remoteBranch) throws Exception {
        String url = remoteDir.toUri().toString();
        VglConfig.writeProps(repoDir, props -> {
            props.setProperty(VglConfig.KEY_REMOTE_URL, url);
            props.setProperty(VglConfig.KEY_REMOTE_BRANCH, remoteBranch);
        });
    }

    public static Properties readVglProps(Path repoDir) {
        return VglConfig.readProps(repoDir);
    }
}
