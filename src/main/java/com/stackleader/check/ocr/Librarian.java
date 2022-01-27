package com.stackleader.check.ocr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sourceforge.tess4j.util.LoadLibs;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jboss.vfs.VFS;
import static org.jboss.vfs.VFSUtils.VFS_PROTOCOL;
import org.jboss.vfs.VirtualFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 *
 * @author dcnorris
 */
@Component
public class Librarian {

    public static final String LIB_TEMP_DIR = new File(System.getProperty("java.io.tmpdir"), "micro").getPath();

    public static final String DETECTED_NAME = "os.detected.name";
    public static final String DETECTED_ARCH = "os.detected.arch";
    public static final String DETECTED_BITNESS = "os.detected.bitness";
    public static final String DETECTED_VERSION = "os.detected.version";
    public static final String DETECTED_VERSION_MAJOR = DETECTED_VERSION + ".major";
    public static final String DETECTED_VERSION_MINOR = DETECTED_VERSION + ".minor";
    public static final String DETECTED_CLASSIFIER = "os.detected.classifier";
    public static final String DETECTED_RELEASE = "os.detected.release";
    public static final String DETECTED_RELEASE_VERSION = DETECTED_RELEASE + ".version";
    public static final String DETECTED_RELEASE_LIKE_PREFIX = DETECTED_RELEASE + ".like.";

    private static final String UNKNOWN = "unknown";
    private static final String LINUX_ID_PREFIX = "ID=";
    private static final String LINUX_ID_LIKE_PREFIX = "ID_LIKE=";
    private static final String LINUX_VERSION_ID_PREFIX = "VERSION_ID=";
    private static final String[] LINUX_OS_RELEASE_FILES = {"/etc/os-release", "/usr/lib/os-release"};
    private static final String REDHAT_RELEASE_FILE = "/etc/redhat-release";
    private static final String[] DEFAULT_REDHAT_VARIANTS = {"rhel", "fedora"};

    private static final Pattern VERSION_REGEX = Pattern.compile("((\\d+)\\.(\\d+)).*");
    private static final Pattern REDHAT_MAJOR_VERSION_REGEX = Pattern.compile("(\\d+)");

    private static final Logger LOG = LoggerFactory.getLogger(Librarian.class);

    public static void detect(Properties props, List<String> classifierWithLikes) {
        LOG.info("------------------------------------------------------------------------");
        LOG.info("Detecting the operating system and CPU architecture");
        LOG.info("------------------------------------------------------------------------");

        final String osName = getSystemProperty("os.name");
        final String osArch = getSystemProperty("os.arch");
        final String osVersion = getSystemProperty("os.version");

        final String detectedName = normalizeOs(osName);
        final String detectedArch = normalizeArch(osArch);
        final int detectedBitness = determineBitness(detectedArch);

        setProperty(props, DETECTED_NAME, detectedName);
        setProperty(props, DETECTED_ARCH, detectedArch);
        setProperty(props, DETECTED_BITNESS, "" + detectedBitness);

        final Matcher versionMatcher = VERSION_REGEX.matcher(osVersion);
        if (versionMatcher.matches()) {
            setProperty(props, DETECTED_VERSION, versionMatcher.group(1));
            setProperty(props, DETECTED_VERSION_MAJOR, versionMatcher.group(2));
            setProperty(props, DETECTED_VERSION_MINOR, versionMatcher.group(3));
        }

        final String failOnUnknownOS
                = getSystemProperty("failOnUnknownOS");
        if (!"false".equalsIgnoreCase(failOnUnknownOS)) {
            if (UNKNOWN.equals(detectedName)) {
                throw new IllegalStateException("unknown os.name: " + osName);
            }
            if (UNKNOWN.equals(detectedArch)) {
                throw new IllegalStateException("unknown os.arch: " + osArch);
            }
        }

        // Assume the default classifier, without any os "like" extension.
        final StringBuilder detectedClassifierBuilder = new StringBuilder();
        detectedClassifierBuilder.append(detectedName);
        detectedClassifierBuilder.append('-');
        detectedClassifierBuilder.append(detectedArch);

        // For Linux systems, add additional properties regarding details of the OS.
        final LinuxRelease linuxRelease = "linux".equals(detectedName) ? getLinuxRelease() : null;
        if (linuxRelease != null) {
            setProperty(props, DETECTED_RELEASE, linuxRelease.id);
            if (linuxRelease.version != null) {
                setProperty(props, DETECTED_RELEASE_VERSION, linuxRelease.version);
            }

            // Add properties for all systems that this OS is "like".
            for (String like : linuxRelease.like) {
                final String propKey = DETECTED_RELEASE_LIKE_PREFIX + like;
                setProperty(props, propKey, "true");
            }

            // If any of the requested classifier likes are found in the "likes" for this system,
            // append it to the classifier.
            for (String classifierLike : classifierWithLikes) {
                if (linuxRelease.like.contains(classifierLike)) {
                    detectedClassifierBuilder.append('-');
                    detectedClassifierBuilder.append(classifierLike);
                    // First one wins.
                    break;
                }
            }
        }
        setProperty(props, DETECTED_CLASSIFIER, detectedClassifierBuilder.toString());
    }

