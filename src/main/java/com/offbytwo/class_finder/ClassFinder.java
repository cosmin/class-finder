package com.offbytwo.class_finder;

/*
 * ClassFinder.java
 */

import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


public class ClassFinder {
    public static final List<String> PACKAGES_TO_IGNORE = Arrays.asList("sun.", "com.sun.", "java.beans");

    private NavigableSet<String> allClasses = new TreeSet<String>();
    private NavigableMap<String, NavigableSet<String>> packagesByClassName = new TreeMap<String, NavigableSet<String>>();
    private NavigableMap<String, NavigableSet<String>> classesByPackageName = new TreeMap<String, NavigableSet<String>>();

    private Logger logger;

    public ClassFinder() {
        logger = Logger.getLogger(this.getClass());

        final long startTime = System.nanoTime();

        for (String fullClassName : findAllClassesInClasspath()) {
            int lastIndex = fullClassName.lastIndexOf(".");

            String className;
            String packageName;

            if (lastIndex > 0) {
                className = fullClassName.substring(lastIndex + 1);
                packageName = fullClassName.substring(0, lastIndex);
            } else {
                className = fullClassName;
                packageName = "";
            }

            if (className.startsWith("_")) {
                continue;
            }

            allClasses.add(fullClassName);

            boolean skip = false;

            for (String ignore : PACKAGES_TO_IGNORE) {
                if (packageName.startsWith(ignore)) {
                    skip = true;
                }
            }

            if (skip) {
                continue;
            }

            if (!packagesByClassName.containsKey(className)) {
                packagesByClassName.put(className, new TreeSet<String>());
            }
            packagesByClassName.get(className).add(packageName);

            if (!classesByPackageName.containsKey(packageName)) {
                classesByPackageName.put(packageName, new TreeSet<String>());
            }
            classesByPackageName.get(packageName).add(className);
        }

        final long endTime = System.nanoTime();
        final long duration = endTime - startTime;


        logger.info(String.format("ClassFinder took %f seconds", duration / 1000000000.0));
    }

    protected List<String> findAllClassesInClasspath() {
        ArrayList<String> classes = new ArrayList<String>();

        for (File file : getClasspathLocationsThatExist()) {
            if (file.isDirectory()) {
                classes.addAll(getClassesInDirectory(file));
            } else if (file.getName().endsWith(".jar")) {
                classes.addAll(getClassesInJar(file));
            }
        }

        return classes;
    }

