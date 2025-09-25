package org.ts.clipharbor;

import io.github.bonigarcia.wdm.WebDriverManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.stream.Collectors;

public class ClipHarborController {

    @FXML private TextField urlField;
    @FXML private TextField folderField;
    @FXML private ProgressBar progressBar;
    @FXML private TextArea statusArea;
    @FXML private Button downloadButton;

    private File selectedFolder;

    private static final Set<String> blockedDomains = Set.of(
            "doubleclick", "googletag", "adservice", "facebook", "analytics", "tracking"
    );

    @FXML
    private void onBrowse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Download Folder");
        Stage stage = (Stage) folderField.getScene().getWindow();
        if (selectedFolder != null) chooser.setInitialDirectory(selectedFolder);
        File folder = chooser.showDialog(stage);
        if (folder != null) {
            selectedFolder = folder;
            folderField.setText(selectedFolder.getAbsolutePath());
            log("[DEBUG] Selected folder: " + selectedFolder.getAbsolutePath());
        }
    }

    @FXML
    private void onDownload() {
        String url = urlField.getText().trim();
        String folder = folderField.getText().trim();

        if (url.isEmpty() || folder.isEmpty()) {
            log("[DEBUG] URL or folder empty");
            appendStatus("Please enter a URL and select a save folder.");
            return;
        }

        downloadButton.setDisable(true);
        progressBar.setProgress(0);
        log("[DEBUG] Starting download for URL: " + url);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    List<String> videoUrls = new ArrayList<>();

                    if (url.toLowerCase().endsWith(".mp4")) {
                        videoUrls.add(url);
                    } else if (url.toLowerCase().endsWith(".m3u8")) {
                        videoUrls.addAll(fetchHlsSegmentsRecursive(url));
                    } else {
                        try {
                            log("[DEBUG] Attempting HTML parse...");
                            videoUrls.addAll(fetchMp4Links(url));
                            videoUrls.addAll(fetchVideoUrlsFromScripts(url));

                            if (videoUrls.isEmpty()) {
                                log("[DEBUG] HTML parse found nothing, trying Selenium...");
                                videoUrls.addAll(fetchVideoUrlsWithSelenium(url));
                            }

                        } catch (Exception e) {
                            log("[ERROR] Parsing failed: " + e.getMessage());
                        }
                    }

                    Optional<String> bestStream = pickBestStream(videoUrls);
                    if (bestStream.isEmpty()) {
                        updateMessage("[DEBUG] No valid video streams found.");
                        return null;
                    }

                    String videoUrl = bestStream.get();
                    updateMessage("[DEBUG] Best stream selected: " + videoUrl);

                    if (videoUrl.endsWith(".m3u8")) {
                        log("[DEBUG] Downloading HLS stream...");
                        List<String> segments = fetchHlsSegmentsRecursive(videoUrl);
                        downloadHlsSegments(segments, folder, "video.ts");
                    } else if (videoUrl.endsWith(".mp4")) {
                        log("[DEBUG] Downloading MP4 file...");
                        downloadFile(videoUrl, folder, "video.mp4");
                    } else if (videoUrl.endsWith(".ts")) {
                        log("[DEBUG] Downloading TS file...");
                        downloadFile(videoUrl, folder, "video.ts");
                    } else {
                        log("[DEBUG] Unknown extension, downloading as binary...");
                        downloadFile(videoUrl, folder, "video.bin");
                    }

                    updateMessage("[DEBUG] Download completed.");
                } catch (Exception e) {
                    updateMessage("[ERROR] " + e.getMessage());
                    e.printStackTrace();
                }
                return null;
            }

            private List<String> fetchMp4Links(String pageUrl) throws IOException {
                List<String> videos = new ArrayList<>();
                Document doc = Jsoup.connect(pageUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .timeout(15_000)
                        .get();

                Elements videoElements = doc.select(
                        "video[src$=.mp4], video > source[src$=.mp4], a[href$=.mp4], a[href$=.m3u8], a[href$=.ts]"
                );

                for (Element el : videoElements) {
                    String videoUrl = el.hasAttr("src") ? el.absUrl("src") : el.absUrl("href");
                    if (!videoUrl.isEmpty() && blockedDomains.stream().noneMatch(videoUrl::contains)) {
                        videos.add(videoUrl);
                        log("[DEBUG] Found video link: " + videoUrl);
                    }
                }
                return videos;
            }

            private List<String> fetchVideoUrlsFromScripts(String pageUrl) throws IOException {
                List<String> found = new ArrayList<>();
                Document doc = Jsoup.connect(pageUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .timeout(15_000)
                        .get();

                List<String> scripts = doc.select("script").eachText();
                for (String script : scripts) {
                    if (script == null) continue;
                    found.addAll(extractVideoUrlsFromText(script));
                }
                return found.stream().distinct().collect(Collectors.toList());
            }

            private List<String> extractVideoUrlsFromText(String text) {
                List<String> found = new ArrayList<>();
                String regex = "(https?:\\\\?/\\\\?/[^\"'\\s]+\\.(mp4|m3u8|ts))";
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(text);
                while (m.find()) {
                    String url = m.group(1).replaceAll("\\\\/", "/");
                    if (blockedDomains.stream().noneMatch(url.toLowerCase()::contains)) {
                        found.add(url);
                        log("[DEBUG] Script candidate: " + url);
                    }
                }
                return found;
            }

            private List<String> fetchVideoUrlsWithSelenium(String pageUrl) {
                log("[DEBUG][Selenium] Launching ChromeDriver for URL: " + pageUrl);
                Set<String> found = new HashSet<>();
                WebDriver driver = null;

                try {
                    WebDriverManager.chromedriver().setup();
                    ChromeOptions options = new ChromeOptions();
                    // options.addArguments("--headless"); // Uncomment to enable headless
                    options.addArguments("--disable-gpu");
                    options.addArguments("--no-sandbox");
                    options.addArguments("--disable-popup-blocking");
                    options.addArguments("--disable-notifications");
                    options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

                    driver = new ChromeDriver(options);
                    driver.manage().timeouts().pageLoadTimeout(java.time.Duration.ofSeconds(30));
                    driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(5));

                    driver.get(pageUrl);
                    log("[DEBUG][Selenium] Page loaded: " + pageUrl);

                    Thread.sleep(5000); // Wait to allow JS to run

                    ((JavascriptExecutor) driver).executeScript(
                            "document.querySelectorAll('video').forEach(v=>{v.muted=true;v.play().catch(()=>{});});"
                    );
                    log("[DEBUG][Selenium] Triggered video play()");

                    String js = """
            (function(){
                const out = [];
                function add(u){ if(u) out.push(u.trim()); }
                document.querySelectorAll('video').forEach(v=>{
                    add(v.currentSrc || v.src);
                    v.querySelectorAll('source').forEach(s=>add(s.src || s.getAttribute('data-src')));
                });
                document.querySelectorAll('source').forEach(s=>{
                    add(s.src || s.getAttribute('data-src') || s.getAttribute('data-href'));
                });
                document.querySelectorAll('a[href]').forEach(a=>{
                    const h=a.href;
                    if(h && (h.match(/\\.mp4(\\?.*)?$/i) || h.match(/\\.m3u8(\\?.*)?$/i) || h.match(/\\.ts(\\?.*)?$/i))){
                        add(h);
                    }
                });
                return Array.from(new Set(out)).filter(Boolean);
            })();
        """;

                    Object result = ((JavascriptExecutor) driver).executeScript(js);
                    if (result instanceof List<?>) {
                        for (Object o : (List<?>) result) {
                            if (o != null) {
                                String s = String.valueOf(o).trim();
                                if (!s.isEmpty() && blockedDomains.stream().noneMatch(s.toLowerCase()::contains)) {
                                    found.add(s);
                                    log("[DEBUG][Selenium] Found video: " + s);
                                }
                            }
                        }
                    }
                    log("[DEBUG][Selenium] Total candidates found: " + found.size());

                } catch (Exception e) {
                    log("[ERROR][Selenium] " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    if (driver != null) {
                        try {
                            driver.quit();
                            log("[DEBUG][Selenium] ChromeDriver closed");
                        } catch (Exception ignored) {}
                    }
                }

                return new ArrayList<>(found);
            }

            private List<String> fetchHlsSegmentsRecursive(String playlistUrl) throws IOException {
                List<String> segmentUrls = new ArrayList<>();
                URL url = new URL(playlistUrl);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        URL absoluteUrl = new URL(url, line);
                        if (line.toLowerCase().endsWith(".m3u8")) {
                            segmentUrls.addAll(fetchHlsSegmentsRecursive(absoluteUrl.toString()));
                        } else {
                            segmentUrls.add(absoluteUrl.toString());
                            log("[DEBUG] Added segment: " + absoluteUrl);
                        }
                    }
                }
                return segmentUrls;
            }

            private Optional<String> pickBestStream(List<String> urls) {
                return urls.stream()
                        .sorted((a, b) -> Integer.compare(scoreStream(b), scoreStream(a)))
                        .findFirst();
            }

            private int scoreStream(String url) {
                if (url == null) return 0;
                String u = url.toLowerCase();
                if (u.contains("master.m3u8")) return 100;
                if (u.endsWith(".m3u8")) return 90;
                if (u.endsWith(".mp4")) return 80;
                if (u.endsWith(".ts")) return 50;
                return 10;
            }

            private void downloadFile(String fileUrl, String folder, String fileName) throws IOException {
                log("[DEBUG] Downloading file: " + fileUrl);
                URL url = new URL(fileUrl);
                URLConnection connection = url.openConnection();
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(30_000);

                File outputFile = new File(folder, fileName);
                try (InputStream in = connection.getInputStream();
                     FileOutputStream out = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                log("[DEBUG] File saved: " + fileName);
            }

            private void downloadHlsSegments(List<String> segmentUrls, String folder, String fileName) throws IOException {
                File outputFile = new File(folder, fileName);
                try (FileOutputStream out = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[8192];
                    int segCount = 0;
                    int total = segmentUrls.size();
                    for (String segUrl : segmentUrls) {
                        segCount++;
                        log("[DEBUG] Downloading segment " + segCount + "/" + total + ": " + segUrl);
                        URLConnection conn = new URL(segUrl).openConnection();
                        conn.setConnectTimeout(15_000);
                        conn.setReadTimeout(30_000);
                        try (InputStream in = conn.getInputStream()) {
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }
                    }
                }
                log("[DEBUG] HLS download complete: " + fileName);
            }

            @Override
            protected void updateMessage(String message) {
                Platform.runLater(() -> appendStatus(message));
            }

            @Override
            protected void updateProgress(double workDone, double max) {
                Platform.runLater(() -> {
                    double fraction = max == 0 ? 0 : workDone / max;
                    fraction = Math.max(0, Math.min(1, fraction));
                    progressBar.setProgress(fraction);
                });
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> downloadButton.setDisable(false));
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> downloadButton.setDisable(false));
            }
        };

        new Thread(task).start();
    }

    private void appendStatus(String message) {
        Platform.runLater(() -> statusArea.appendText(message + "\n"));
    }

    private void log(String message) {
        String timestamp = "[" + new Date() + "] ";
        System.out.println(timestamp + message);
        Platform.runLater(() -> statusArea.appendText(timestamp + message + "\n"));
    }
}