    private static void setProperty(Properties props, String name, String value) {
        props.setProperty(name, value);
        setSystemProperty(name, value);
        logProperty(name, value);
    }

    private static String getSystemProperty(String name) {
        return System.getProperty(name);
    }

    private static String getSystemProperty(String name, String def) {
        return System.getProperty(name, def);
    }

    private static String setSystemProperty(String name, String value) {
        return System.setProperty(name, value);
    }

    public static synchronized File extractTessResources(String resourceName) {
        File targetPath = null;

        try {
            targetPath = new File(LIB_TEMP_DIR, resourceName);

            Enumeration<URL> resources = LoadLibs.class.getClassLoader().getResources("lib/" + resourceName);
            while (resources.hasMoreElements()) {
                URL resourceUrl = resources.nextElement();
                copyResources(resourceUrl, targetPath);
            }
        } catch (IOException | URISyntaxException e) {
            LOG.warn(e.getMessage(), e);
        }

        return targetPath;
    }

    /**
     * Copies resources to target folder.
     *
     * @param resourceUrl
     * @param targetPath
     * @return
     */
    static void copyResources(URL resourceUrl, File targetPath) throws IOException, URISyntaxException {
        if (resourceUrl == null) {
            return;
        }

        URLConnection urlConnection = resourceUrl.openConnection();

        /**
         * Copy resources either from inside jar or from project folder.
         */
        if (urlConnection instanceof JarURLConnection) {
            copyJarResourceToPath((JarURLConnection) urlConnection, targetPath);
        } else if (VFS_PROTOCOL.equals(resourceUrl.getProtocol())) {
            VirtualFile virtualFileOrFolder = VFS.getChild(resourceUrl.toURI());
            copyFromWarToFolder(virtualFileOrFolder, targetPath);
        } else {
            File file = new File(resourceUrl.getPath());
            if (file.isDirectory()) {
                for (File resourceFile : FileUtils.listFiles(file, null, true)) {
                    int index = resourceFile.getPath().lastIndexOf(targetPath.getName()) + targetPath.getName().length();
                    File targetFile = new File(targetPath, resourceFile.getPath().substring(index));
                    if (!targetFile.exists() || targetFile.length() != resourceFile.length() || targetFile.lastModified() != resourceFile.lastModified()) {
                        if (resourceFile.isFile()) {
                            FileUtils.copyFile(resourceFile, targetFile, true);
                        }
                    }
                }
            } else {
                if (!targetPath.exists() || targetPath.length() != file.length() || targetPath.lastModified() != file.lastModified()) {
                    FileUtils.copyFile(file, targetPath, true);
                }
            }
        }
    }

