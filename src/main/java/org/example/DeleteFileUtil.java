package org.example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DeleteFileUtil {

    public static void deleteFile(File file) {
        boolean deleted = file.delete();
        if (!deleted) {
            try {
                Path filePath = Paths.get(file.getAbsolutePath());
                Files.delete(filePath);
            } catch (IOException e) {
            }
        }
    }
}
