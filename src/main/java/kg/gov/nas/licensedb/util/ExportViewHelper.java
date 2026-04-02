package kg.gov.nas.licensedb.util;

import org.apache.poi.ss.usermodel.*;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class ExportViewHelper {
    private final static Logger LOGGER = Logger.getLogger(ExportViewHelper.class.getName());
    public static final String DATE_PATTERN = "yyyyMMddHHmmss";

    public void createHeader(Workbook workbook, Sheet sheet, int startRow, List<String> headerTexts) {
        Row header = sheet.createRow(startRow);
        for (int i = 0; i < headerTexts.size(); i++) {
            header.createCell(i).setCellValue(headerTexts.get(i));
            header.getCell(i).setCellStyle(defaultStyle(workbook));

        }

        header.setHeight((short) 500);
    }

    public void createContent(Workbook workbook, Sheet sheet, int startRow, List<List<String>> items) {
        // Create data cells
        int rowCount = startRow;
        for (List<String> item : items) {
            Row row = sheet.createRow(rowCount++);

            for (int i = 0; i < item.size(); i++) {
                row.createCell(i).setCellValue(item.get(i));
            }
            row.setHeight((short) 500);
        }
    }

    public CellStyle defaultStyle(Workbook workbook) {
        CellStyle cellStyle = workbook.createCellStyle();

        cellStyle.setBorderBottom(BorderStyle.THIN);
        cellStyle.setBorderTop(BorderStyle.THIN);
        cellStyle.setBorderLeft(BorderStyle.THIN);
        cellStyle.setBorderRight(BorderStyle.THIN);
        cellStyle.setAlignment(HorizontalAlignment.CENTER);
        cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        cellStyle.setBottomBorderColor(IndexedColors.BLACK.getIndex());
        cellStyle.setTopBorderColor(IndexedColors.BLACK.getIndex());
        cellStyle.setLeftBorderColor(IndexedColors.BLACK.getIndex());
        cellStyle.setRightBorderColor(IndexedColors.BLACK.getIndex());

        cellStyle.setFont(defaultFont(workbook));

        return cellStyle;
    }

    private Font defaultFont(Workbook workbook) {
        return createFont(workbook, (short) 10, IndexedColors.BLACK.getIndex());
    }

    private Font createFont(Workbook workbook, short size, short color) {
        Font defaultFont = workbook.createFont();
        defaultFont.setFontHeightInPoints((size));
        defaultFont.setFontName("Arial");
        defaultFont.setColor(color);
        defaultFont.setBold(true);
        return defaultFont;
    }

    public void writeData(Workbook wb, HttpServletResponse response, String filename) {
        try (ByteArrayOutputStream outByteStream = new ByteArrayOutputStream()) {
            wb.write(outByteStream);
            byte[] outArray = outByteStream.toByteArray();
            response.setContentType(ExcelCommonConstant.XLSX_CONTENT_TYPE);
            response.setContentLength(outArray.length);
            response.setHeader("Expires:", "0"); // eliminates browser caching
            response.setHeader("Content-Disposition", "attachment; filename=" + filename + Util.formatDate(new Date(), DATE_PATTERN) + ExcelCommonConstant.XLSX_EXTENSION);
            OutputStream outStream = response.getOutputStream();
            outStream.write(outArray);
            outStream.flush();
            wb.close();
        } catch (FileNotFoundException e) {
            LOGGER.warning(MessageFormat.format("FileNotFoundException : StackTrace: {0}", Util.getStackTrace(e)));
        } catch (Exception e) {
            LOGGER.warning(MessageFormat.format("Error when writing data : StackTrace: {0}", Util.getStackTrace(e)));
        }
    }

}
