package org.example.core.document;


import org.springframework.ai.document.Document;

import java.io.File;
import java.util.List;

public interface DocumentLoader extends State {
    List<Document> load(File file);
}
