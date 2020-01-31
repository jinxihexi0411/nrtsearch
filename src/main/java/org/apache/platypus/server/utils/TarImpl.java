/*
 *
 *  *
 *  *  Copyright 2019 Yelp Inc.
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  *  either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *  *
 *
 *
 */

package org.apache.platypus.server.utils;

import com.google.inject.Inject;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TarImpl implements Tar {
    @Inject
    public TarImpl() {

    }

    @Override
    public void extractTar(Path sourceFile, Path destDir) throws IOException {
        try (
                final FileInputStream fileInputStream = new FileInputStream(sourceFile.toFile());
                final GzipCompressorInputStream gzipCompressorInputStream = new GzipCompressorInputStream(fileInputStream, true);
                final TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(gzipCompressorInputStream);

        ) {
            extractTar(tarArchiveInputStream, destDir);
        }

    }

    @Override
    public void extractTar(final TarArchiveInputStream tarArchiveInputStream, final Path destDirectory) throws IOException {
        for (TarArchiveEntry tarArchiveEntry = tarArchiveInputStream.getNextTarEntry();
             tarArchiveEntry != null;
             tarArchiveEntry = tarArchiveInputStream.getNextTarEntry()) {
            String filename = tarArchiveEntry.getName();
            final Path destFile = destDirectory.resolve(filename);

            if (tarArchiveEntry.isDirectory()) {
                continue;
            }

            final Path parentDirectory = destFile.getParent();
            if (!Files.exists(parentDirectory)) {
                Files.createDirectories(parentDirectory);
            }
            try (FileOutputStream fileOutputStream = new FileOutputStream(destFile.toFile())) {
                IOUtils.copy(tarArchiveInputStream, fileOutputStream);
            }
        }
    }

    private static String fixNaming(String resource, String path) {
        String resourcePath = resource + "/";
        if (path.startsWith(resourcePath)) {
            return path.substring(resourcePath.length());
        }
        throw new IllegalArgumentException("File name doesn't match expected Archiver pattern: " + path);
    }

    @Override
    public void buildTar(Path sourceDir, Path destinationFile) throws IOException {
        try (
                final FileOutputStream fileOutputStream = new FileOutputStream(destinationFile.toFile());
                final GzipCompressorOutputStream gzipCompressorOutputStream = new GzipCompressorOutputStream(fileOutputStream);
                final TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(gzipCompressorOutputStream)) {
            buildTar(tarArchiveOutputStream, sourceDir);
        }
    }

    @Override
    public void buildTar(TarArchiveOutputStream tarArchiveOutputStream, Path sourceDir) throws IOException {
        if (!Files.exists(sourceDir)) {
            throw new IOException("source directory doesn't exist: " + sourceDir);
        }
        addFilestoTarGz(sourceDir.toString(), "", tarArchiveOutputStream);
    }

    private static void addFilestoTarGz(String filePath, String parent, TarArchiveOutputStream tarArchiveOutputStream) throws IOException {
        File file = new File(filePath);
        String entryName = parent + file.getName();
        tarArchiveOutputStream.putArchiveEntry(new TarArchiveEntry(file, entryName));
        if (file.isFile()) {
            try (
                    FileInputStream fileInputStream = new FileInputStream(file);
                    BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)
            ) {
                IOUtils.copy(bufferedInputStream, tarArchiveOutputStream);
                tarArchiveOutputStream.closeArchiveEntry();
            }
        } else if (file.isDirectory()) {
            tarArchiveOutputStream.closeArchiveEntry();
            for (File f : file.listFiles()) {
                addFilestoTarGz(f.getAbsolutePath(), entryName + File.separator, tarArchiveOutputStream);
            }
        }
    }
}
