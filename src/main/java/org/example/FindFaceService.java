package org.example;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public interface FindFaceService {
    Set<String> getDossiers(File file, String token) throws IOException;

}


