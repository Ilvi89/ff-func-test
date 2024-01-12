package org.example;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface FindFaceService {
    Map<String, Set<String>> getDossiers(File file, String token) throws TracebackE, IOException;

}


