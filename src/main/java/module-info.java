module org.ts.clipharbor {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;

    opens org.ts.clipharbor to javafx.fxml;
    exports org.ts.clipharbor;
}