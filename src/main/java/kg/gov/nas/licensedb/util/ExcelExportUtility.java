package kg.gov.nas.licensedb.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.util.List;

public abstract class ExcelExportUtility<E extends Object> {
    protected SXSSFWorkbook wb;
    protected Sheet sh;

    private void autoResizeColumns(int listSize) {
        for (int colIndex = 0; colIndex < listSize; colIndex++) {
            sh.autoSizeColumn(colIndex);
        }
    }

    protected CellStyle getHeaderStyle() {
        ExportViewHelper helper = new ExportViewHelper();
        return helper.defaultStyle(wb);
    }

    protected CellStyle getNormalStyle(boolean isDate) {
        CellStyle style = wb.createCellStyle();
        //style.setBorderBottom(CellStyle.BORDER_THIN);
        style.setBottomBorderColor(IndexedColors.BLACK.getIndex());
        //style.setBorderLeft(CellStyle.BORDER_THIN);
        style.setLeftBorderColor(IndexedColors.BLACK.getIndex());
        //style.setBorderRight(CellStyle.BORDER_THIN);
        style.setRightBorderColor(IndexedColors.BLACK.getIndex());
        //style.setBorderTop(CellStyle.BORDER_THIN);
        style.setTopBorderColor(IndexedColors.BLACK.getIndex());
        //style.setAlignment(CellStyle.ALIGN_CENTER);
        if (isDate) {
            CreationHelper creationHelper = wb.getCreationHelper();
            style.setDataFormat(creationHelper.createDataFormat().getFormat(("dd.MM.yyyy HH:mm:ss")));
        }
        return style;
    }

    private void fillHeader(String[] columns) {
        wb = new SXSSFWorkbook(SXSSFWorkbook.DEFAULT_WINDOW_SIZE);
        sh = wb.createSheet("Частоты");

        CellStyle style = getHeaderStyle();
        for (int rownum = 0; rownum < 1; rownum++) {
            Row row = sh.createRow(rownum);
            for (int cellnum = 0; cellnum < columns.length; cellnum++) {
                Cell cell = row.createCell(cellnum);
                cell.setCellValue(columns[cellnum]);
                cell.setCellStyle(style);
            }
        }
        sh.setAutoFilter(CellRangeAddress.valueOf("A1:AZ1"));
    }

    public final SXSSFWorkbook exportExcel(String[] columns, List<E> dataList) {
        fillHeader(columns);
        fillData(dataList);

        return wb;
    }

    public abstract void fillData(List<E> dataList);
}
