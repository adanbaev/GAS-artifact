package kg.gov.nas.licensedb.dto;

import kg.gov.nas.licensedb.util.ExcelExportUtility;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;

import java.util.List;

public class FreqExport extends ExcelExportUtility<FreqExportModel> {
    @Override
    public void fillData(List<FreqExportModel> dataList) {
        CellStyle normalStyle = getNormalStyle(false);
        CellStyle dateStyle = getNormalStyle(true);
        int rownum = 1;
        for (FreqExportModel item : dataList) {
            /*BigDecimal sumOfTransaction = item.getSumOfTransaction();
            Date date = item.getDate();*/
            Row row = sh.createRow(rownum);

            Cell cell_0 = row.createCell(0, CellType.NUMERIC);
            cell_0.setCellStyle(normalStyle);
            cell_0.setCellValue( item.getOwnerId() == null ? 0 : item.getOwnerId());

            Cell cell_1 = row.createCell(1, CellType.STRING);
            cell_1.setCellStyle(normalStyle);
            cell_1.setCellValue(item.getOwnerType() == null ? "" : item.getOwnerType().getValue());

            Cell cell_2 = row.createCell(2, CellType.NUMERIC);
            cell_2.setCellStyle(normalStyle);
            cell_2.setCellValue(item.getLicNumber() == null ? 0 : item.getLicNumber());

            Cell cell_3 = row.createCell(3, CellType.STRING);
            cell_3.setCellStyle(normalStyle);
            cell_3.setCellValue(item.getOwnerName() == null ? "" : item.getOwnerName());

            Cell cell_4 = row.createCell(4, CellType.STRING);
            cell_4.setCellStyle(normalStyle);
            cell_4.setCellValue(item.getPhone() == null ? "" : item.getPhone());

            Cell cell_5 = row.createCell(5, CellType.STRING);
            cell_5.setCellStyle(normalStyle);
            cell_5.setCellValue(item.getFax() == null ? "" : item.getFax());

            Cell cell_6 = row.createCell(6, CellType.STRING);
            cell_6.setCellStyle(normalStyle);
            cell_6.setCellValue(item.getNumber() == null ? "" : item.getNumber());

            Cell cell_7 = row.createCell(7, CellType.STRING);
            cell_7.setCellStyle(normalStyle);
            cell_7.setCellValue(item.getPassport() == null ? "" : item.getPassport());

            Cell cell_8 = row.createCell(8, CellType.STRING);
            cell_8.setCellStyle(normalStyle);
            cell_8.setCellValue(item.getRegion() == null ? "" : item.getRegion().getValue());

            Cell cell_9 = row.createCell(9, CellType.STRING);
            cell_9.setCellStyle(normalStyle);
            cell_9.setCellValue(item.getTown() == null ? "" : item.getTown());

            Cell cell_10 = row.createCell(10, CellType.STRING);
            cell_10.setCellStyle(normalStyle);
            cell_10.setCellValue(item.getStreet() == null ? "" : item.getStreet());

            Cell cell_11 = row.createCell(11, CellType.STRING);
            cell_11.setCellStyle(normalStyle);
            cell_11.setCellValue(item.getHouse() == null ? "" : item.getHouse());

            Cell cell_12 = row.createCell(12, CellType.STRING);
            cell_12.setCellStyle(normalStyle);
            cell_12.setCellValue(item.getFlat() == null ? "" : item.getFlat());

            Cell cell_13 = row.createCell(13, CellType.STRING);
            cell_13.setCellStyle(normalStyle);
            cell_13.setCellValue(item.getIssueDate() == null ? "" : item.getIssueDate().toString());

            Cell cell_14 = row.createCell(14, CellType.STRING);
            cell_14.setCellStyle(normalStyle);
            cell_14.setCellValue(item.getExpireDate() == null ? "" : item.getExpireDate().toString());

            Cell cell_15 = row.createCell(15, CellType.STRING);
            cell_15.setCellStyle(normalStyle);
            cell_15.setCellValue(item.getRegDate() == null ? "" : item.getRegDate().toString());

            Cell cell_16 = row.createCell(16, CellType.STRING);
            cell_16.setCellStyle(normalStyle);
            cell_16.setCellValue(item.getInvoiceDate() == null ? "" : item.getInvoiceDate().toString());

            Cell cell_17 = row.createCell(17, CellType.STRING);
            cell_17.setCellStyle(normalStyle);
            cell_17.setCellValue(item.getState() == null ? "" : item.getState().getValue());

            Cell cell_18 = row.createCell(18, CellType.STRING);
            cell_18.setCellStyle(normalStyle);
            cell_18.setCellValue(item.getDesc() == null ? "" : item.getDesc());

            Cell cell_19 = row.createCell(19, CellType.STRING);
            cell_19.setCellStyle(normalStyle);
            cell_19.setCellValue(item.getTypeOfUsing() == null ? "" : item.getTypeOfUsing());

            Cell cell_20 = row.createCell(20, CellType.STRING);
            cell_20.setCellStyle(normalStyle);
            cell_20.setCellValue(item.getPoint() == null ? "" : item.getPoint());

            Cell cell_21 = row.createCell(21, CellType.STRING);
            cell_21.setCellStyle(normalStyle);
            cell_21.setCellValue(item.getPoint() == null ? "" : item.getPoint());

            Cell cell_22 = row.createCell(22, CellType.STRING);
            cell_22.setCellStyle(normalStyle);
            cell_22.setCellValue(item.getVd()  == null ? "" : item.getVd());

            Cell cell_23 = row.createCell(23, CellType.STRING);
            cell_23.setCellStyle(normalStyle);
            cell_23.setCellValue(item.getSh()  == null ? "" : item.getSh());

            Cell cell_24 = row.createCell(24, CellType.STRING);
            cell_24.setCellStyle(normalStyle);
            cell_24.setCellValue(item.getCallSign()  == null ? "" : item.getCallSign());

            Cell cell_25 = row.createCell(25, CellType.NUMERIC);
            cell_25.setCellStyle(normalStyle);
            cell_25.setCellValue(item.getAbsEarth()  == null ? 0.0 : item.getAbsEarth());

            Cell cell_26 = row.createCell(26, CellType.STRING);
            cell_26.setCellStyle(normalStyle);
            cell_26.setCellValue(item.getTransType()  == null ? "" : item.getTransType());

            Cell cell_27 = row.createCell(27, CellType.NUMERIC);
            cell_27.setCellStyle(normalStyle);
            cell_27.setCellValue(item.getReceiverSensitivity()  == null ? 0.0 : item.getReceiverSensitivity());

            Cell cell_28 = row.createCell(28, CellType.STRING);
            cell_28.setCellStyle(normalStyle);
            cell_28.setCellValue(item.getTransNumber()  == null ? "" : item.getTransNumber());

            Cell cell_29 = row.createCell(29, CellType.STRING);
            cell_29.setCellStyle(normalStyle);
            cell_29.setCellValue(item.getAntName() == null ? "" : item.getAntName());

            Cell cell_30 = row.createCell(30, CellType.STRING);
            cell_30.setCellStyle(normalStyle);
            cell_30.setCellValue(item.getAntType() == null ? "" : item.getAntType());

            Cell cell_31 = row.createCell(31, CellType.NUMERIC);
            cell_31.setCellStyle(normalStyle);
            cell_31.setCellValue(item.getAntKU()  == null ? 0.0 : item.getAntKU());

            Cell cell_32 = row.createCell(32, CellType.NUMERIC);
            cell_32.setCellStyle(normalStyle);
            cell_32.setCellValue(item.getAntKUrecv() == null ? 0.0 : item.getAntKUrecv());

            Cell cell_33 = row.createCell(33, CellType.STRING);
            cell_33.setCellStyle(normalStyle);
            cell_33.setCellValue(item.getIsz() == null ? "" : item.getIsz());

            Cell cell_34 = row.createCell(34, CellType.NUMERIC);
            cell_34.setCellStyle(normalStyle);
            cell_34.setCellValue(item.getDolgOrbit() == null ? 0.0 : item.getDolgOrbit());

            Cell cell_35 = row.createCell(35, CellType.NUMERIC);
            cell_35.setCellStyle(normalStyle);
            cell_35.setCellValue(item.getBeamWidth() == null ? 0.0 : item.getBeamWidth());

            Cell cell_36 = row.createCell(36, CellType.NUMERIC);
            cell_36.setCellStyle(normalStyle);
            cell_36.setCellValue(item.getHighlight() == null ? 0.0 : item.getHighlight());

            Cell cell_37 = row.createCell(37, CellType.NUMERIC);
            cell_37.setCellStyle(normalStyle);
            cell_37.setCellValue(item.getFreqStable() == null ? 0.0 : item.getFreqStable());

            Cell cell_38 = row.createCell(38, CellType.STRING);
            cell_38.setCellStyle(normalStyle);
            cell_38.setCellValue(item.getRecvrType() == null ? "" : item.getRecvrType());

            Cell cell_39 = row.createCell(39, CellType.STRING);
            cell_39.setCellStyle(normalStyle);
            cell_39.setCellValue(item.getPolar() == null ? "" : item.getPolar().getValue());

            Cell cell_40 = row.createCell(40, CellType.STRING);
            cell_40.setCellStyle(normalStyle);
            cell_40.setCellValue(item.getReceiverAntType() == null ? "" : item.getReceiverAntType());

            Cell cell_41 = row.createCell(41, CellType.NUMERIC);
            cell_41.setCellStyle(normalStyle);
            cell_41.setCellValue(item.getReceiverHighlight() == null ? 0.0 : item.getReceiverHighlight());

            Cell cell_42 = row.createCell(42, CellType.NUMERIC);
            cell_42.setCellStyle(normalStyle);
            cell_42.setCellValue(item.getNominal() == null ? 0.0 : item.getNominal());

            Cell cell_43 = row.createCell(43, CellType.STRING);
            cell_43.setCellStyle(normalStyle);
            cell_43.setCellValue(item.getType() == null ? "" : item.getType().getValue());

            Cell cell_44 = row.createCell(44, CellType.NUMERIC);
            cell_44.setCellStyle(normalStyle);
            cell_44.setCellValue(item.getBand() == null ? 0.0 : item.getBand());

            Cell cell_45 = row.createCell(45, CellType.STRING);
            cell_45.setCellStyle(normalStyle);
            cell_45.setCellValue(item.getMode() == null ? "" : item.getMode().getValue());

            Cell cell_46 = row.createCell(46, CellType.STRING);
            cell_46.setCellStyle(normalStyle);
            cell_46.setCellValue(item.getMeaning() == null ? "" : item.getMeaning());

            Cell cell_47 = row.createCell(47, CellType.NUMERIC);
            cell_47.setCellStyle(normalStyle);
            cell_47.setCellValue(item.getMobStan() == null ? 0.0 : item.getMobStan());

            Cell cell_48 = row.createCell(48, CellType.NUMERIC);
            cell_48.setCellStyle(normalStyle);
            cell_48.setCellValue(item.getSatRadius() == null ? 0.0 : item.getSatRadius());

            Cell cell_49 = row.createCell(49, CellType.NUMERIC);
            cell_49.setCellStyle(normalStyle);
            cell_49.setCellValue(item.getDeviation() == null ? 0.0 : item.getDeviation());

            Cell cell_50 = row.createCell(50, CellType.NUMERIC);
            cell_50.setCellStyle(normalStyle);
            cell_50.setCellValue(item.getChannel() == null ? 0.0 : item.getChannel());

            Cell cell_51 = row.createCell(51, CellType.NUMERIC);
            cell_51.setCellStyle(normalStyle);
            cell_51.setCellValue(item.getSnch() == null ? 0.0 : item.getSnch());

            rownum++;
        }
    }
}
