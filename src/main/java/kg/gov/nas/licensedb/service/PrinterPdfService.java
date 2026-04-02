package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dto.FreqModel;
import kg.gov.nas.licensedb.dto.OwnerModel;
import kg.gov.nas.licensedb.dto.SiteModel;
import kg.gov.nas.licensedb.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrinterPdfService {

    private final SpringTemplateEngine templateEngine;

    @Value("${app.pdf.browserPath:}")
    private String browserPathFromProps;

    // Сколько миллиметров закрашивать сверху/снизу (чтобы убрать header/footer Chromium)
    // Если вдруг нужно точнее — меняй эти числа.
    private static final float WIPE_TOP_MM = 7f;
    private static final float WIPE_BOTTOM_MM = 9f;

    public byte[] generatePdf(List<OwnerModel> items, String generatedBy, LocalDateTime generatedAt) {

        // 1) Рендерим тот же HTML, что и "Показать HTML"
        Context ctx = new Context(Locale.forLanguageTag("ru"));
        ctx.setVariable("items", items);

        final String htmlRaw;
        try {
            htmlRaw = templateEngine.process("printer/result", ctx);
        } catch (Exception e) {
            log.error("Ошибка Thymeleaf при формировании HTML для PDF (printer/result)", e);
            throw new IllegalStateException("Не удалось обработать шаблон printer/result.html: " + e.getMessage(), e);
        }

        // 2) Формируем "серьёзные" реквизиты (hash + код проверки)
        String docHash = buildDocumentHash(items);
        String code = (docHash != null && docHash.length() >= 12)
            ? docHash.substring(0, 12).toUpperCase(Locale.ROOT)
            : "N/A";

        String generatedAtTxt = generatedAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));

        // 3) Вставляем метку как FIXED (не должна толкать верстку на новую страницу)
        String htmlWithProof = injectProofFixed(htmlRaw, code, docHash, generatedAtTxt, generatedBy);

        // 4) Печатаем Chromium/Edge (стараемся выключить header/footer флагами)
        byte[] pdf = renderPdfByHeadlessChromium(htmlWithProof);

        // 5) Если header/footer всё равно попал в PDF — убираем его “маской” (без обрезки страницы)
        if (looksLikeChromiumHeaderFooterPresent(pdf)) {
            pdf = wipeHeaderFooter(pdf, WIPE_TOP_MM, WIPE_BOTTOM_MM);
        }

        // 6) Если вдруг получился хвостовой пустой лист (обычно из-за header/footer) — уберём его (для одного документа)
        if (items != null && items.size() == 1) {
            pdf = dropTrailingBlankPageIfAny(pdf);
        }

        return pdf;
    }

    /**
     * Хэш документа: стабильная сортировка dataHash по freqId + секрет (если доступен).
     * Это “серьёзный” реквизит, который нельзя честно повторить без секрета.
     */
    private String buildDocumentHash(List<OwnerModel> items) {
        List<String> parts = new ArrayList<>();
        if (items != null) {
            for (OwnerModel owner : items) {
                if (owner == null || owner.getSites() == null) continue;
                for (SiteModel site : owner.getSites()) {
                    if (site == null || site.getFrequencies() == null) continue;
                    for (FreqModel freq : site.getFrequencies()) {
                        if (freq == null) continue;

                        String dh = freq.getDataHash();
                        if (dh == null || dh.isBlank()) {
                            dh = SecurityUtil.freqDataHash(owner, site, freq);
                        }
                        Long fid = freq.getFreqId();
                        parts.add((fid == null ? "0" : fid.toString()) + ":" + dh);
                    }
                }
            }
        }
        Collections.sort(parts);

        String secret = "";
        try {
            secret = SecurityUtil.getChainSecret();
        } catch (Exception ignored) {
            // если в вашей версии нет getChainSecret() — просто не добавляем
        }

        String payload = "PDF_PRINT|" + String.join(";", parts) + "|" + secret;
        return sha256Hex(payload);
    }

    private String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 недоступен: " + e.getMessage(), e);
        }
    }

    /**
     * Метка ставится как position:fixed (не влияет на высоту страницы).
     */
    private String injectProofFixed(String html, String code, String hash, String generatedAtTxt, String generatedBy) {

        String css =
            "\n<style>\n" +
                "  @page { size: A4 portrait; margin: 0mm; }\n" +
                "  #pdf-proof{\n" +
                "    position: fixed;\n" +
                "    left: 10mm; right: 10mm;\n" +
                "    bottom: 14mm; /* специально выше зоны footer */\n" +
                "    font-size: 9px;\n" +
                "    background: #fff;\n" +
                "    padding-top: 2px;\n" +
                "    border-top: 1px dashed #000;\n" +
                "  }\n" +
                "  #pdf-proof .mono{font-family: monospace; word-break: break-all;}\n" +
                "</style>\n";

        String proof =
            "\n<div id=\"pdf-proof\">\n" +
                "  <div><b>Электронная метка документа (ГАС)</b> · Сформировано: " + esc(generatedAtTxt) +
                " · Пользователь: " + esc(generatedBy) + " · Код проверки: <b>" + esc(code) + "</b></div>\n" +
                "  <div class=\"mono\">SHA-256: " + esc(hash) + "</div>\n" +
                "</div>\n";

        String lower = html.toLowerCase(Locale.ROOT);

        int headClose = lower.indexOf("</head>");
        if (headClose >= 0) {
            html = html.substring(0, headClose) + css + html.substring(headClose);
            lower = html.toLowerCase(Locale.ROOT);
        } else {
            html = css + html;
            lower = html.toLowerCase(Locale.ROOT);
        }

        int bodyClose = lower.lastIndexOf("</body>");
        if (bodyClose >= 0) {
            html = html.substring(0, bodyClose) + proof + html.substring(bodyClose);
            return html;
        }

        return html + proof;
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private byte[] renderPdfByHeadlessChromium(String html) {
        String browserPath = resolveBrowserPath();
        if (browserPath == null) {
            throw new IllegalStateException(
                "Не найден Chrome/Edge для генерации PDF. " +
                    "Укажи путь в application.properties: app.pdf.browserPath=... (chrome.exe или msedge.exe)"
            );
        }

        Path tempHtml = null;
        Path tempPdf = null;

        try {
            tempHtml = Files.createTempFile("print_", ".html");
            tempPdf = Files.createTempFile("print_", ".pdf");
            Files.writeString(tempHtml, html, StandardCharsets.UTF_8);

            URI htmlUri = tempHtml.toUri();

            List<String> cmd = new ArrayList<>();
            cmd.add(browserPath);

            // Новый headless (чаще корректнее печатает)
            cmd.add("--headless=new");
            cmd.add("--disable-gpu");
            cmd.add("--no-first-run");
            cmd.add("--no-default-browser-check");

            cmd.add("--run-all-compositor-stages-before-draw");
            cmd.add("--virtual-time-budget=3000");

            // Пытаемся выключить header/footer (разные сборки понимают разные флаги)
            cmd.add("--no-pdf-header-footer");
            cmd.add("--print-to-pdf-no-header");

            cmd.add("--print-to-pdf=" + tempPdf.toAbsolutePath());
            cmd.add("--allow-file-access-from-files");
            cmd.add(htmlUri.toString());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            Process p = pb.start();
            String out = readAll(p.getInputStream());

            boolean finished = p.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new IllegalStateException("Chrome/Edge не успел сформировать PDF за 30 секунд.");
            }

            int code = p.exitValue();
            if (code != 0) {
                throw new IllegalStateException("Chrome/Edge завершился с кодом " + code + ". Вывод: " + out);
            }

            byte[] pdf = Files.readAllBytes(tempPdf);
            if (pdf.length == 0) {
                throw new IllegalStateException("PDF получился пустым. Вывод Chrome/Edge: " + out);
            }

            return pdf;

        } catch (Exception e) {
            log.error("Ошибка генерации PDF через headless Chromium/Edge", e);
            throw new IllegalStateException("Не удалось сформировать PDF через Chrome/Edge: " + e.getMessage(), e);
        } finally {
            safeDelete(tempHtml);
            safeDelete(tempPdf);
        }
    }

    /**
     * Определяем, что Chromium реально добавил header/footer (обычно там есть file:///... и/или 1/2).
     */
    private boolean looksLikeChromiumHeaderFooterPresent(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String txt = stripper.getText(doc);
            if (txt == null) return false;

            if (txt.contains("file:///")) return true;
            // часто ещё бывает "1/2" или "1 / 2"
            return txt.matches("(?s).*\\b\\d+\\s*/\\s*\\d+\\b.*");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Убираем header/footer: рисуем белые прямоугольники сверху/снизу.
     * Это НЕ режет страницу, а только скрывает лишний текст.
     */
    private byte[] wipeHeaderFooter(byte[] pdf, float topMm, float bottomMm) {
        float topPts = mmToPt(topMm);
        float bottomPts = mmToPt(bottomMm);

        try (PDDocument doc = Loader.loadPDF(pdf);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            for (PDPage page : doc.getPages()) {
                PDRectangle box = page.getMediaBox();
                float w = box.getWidth();
                float h = box.getHeight();

                try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true)) {

                    cs.setNonStrokingColor(255, 255, 255);

                    // низ
                    cs.addRect(0, 0, w, bottomPts);
                    cs.fill();

                    // верх
                    cs.addRect(0, h - topPts, w, topPts);
                    cs.fill();
                }
            }

            doc.save(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.warn("Не удалось скрыть header/footer (wipe). Возвращаю исходный PDF.", e);
            return pdf;
        }
    }

    private float mmToPt(float mm) {
        return mm * 2.83465f; // 1 мм = 2.83465 pt
    }

    /**
     * Если напечатался хвостовой лист (обычно из-за header/footer), удаляем его для items.size()==1.
     */
    private byte[] dropTrailingBlankPageIfAny(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            if (doc.getNumberOfPages() <= 1) return pdf;

            PDFTextStripper stripper = new PDFTextStripper();

            int lastIdx = doc.getNumberOfPages();
            stripper.setStartPage(lastIdx);
            stripper.setEndPage(lastIdx);
            String lastText = stripper.getText(doc);

            if (lastText != null
                && lastText.contains("Электронная метка документа")
                && !lastText.contains("Частотное присвоение")) {

                doc.removePage(lastIdx - 1);
                doc.save(bos);
                return bos.toByteArray();
            }

            return pdf;
        } catch (Exception e) {
            return pdf;
        }
    }

    private String resolveBrowserPath() {
        if (browserPathFromProps != null && !browserPathFromProps.isBlank()) {
            File f = new File(browserPathFromProps.trim());
            if (f.isFile()) return f.getAbsolutePath();
        }

        String[] candidates = new String[] {
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
            "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
            "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
            "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
        };

        for (String c : candidates) {
            File f = new File(c);
            if (f.isFile()) return f.getAbsolutePath();
        }

        return null;
    }

    private String readAll(InputStream is) {
        try (InputStream in = is; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) != -1) {
                bos.write(buf, 0, r);
            }
            return bos.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private void safeDelete(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }
}
