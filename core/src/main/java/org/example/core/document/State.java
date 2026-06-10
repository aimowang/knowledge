package org.example.core.document;

import java.util.List;

public interface State {
   default List<String> support() {
       return List.of("all");
   }
}
