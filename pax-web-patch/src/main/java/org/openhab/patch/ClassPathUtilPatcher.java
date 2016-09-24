/**
 * Copyright (c) 2010-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.patch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * Utility class to patch the pax web ClassPathUtil. After the patch is applied, pax web no longer scans all bundles for
 * services.
 * This avoids a slow startup time on ARM.
 */
public class ClassPathUtilPatcher {

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            System.out.println("Usage: java org.openhab.patch.ClassPathUtilPatcher <jarDirectory>");
        }

        File originalClass = File.createTempFile("originalClass", "tmp");
        originalClass.delete();

        File modifiedClass = File.createTempFile("modifiedClass", "tmp");
        modifiedClass.delete();

        File jarDir = new File(args[0]);
        File jar = findJar(jarDir);

        if (jar == null) {
            throw new RuntimeException("No jar found in " + jarDir.getAbsolutePath());
        }

        // extract class
        FileSystem fileSystem = FileSystems.newFileSystem(jar.toPath(), null);
        Path source = fileSystem.getPath("/org/ops4j/pax/web/utils/ClassPathUtil.class");
        Files.copy(source, originalClass.toPath());

        // alter class
        FileInputStream is = new FileInputStream(originalClass);

        ClassReader cr = new ClassReader(is);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ClassAdapter ca = new ClassAdapter(cw);
        cr.accept(ca, 0);

        FileOutputStream fos = new FileOutputStream(modifiedClass);
        fos.write(cw.toByteArray());
        fos.close();

        // put modified class in archive
        Files.copy(modifiedClass.toPath(), source, StandardCopyOption.REPLACE_EXISTING);

        fileSystem.close();

        originalClass.deleteOnExit();
        modifiedClass.deleteOnExit();

        System.out.println("Finished updating " + jar.getAbsolutePath());
    }

    private static File findJar(File dir) {

        if (dir == null || !dir.isDirectory()) {
            return null;
        }

        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                File f = findJar(file);
                if (f != null) {
                    return f;
                }
            } else if (file.getName().trim().toLowerCase().endsWith(".jar")) {
                return file;
            }
        }

        return null;
    }
}
