module org.ts.clipharbor {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires org.jsoup;
    requires org.seleniumhq.selenium.api;
    requires org.seleniumhq.selenium.chrome_driver;
    requires org.seleniumhq.selenium.devtools_v113;
    requires io.github.bonigarcia.webdrivermanager;

    opens org.ts.clipharbor to javafx.fxml;
    exports org.ts.clipharbor;
}