    /**
     * Copies resources from the jar file of the current thread and extract it
     * to the destination path.
     *
     * @param jarConnection
     * @param destPath destination file or directory
     */
    static void copyJarResourceToPath(JarURLConnection jarConnection, File destPath) {
        try (JarFile jarFile = jarConnection.getJarFile()) {
            String jarConnectionEntryName = jarConnection.getEntryName();
            if (!jarConnectionEntryName.endsWith("/")) {
                jarConnectionEntryName += "/";
            }

            /**
             * Iterate all entries in the jar file.
             */
            for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
                JarEntry jarEntry = e.nextElement();
                String jarEntryName = jarEntry.getName();

                /**
                 * Extract files only if they match the path.
                 */
                if (jarEntryName.startsWith(jarConnectionEntryName)) {
                    String filename = jarEntryName.substring(jarConnectionEntryName.length());
                    File targetFile = new File(destPath, filename);

                    if (jarEntry.isDirectory()) {
                        targetFile.mkdirs();
                    } else {
                        if (!targetFile.exists() || targetFile.length() != jarEntry.getSize()) {
                            try (InputStream is = jarFile.getInputStream(jarEntry);
                                    OutputStream out = FileUtils.openOutputStream(targetFile)) {
                                IOUtils.copy(is, out);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn(e.getMessage(), e);
        }
    }

    static void copyFromWarToFolder(VirtualFile virtualFileOrFolder, File targetFolder) throws IOException {
        if (virtualFileOrFolder.isDirectory() && !virtualFileOrFolder.getName().contains(".")) {
            if (targetFolder.getName().equalsIgnoreCase(virtualFileOrFolder.getName())) {
                for (VirtualFile innerFileOrFolder : virtualFileOrFolder.getChildren()) {
                    copyFromWarToFolder(innerFileOrFolder, targetFolder);
                }
            } else {
                File innerTargetFolder = new File(targetFolder, virtualFileOrFolder.getName());
                innerTargetFolder.mkdir();
                for (VirtualFile innerFileOrFolder : virtualFileOrFolder.getChildren()) {
                    copyFromWarToFolder(innerFileOrFolder, innerTargetFolder);
                }
            }
        } else {
            File targetFile = new File(targetFolder, virtualFileOrFolder.getName());
            if (!targetFile.exists() || targetFile.length() != virtualFileOrFolder.getSize()) {
                FileUtils.copyURLToFile(virtualFileOrFolder.asFileURL(), targetFile);
            }
        }
    }

    private static void logProperty(String name, String value) {
        LOG.info("{} detected value = {}", name, value);
    }

    private static int determineBitness(String architecture) {
        // try the widely adopted sun specification first.
        String bitness = getSystemProperty("sun.arch.data.model", "");

        if (!bitness.isEmpty() && bitness.matches("[0-9]+")) {
            return Integer.parseInt(bitness, 10);
        }

        // bitness from sun.arch.data.model cannot be used. Try the IBM specification.
        bitness = getSystemProperty("com.ibm.vm.bitmode", "");

        if (!bitness.isEmpty() && bitness.matches("[0-9]+")) {
            return Integer.parseInt(bitness, 10);
        }

        // as a last resort, try to determine the bitness from the architecture.
        return guessBitnessFromArchitecture(architecture);
    }

    public static int guessBitnessFromArchitecture(final String arch) {
        if (arch.contains("64")) {
            return 64;
        }

        return 32;
    }

    private static class LinuxRelease {

        final String id;
        final String version;
        final Collection<String> like;

        LinuxRelease(String id, String version, Set<String> like) {
            this.id = id;
            this.version = version;
            this.like = Collections.unmodifiableCollection(like);
        }
    }

    private static String normalizeOs(String value) {
        value = normalize(value);
        if (value.startsWith("aix")) {
            return "aix";
        }
        if (value.startsWith("hpux")) {
            return "hpux";
        }
        if (value.startsWith("os400")) {
            // Avoid the names such as os4000
            if (value.length() <= 5 || !Character.isDigit(value.charAt(5))) {
                return "os400";
            }
        }
        if (value.startsWith("linux")) {
            return "linux";
        }
        if (value.startsWith("macosx") || value.startsWith("osx")) {
            return "osx";
        }
        if (value.startsWith("freebsd")) {
            return "freebsd";
        }
        if (value.startsWith("openbsd")) {
            return "openbsd";
        }
        if (value.startsWith("netbsd")) {
            return "netbsd";
        }
        if (value.startsWith("solaris") || value.startsWith("sunos")) {
            return "sunos";
        }
        if (value.startsWith("windows")) {
            return "windows";
        }
        if (value.startsWith("zos")) {
            return "zos";
        }

        return UNKNOWN;
    }

    private static String normalizeArch(String value) {
        value = normalize(value);
        if (value.matches("^(x8664|amd64|ia32e|em64t|x64)$")) {
            return "x86_64";
        }
        if (value.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) {
            return "x86_32";
        }
        if (value.matches("^(ia64w?|itanium64)$")) {
            return "itanium_64";
        }
        if ("ia64n".equals(value)) {
            return "itanium_32";
        }
        if (value.matches("^(sparc|sparc32)$")) {
            return "sparc_32";
        }
        if (value.matches("^(sparcv9|sparc64)$")) {
            return "sparc_64";
        }
        if (value.matches("^(arm|arm32)$")) {
            return "arm_32";
        }
        if ("aarch64".equals(value)) {
            return "aarch_64";
        }
        if (value.matches("^(mips|mips32)$")) {
            return "mips_32";
        }
        if (value.matches("^(mipsel|mips32el)$")) {
            return "mipsel_32";
        }
        if ("mips64".equals(value)) {
            return "mips_64";
        }
        if ("mips64el".equals(value)) {
            return "mipsel_64";
        }
        if (value.matches("^(ppc|ppc32)$")) {
            return "ppc_32";
        }
        if (value.matches("^(ppcle|ppc32le)$")) {
            return "ppcle_32";
        }
        if ("ppc64".equals(value)) {
            return "ppc_64";
        }
        if ("ppc64le".equals(value)) {
            return "ppcle_64";
        }
        if ("s390".equals(value)) {
            return "s390_32";
        }
        if ("s390x".equals(value)) {
            return "s390_64";
        }
        if ("riscv".equals(value)) {
            return "riscv";
        }
        if ("e2k".equals(value)) {
            return "e2k";
        }
        return UNKNOWN;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
    }

    private static LinuxRelease getLinuxRelease() {
        // First, look for the os-release file.
        for (String osReleaseFileName : LINUX_OS_RELEASE_FILES) {
            LinuxRelease res = parseLinuxOsReleaseFile(osReleaseFileName);
            if (res != null) {
                return res;
            }
        }

        // Older versions of redhat don't have /etc/os-release. In this case, try
        // parsing this file.
        return parseLinuxRedhatReleaseFile(REDHAT_RELEASE_FILE);
    }

    /**
     * Parses a file in the format of {@code /etc/os-release} and return a {@link LinuxRelease}
     * based on the {@code ID}, {@code ID_LIKE}, and {@code VERSION_ID} entries.
     */
    private static LinuxRelease parseLinuxOsReleaseFile(String fileName) {
        try (InputStream in = new FileInputStream(fileName);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, "utf-8"));) {

            String id = null;
            String version = null;
            final Set<String> likeSet = new LinkedHashSet<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                // Parse the ID line.
                if (line.startsWith(LINUX_ID_PREFIX)) {
                    // Set the ID for this version.
                    id = normalizeOsReleaseValue(line.substring(LINUX_ID_PREFIX.length()));

                    // Also add the ID to the "like" set.
                    likeSet.add(id);
                    continue;
                }

                // Parse the VERSION_ID line.
                if (line.startsWith(LINUX_VERSION_ID_PREFIX)) {
                    // Set the ID for this version.
                    version = normalizeOsReleaseValue(line.substring(LINUX_VERSION_ID_PREFIX.length()));
                    continue;
                }

                // Parse the ID_LIKE line.
                if (line.startsWith(LINUX_ID_LIKE_PREFIX)) {
                    line = normalizeOsReleaseValue(line.substring(LINUX_ID_LIKE_PREFIX.length()));

                    // Split the line on any whitespace.
                    final String[] parts = line.split("\\s+");
                    Collections.addAll(likeSet, parts);
                }
            }

            if (id != null) {
                return new LinuxRelease(id, version, likeSet);
            }
        } catch (IOException ignored) {
            // Just absorb. Don't treat failure to read /etc/os-release as an error.
        }
        return null;
    }

    private static String normalizeOsReleaseValue(String value) {
        // Remove any quotes from the string.
        return value.trim().replace("\"", "");
    }

    /**
     * Parses the {@code /etc/redhat-release} and returns a {@link LinuxRelease} containing the
     * ID and like ["rhel", "fedora", ID]. Currently only supported for CentOS, Fedora, and RHEL.
     * Other variants will return {@code null}.
     */
    private static LinuxRelease parseLinuxRedhatReleaseFile(String fileName) {
        try (InputStream in = new FileInputStream(fileName);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, "utf-8"));) {
            String line = reader.readLine();
            if (line != null) {
                line = line.toLowerCase(Locale.US);

                final String id;
                String version = null;
                if (line.contains("centos")) {
                    id = "centos";
                } else if (line.contains("fedora")) {
                    id = "fedora";
                } else if (line.contains("red hat enterprise linux")) {
                    id = "rhel";
                } else {
                    // Other variants are not currently supported.
                    return null;
                }

                final Matcher versionMatcher = REDHAT_MAJOR_VERSION_REGEX.matcher(line);
                if (versionMatcher.find()) {
                    version = versionMatcher.group(1);
                }

                final Set<String> likeSet = new LinkedHashSet<String>(Arrays.asList(DEFAULT_REDHAT_VARIANTS));
                likeSet.add(id);

                return new LinuxRelease(id, version, likeSet);
            }
        } catch (IOException ignored) {
            // Just absorb. Don't treat failure to read /etc/os-release as an error.
        }

        return null;
    }

}