    private List<String> getClassesInJar(File file) {
        logger.debug("Getting classes in jar file " + file);
        List<String> classes = new ArrayList<String>();

        try {
            JarFile jar = new JarFile(file);

            // TODO: handle items added to classpath by the jar
            // String jarClassPath = jar.getManifest().getMainAttributes().getValue("Class-Path");

            Enumeration<JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                JarEntry entry = e.nextElement();
                if (entry.isDirectory()) {
                    if (entry.getName().toUpperCase().equals("META-INF/")) continue;
                } else if (entry.getName().endsWith(".class")) {
                    String className = fullClassNameFromJarEntry(entry);
                    classes.add(className);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return classes;
        }
        return classes;
    }

    public final List<File> getClasspathLocationsThatExist() {
        List<File> locations = new ArrayList<File>();

        ClassLoader currentClassLoader = this.getClass().getClassLoader();

        while (currentClassLoader.getParent() != null && currentClassLoader.getParent() != currentClassLoader) {
            logger.debug("Looking at class loader of type: " + currentClassLoader.getClass());
            if (currentClassLoader instanceof URLClassLoader) {
                locations.addAll(getLocationsThatExistFromURLClassLoader((URLClassLoader) currentClassLoader));
            } else if (currentClassLoader == ClassLoader.getSystemClassLoader()) {
                locations.addAll(getFilesFromClassPath(System.getProperty("java.class.path")));
            } else {
                throw new RuntimeException(currentClassLoader.getClass().getName());
            }
            currentClassLoader = currentClassLoader.getParent();
        }
        locations.addAll(getFilesFromClassPath(getBootClassPath()));

        if (logger.isInfoEnabled()) {
            for (File file : locations) {
                logger.info(String.format("Adding %s to class finder.", file));
            }
        }
        return locations;
    }

    private List<File> getLocationsThatExistFromURLClassLoader(URLClassLoader currentClassLoader) {
        List<File> urlClassLoaderFiles = new ArrayList<File>();
        for (URL url : currentClassLoader.getURLs()) {
            if (url.getProtocol().equals("file")) {
                String filename = url.getFile();
                File file = new File(filename);
                if (file.exists()) {
                    urlClassLoaderFiles.add(file);
                }
            } else {
                logger.warn("Only file:// urls are currently supported. Found: " + url.getProtocol());
            }
        }

        return urlClassLoaderFiles;
    }

    private List<File> getFilesFromClassPath(String classpath) {
        List<File> locations = new ArrayList<File>();

        if (isBlank(classpath)) {
            return locations;
        }

        for (String path : classpath.split(System.getProperty("path.separator"))) {
            File file = new File(path);
            if (!file.exists()) {
                continue;
            }
            locations.add(file);
        }

        return locations;
    }

    private String getBootClassPath() {
        return System.getProperty("sun.boot.class.path");
    }

    public List<String> getClassesInDirectory(File startingWithDirectory) {
        logger.debug("Getting classes in directory " + startingWithDirectory.getAbsolutePath());
        List<String> classes = new ArrayList<String>();

        Set<String> seen = new HashSet<String>();
        seen.add(startingWithDirectory.getAbsolutePath());

        Queue<File> directoriesToScan = new ArrayDeque<File>();
        directoriesToScan.add(startingWithDirectory);

        File directory;

        while ((directory = directoriesToScan.poll()) != null) {
            File[] files = directory.listFiles();
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".class")) {
                    String pathToClass = file.getAbsolutePath().replace(startingWithDirectory.getAbsolutePath(), "");
                    String className = getClassNameFromPath(pathToClass);
                    logger.debug("Found " + className);
                    classes.add(className);
                } else if (file.isDirectory() && !seen.contains(file.getAbsolutePath())) {
                    seen.add(file.getAbsolutePath());
                    directoriesToScan.add(file);
                }
            }
        }

        return classes;
    }

    public static String fullClassNameFromJarEntry(JarEntry entry) {
        if (entry == null || isBlank(entry.getName())) {
            return null;
        }

        String s = entry.getName();

        return getClassNameFromPath(s);
    }

    private static String getClassNameFromPath(String s) {
        if (s.startsWith("/")) s = s.substring(1);
        if (s.endsWith(".class")) s = s.substring(0, s.length() - 6);

        return s.replace('/', '.');
    }

    public Set<String> getAllClassNames() {
        return packagesByClassName.keySet();
    }

    public Set<String> findClassesByName(String className) {
        Set<String> matches = new TreeSet<String>();

        if (packagesByClassName.containsKey(className)) {
            for (String packageName : packagesByClassName.get(className)) {
                matches.add(packageName + "." + className);
            }
        }

        return matches;
    }

    public Set<String> findClassesByGlob(String glob) {
        Set<String> matches = new TreeSet<String>();

        String regex = convertGlobToRegEx(glob);
        for (String className : packagesByClassName.keySet()) {
            if (className.matches(regex)) {
                for (String packageName : packagesByClassName.get(className)) {
                    matches.add(packageName + "." + className);
                }
            }
        }

        return matches;
    }

    public Set<String> findFQClassesStartingWith(String prefix) {
        return allClasses.subSet(prefix, prefix + "\uffff");
    }

    public Set<String> findClassesInPackageByName(String packageName, String className) {
        Set<String> matches = new TreeSet<String>();

        if (!isBlank(packageName)) {
            for (String match : findClassesByName(className)) {
                if (match.startsWith(packageName)) {
                    matches.add(match);
                }
            }
        } else {
            matches.addAll(findClassesByName(className));
        }

        return matches;
    }

    public Set<String> findClassesInPackageByGlob(String packageName, String glob) {
        Set<String> matches = new TreeSet<String>();

        if (!isBlank(packageName)) {
            for (String match : findClassesByGlob(glob)) {
                if (match.startsWith(packageName)) {
                    matches.add(match);
                }
            }
        } else {
            matches.addAll(findClassesByGlob(glob));
        }

        return matches;
    }

    private String convertGlobToRegEx(String line) {
        line = line.trim();
        int strLen = line.length();
        StringBuilder sb = new StringBuilder(strLen);

        boolean escaping = false;
        int inCurlies = 0;
        for (char currentChar : line.toCharArray()) {
            switch (currentChar) {
                case '*':
                    if (escaping)
                        sb.append("\\*");
                    else
                        sb.append(".*");
                    escaping = false;
                    break;
                case '?':
                    if (escaping)
                        sb.append("\\?");
                    else
                        sb.append('.');
                    escaping = false;
                    break;
                case '.':
                case '(':
                case ')':
                case '+':
                case '|':
                case '^':
                case '$':
                case '@':
                case '%':
                    sb.append('\\');
                    sb.append(currentChar);
                    escaping = false;
                    break;
                case '\\':
                    if (escaping) {
                        sb.append("\\\\");
                        escaping = false;
                    } else
                        escaping = true;
                    break;
                case '{':
                    if (escaping) {
                        sb.append("\\{");
                    } else {
                        sb.append('(');
                        inCurlies++;
                    }
                    escaping = false;
                    break;
                case '}':
                    if (inCurlies > 0 && !escaping) {
                        sb.append(')');
                        inCurlies--;
                    } else if (escaping)
                        sb.append("\\}");
                    else
                        sb.append("}");
                    escaping = false;
                    break;
                case ',':
                    if (inCurlies > 0 && !escaping) {
                        sb.append('|');
                    } else if (escaping)
                        sb.append("\\,");
                    else
                        sb.append(",");
                    break;
                default:
                    escaping = false;
                    sb.append(currentChar);
            }
        }
        return sb.toString();
    }

    private static boolean isBlank(String classpath) {
        return classpath == null || classpath.length() == 0;
    }

    public NavigableMap<String, NavigableSet<String>> getClassesByPackageName() {
        return classesByPackageName;
    }

    public Set<String> findPackagesStartingWith(String prefix) {
        return classesByPackageName.subMap(prefix, prefix + "\uffff").keySet();
    }
}
